package com.routemate.app.room.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.domain.*
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoomUiState(val snapshot: RoomSnapshot=RoomSnapshot(null,null,null),val nearby: List<NearbyRoom> = emptyList(),
    val radiusKm: Int=RoomPolicy.defaultRadiusKm,val busy: Boolean=false,val error: RoomError?=null,val voice: VoiceState=VoiceState())

class RoomViewModel(private val rooms: ManageRooms,private val voice: VoiceCall,private val trips: TripRegistration,
    private val heartbeatMs: Long=RoomPolicy.heartbeatMs) : ViewModel() {
    private val mutableState=MutableStateFlow(RoomUiState())
    val state=mutableState.asStateFlow()
    private var action: Job?=null
    private var heartbeat: Job?=null
    private var call: Job?=null
    private var mic: Job?=null
    private var cleanup: Job?=null
    private var foreground=true
    private var generation=0
    private var desiredDestination: Destination?=null
    private var destinationRevision=0L
    private var syncPending=false
    private var refreshPending=false
    private var registrationPending=false
    private var destinationStarted=false
    private fun error(e: Exception)=when(e){
        is RoomAccessException -> e.reason
        is TripRegistrationException -> when(e.reason){
            TripFailure.NETWORK -> RoomError.NETWORK
            TripFailure.UNAUTHORIZED -> RoomError.UNAUTHORIZED
            TripFailure.CONFIGURATION -> RoomError.CONFIGURATION
            TripFailure.INVALID_DESTINATION -> RoomError.INVALID_REQUEST
            TripFailure.VEHICLE_UNAVAILABLE -> RoomError.VEHICLE_UNAVAILABLE
            TripFailure.MOCK_VEHICLE_DISABLED -> RoomError.MOCK_VEHICLE_DISABLED
            TripFailure.TRIP_CHANGED -> RoomError.TRIP_CHANGED
            TripFailure.VEHICLE_CONFLICT,TripFailure.SERVER -> RoomError.SERVER
        }
        is IllegalArgumentException -> RoomError.INVALID_REQUEST
        else -> RoomError.SERVER
    }
    private fun perform(block: suspend ()->Unit) {
        if(state.value.busy)return
        val current=generation
        mutableState.update { it.copy(busy=true,error=null) }
        action=viewModelScope.launch {
            try { block() }
            catch(e: CancellationException){throw e}
            catch(e: Exception){if(current==generation)mutableState.update { it.copy(error=error(e)) }}
            finally {
                if(current==generation){
                    mutableState.update { it.copy(busy=false) }
                    if(foreground&&syncPending)syncDestination()
                    else if(foreground&&refreshPending){refreshPending=false;refresh()}
                }
            }
        }
    }
    private suspend fun reload(){
        val snapshot=rooms.snapshot()
        currentCoroutineContext().ensureActive()
        if(snapshot.room?.id!=state.value.snapshot.room?.id)stopVoice()
        mutableState.update { it.copy(snapshot=snapshot,nearby=if(snapshot.destination!=it.snapshot.destination)emptyList() else it.nearby) }
        startHeartbeat()
    }
    fun refresh(){
        if(state.value.busy){refreshPending=true;return}
        refreshPending=false;perform { reload();loadNearby() }
    }
    private suspend fun loadNearby() {
        if(state.value.snapshot.tripId==null||state.value.snapshot.room!=null)return
        val revision=destinationRevision
        val found=rooms.nearby(state.value.radiusKm)
        currentCoroutineContext().ensureActive()
        if(revision==destinationRevision)mutableState.update { it.copy(nearby=found) }
    }
    /** Automatic startup is once per destination, including across Activity recreation. */
    fun startDestination(value: Destination) {
        if(destinationStarted&&value==desiredDestination)return
        destinationStarted=true
        registerDestination(value)
    }
    fun registerDestination(value: Destination) {
        if(state.value.snapshot.room!=null&&value!=state.value.snapshot.destination)stopVoice()
        destinationChanged(value)
        registrationPending=true
        syncDestination()
    }
    fun search(radiusKm: Int)=perform {
        val revision=destinationRevision
        mutableState.update { it.copy(radiusKm=radiusKm,nearby=emptyList()) }
        val found=rooms.nearby(radiusKm)
        currentCoroutineContext().ensureActive()
        if(revision==destinationRevision)mutableState.update { it.copy(nearby=found) }
    }
    fun create(name: String,isPublic: Boolean,capacity: Int)=perform {
        val room=rooms.create(name,isPublic,capacity)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),nearby=emptyList()) };startHeartbeat()
    }
    fun join(id: String)=perform {
        val room=rooms.join(id)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),nearby=emptyList()) };startHeartbeat()
    }
    fun leave() {
        val room=state.value.snapshot.room ?: return
        stopVoice()
        perform { rooms.leave(room.id);reload() }
    }
    fun endTrip() {
        stopVoice()
        registrationPending=false;syncPending=false
        perform { rooms.endTrip();currentCoroutineContext().ensureActive();mutableState.value=RoomUiState(radiusKm=state.value.radiusKm);heartbeat?.cancel() }
    }
    fun connect(permitted: Boolean) {
        if(!permitted){mutableState.update { it.copy(voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.PERMISSION)) };return}
        if(call?.isActive==true||state.value.busy)return
        val room=state.value.snapshot.room ?: return
        val previous=call
        call=viewModelScope.launch {
            try {
                previous?.cancelAndJoin()
                mutableState.update { it.copy(voice=VoiceState(status=VoiceStatus.CONNECTING),error=null) }
                val connection=rooms.voice(room.id)
                currentCoroutineContext().ensureActive()
                voice.join(connection).collect { value -> mutableState.update { it.copy(voice=value) } }
            }catch(e: CancellationException){throw e}
            catch(e: Exception){mutableState.update { it.copy(error=error(e),voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.CONNECTION)) }}
        }
    }
    fun toggleMicrophone(){
        if(mic?.isActive==true||state.value.voice.status!=VoiceStatus.CONNECTED)return
        mic=viewModelScope.launch {
            try { voice.setMicrophoneEnabled(!state.value.voice.microphoneEnabled) }
            catch(e: CancellationException){throw e}
            catch(_: Exception){mutableState.update { it.copy(voice=it.voice.copy(error=VoiceFailure.MICROPHONE)) }}
        }
    }
    private fun stopVoice(){mic?.cancel();call?.cancel();mutableState.update { it.copy(voice=VoiceState()) }}
    private fun startHeartbeat(){
        heartbeat?.cancel()
        val id=state.value.snapshot.room?.id ?: return
        if(!foreground)return
        heartbeat=viewModelScope.launch {
            while(isActive){
                delay(heartbeatMs)
                try {
                    val room=rooms.heartbeat(id)
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),error=null) }
                }catch(e: CancellationException){throw e}
                catch(e: Exception){
                    stopVoice()
                    mutableState.update { it.copy(error=error(e)) }
                    if(error(e)!=RoomError.NETWORK){mutableState.update { it.copy(snapshot=it.snapshot.copy(room=null)) };break}
                }
            }
        }
    }
    fun destinationChanged(value: Destination?){
        if(value==desiredDestination)return
        desiredDestination=value
        destinationRevision++
        mutableState.update { it.copy(nearby=emptyList()) }
    }
    /** Missing-trip creation requires an explicit startup/selection request, never a plain refresh. */
    fun syncDestination(){
        syncPending=true
        if(state.value.busy)return
        syncPending=false
        val registerMissing=registrationPending
        registrationPending=false
        perform {
            val destination=desiredDestination
            if(registerMissing&&destination!=null) {
                // Bootstrap may discover a room from the previous app version/process.
                // A missing/invalid local credential is still checked by register; it never gets silently replaced.
                val previous=try {rooms.snapshot()} catch(e: RoomAccessException) {
                    if(e.reason!=RoomError.UNAUTHORIZED)throw e
                    RoomSnapshot(null,null,null)
                }
                if(previous.room!=null&&previous.destination!=destination){stopVoice();rooms.leave(previous.room.id)}
                trips.register(destination)
            }
            else {
                val snapshot=rooms.snapshot()
                currentCoroutineContext().ensureActive()
                if(snapshot.tripId!=null&&destination!=null&&destination!=snapshot.destination)trips.register(destination)
                if(snapshot.tripId!=null&&destination==null)rooms.endTrip()
            }
            reload()
            loadNearby()
        }
    }
    fun foreground(){
        if(foreground)return
        foreground=true
        viewModelScope.launch {cleanup?.join();if(foreground)refresh()}
    }
    fun background(){
        foreground=false;syncPending=false;refreshPending=false;registrationPending=false;generation++;action?.cancel();heartbeat?.cancel();stopVoice()
        mutableState.update { it.copy(busy=false) }
        val room=state.value.snapshot.room ?: return
        cleanup=viewModelScope.launch {
            try { rooms.leave(room.id);mutableState.update { it.copy(snapshot=it.snapshot.copy(room=null)) } }
            catch(e: CancellationException){throw e}
            catch(_: Exception){ /* Server lease expires if a final leave cannot be delivered. */ }
        }
    }
}
