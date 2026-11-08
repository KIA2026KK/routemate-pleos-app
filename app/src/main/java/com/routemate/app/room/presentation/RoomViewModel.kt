package com.routemate.app.room.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.domain.*
import com.routemate.app.trip.domain.TripRegistration
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoomUiState(val snapshot: RoomSnapshot=RoomSnapshot(null,null,null),val nearby: List<NearbyRoom> = emptyList(),
    val radiusKm: Int=5,val busy: Boolean=false,val error: RoomError?=null,val voice: VoiceState=VoiceState())

class RoomViewModel(private val rooms: ManageRooms,private val voice: VoiceCall,private val trips: TripRegistration,
    private val heartbeatMs: Long=20_000) : ViewModel() {
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
    private var syncPending=false
    private var refreshPending=false
    private fun error(e: Exception)=(e as? RoomAccessException)?.reason ?: if(e is IllegalArgumentException)RoomError.INVALID_REQUEST else RoomError.SERVER
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
        mutableState.update { it.copy(snapshot=snapshot) }
        if(snapshot.room==null)stopVoice()
        startHeartbeat()
    }
    fun refresh(){
        if(state.value.busy){refreshPending=true;return}
        refreshPending=false;perform { reload() }
    }
    fun search(radiusKm: Int)=perform {
        mutableState.update { it.copy(radiusKm=radiusKm,nearby=emptyList()) }
        val found=rooms.nearby(radiusKm)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(nearby=found) }
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
        perform { rooms.endTrip();currentCoroutineContext().ensureActive();mutableState.value=RoomUiState();heartbeat?.cancel() }
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
    fun destinationChanged(value: Destination?){desiredDestination=value}
    /** Only sync a known active trip; initial destination loading never creates an account. */
    fun syncDestination(){
        syncPending=true
        if(state.value.busy)return
        syncPending=false
        perform {
            val snapshot=rooms.snapshot()
            val destination=desiredDestination
            if(snapshot.tripId!=null&&destination!=null&&destination!=snapshot.destination)trips.register(destination)
            if(snapshot.tripId!=null&&destination==null)rooms.endTrip()
            reload()
        }
    }
    fun foreground(){foreground=true;viewModelScope.launch {cleanup?.join();if(foreground)refresh()}}
    fun background(){
        foreground=false;syncPending=false;refreshPending=false;generation++;action?.cancel();heartbeat?.cancel();stopVoice()
        mutableState.update { it.copy(busy=false) }
        val room=state.value.snapshot.room ?: return
        cleanup=viewModelScope.launch {
            try { rooms.leave(room.id);mutableState.update { it.copy(snapshot=it.snapshot.copy(room=null)) } }
            catch(e: CancellationException){throw e}
            catch(_: Exception){ /* Server lease expires if a final leave cannot be delivered. */ }
        }
    }
}
