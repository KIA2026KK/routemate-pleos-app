package com.routemate.app.room.application

import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.domain.*
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoomSessionState(val snapshot: RoomSnapshot=RoomSnapshot(null,null,null),val nearby: List<NearbyRoom> = emptyList(),
    val radiusKm: Int=RoomPolicy.defaultRadiusKm,val busy: Boolean=false,val error: RoomError?=null,val voice: VoiceState=VoiceState(),
    val callActive:Boolean=false,val reconnectAttempt:Int=0,val leaving:Boolean=false)
data class CallDiagnostic(val elapsedMs:Long,val code:String)

class RoomSession(private val rooms: ManageRooms,private val voice: VoiceCall,private val trips: TripRegistration,
    private val heartbeatMs: Long=RoomPolicy.heartbeatMs, private val leaseMs:Long=85_000, private val retryMs:Long=2_000, private val clockMillis:()->Long={System.nanoTime()/1_000_000}, private val runtime:CallRuntime=CallRuntime.None, private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)) {
    private val mutableState=MutableStateFlow(RoomSessionState())
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
    private var heartbeatRoom:String?=null
    private var leaseAt:Long?=null
    private var leaseRoom:String?=null
    private var leaseGuard:Job?=null
    private var callEpoch=0
    private var exiting=false
    private var desiredMicrophone=false
    private val beganAt=clockMillis()
    private val history=MutableStateFlow<List<CallDiagnostic>>(emptyList())
    val diagnostics=history.asStateFlow()
    private fun note(code:String){
        history.update{(it+CallDiagnostic((clockMillis()-beganAt).coerceAtLeast(0),code)).takeLast(32)}
        runtime.event(code)
    }
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
        action=scope.launch {
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
        if(state.value.snapshot.tripId==null)return
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
        val requestAt=clockMillis()
        val room=rooms.create(name,isPublic,capacity)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),nearby=emptyList()) };acknowledge(requestAt);startHeartbeat()
    }
    fun join(id: String)=perform {
        val requestAt=clockMillis()
        val room=rooms.join(id)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),nearby=emptyList()) };acknowledge(requestAt);startHeartbeat()
    }
    fun switchTo(invitation:String)=perform {
        val requestAt=clockMillis()
        val room=rooms.switchTo(invitation)
        currentCoroutineContext().ensureActive()
        if(room.id!=state.value.snapshot.room?.id)stopVoice()
        mutableState.update{it.copy(snapshot=it.snapshot.copy(room=room),nearby=emptyList())}
        acknowledge(requestAt);startHeartbeat()
    }
    private fun exit(block:suspend ()->Unit){
        if(exiting)return
        exiting=true
        val previous=action
        generation++;syncPending=false;refreshPending=false;registrationPending=false
        stopVoice();heartbeat?.cancel();leaseGuard?.cancel();heartbeatRoom=null;leaseAt=null
        mutableState.update{it.copy(busy=true,error=null,leaving=true)}
        action=scope.launch {
            try {previous?.join();block()}
            catch(e:CancellationException){throw e}
            catch(e:Exception){mutableState.update{it.copy(error=error(e))}}
            finally{exiting=false;mutableState.update{it.copy(busy=false,leaving=false)}}
        }
    }
    fun leave() {
        if(state.value.snapshot.room==null)return
        exit {
            val current=rooms.snapshot().room
            if(current!=null)rooms.leave(current.id)
            reload();loadNearby();note("room-left")
        }
    }
    fun endTrip() {
        exit {rooms.endTrip();currentCoroutineContext().ensureActive();mutableState.value=RoomSessionState(radiusKm=state.value.radiusKm);note("trip-ended")}
    }
    fun connect(permitted: Boolean) {
        if(!permitted){mutableState.update { it.copy(voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.PERMISSION)) };return}
        if(call?.isActive==true||state.value.busy)return
        val room=state.value.snapshot.room ?: return
        val previous=call
        val epoch=++callEpoch
        mutableState.update{it.copy(callActive=true,reconnectAttempt=0,error=null)}
        try{runtime.start()}catch(_:Exception){
            mutableState.update{it.copy(callActive=false,voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.PERMISSION))}
            note("runtime-start-rejected");return
        }
        startHeartbeat()
        call=scope.launch {
            try {
                previous?.cancelAndJoin()
                var failures=0
                while(isActive&&epoch==callEpoch&&state.value.snapshot.room?.id==room.id){
                    mutableState.update{it.copy(voice=it.voice.copy(status=if(failures==0)VoiceStatus.CONNECTING else VoiceStatus.RECONNECTING),reconnectAttempt=failures)}
                    note(if(failures==0)"voice-connect" else "voice-retry-$failures")
                    try{
                        val requestAt=clockMillis()
                        val connection=rooms.voice(room.id)
                        currentCoroutineContext().ensureActive();acknowledge(requestAt)
                        var restore=desiredMicrophone
                        var lastStatus:VoiceStatus?=null
                        voice.join(connection).collect{value->
                            if(epoch!=callEpoch)return@collect
                            mutableState.update{it.copy(voice=value)}
                            if(lastStatus!=value.status){note("voice-${value.status.name.lowercase()}");lastStatus=value.status}
                            if(value.status==VoiceStatus.CONNECTED){
                                failures=0
                                if(restore){restore=false;mic=scope.launch{if(desiredMicrophone&&epoch==callEpoch)runCatching{voice.setMicrophoneEnabled(true)}}}
                            }
                        }
                        val reason=state.value.voice.disconnectReason
                        if(reason!=null)note("voice-ended-$reason")
                        if(reason in setOf("PARTICIPANT_REMOVED","ROOM_DELETED","ROOM_CLOSED","USER_REJECTED","DUPLICATE_IDENTITY","CLIENT_INITIATED"))break
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception){
                        val failure=error(e);note("voice-request-${failure.name.lowercase()}")
                        if(failure !in setOf(RoomError.NETWORK,RoomError.SERVER,RoomError.VOICE_UNAVAILABLE)){
                            mutableState.update{it.copy(error=failure,snapshot=if(failure in setOf(RoomError.NOT_IN_ROOM,RoomError.ROOM_UNAVAILABLE,RoomError.UNAUTHORIZED,RoomError.TRIP_REQUIRED))it.snapshot.copy(room=null)else it.snapshot)};break
                        }
                    }
                    failures++
                    if(failures>=4)break
                    mutableState.update{it.copy(voice=it.voice.copy(status=VoiceStatus.RECONNECTING),reconnectAttempt=failures)}
                    delay((retryMs*failures).coerceAtMost(10_000))
                }
            }catch(e: CancellationException){throw e}
            catch(e: Exception){if(epoch==callEpoch)mutableState.update { it.copy(error=error(e),voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.CONNECTION)) }}
            finally{
                if(epoch==callEpoch){
                    mutableState.update{it.copy(callActive=false,voice=it.voice.copy(status=VoiceStatus.ERROR,error=VoiceFailure.CONNECTION))}
                    runtime.stop()
                    if(!foreground){heartbeat?.cancel();leaseGuard?.cancel()}
                }
            }
        }
    }
    fun toggleMicrophone(){
        if(mic?.isActive==true||state.value.voice.status!=VoiceStatus.CONNECTED)return
        mic=scope.launch {
            try {desiredMicrophone=!state.value.voice.microphoneEnabled;voice.setMicrophoneEnabled(desiredMicrophone)}
            catch(e: CancellationException){throw e}
            catch(_: Exception){mutableState.update { it.copy(voice=it.voice.copy(error=VoiceFailure.MICROPHONE)) }}
        }
    }
    private fun stopVoice(){
        callEpoch++;desiredMicrophone=false;mic?.cancel();call?.cancel()
        mutableState.update { it.copy(voice=VoiceState(),callActive=false,reconnectAttempt=0) }
        runtime.stop()
    }
    private fun acknowledge(requestAt:Long){
        leaseAt=requestAt;leaseGuard?.cancel()
        val id=state.value.snapshot.room?.id ?: return
        leaseRoom=id
        leaseGuard=scope.launch{
            delay((leaseMs-(clockMillis()-requestAt)).coerceAtLeast(0))
            if(state.value.snapshot.room?.id==id&&leaseAt==requestAt){
                note("lease-safety-timeout");stopVoice();heartbeat?.cancel()
                mutableState.update{it.copy(error=RoomError.NETWORK,voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.TIMEOUT))}
            }
        }
    }
    private fun startHeartbeat(){
        val id=state.value.snapshot.room?.id
        if(id==heartbeatRoom&&heartbeat?.isActive==true)return
        heartbeat?.cancel()
        if(id==null){leaseGuard?.cancel();leaseAt=null;leaseRoom=null;heartbeatRoom=null;return}
        if(!foreground&&!state.value.callActive)return
        if(heartbeatRoom!=id&&leaseRoom!=id){leaseAt=null;leaseGuard?.cancel()}
        heartbeatRoom=id
        heartbeat=scope.launch {
            var wait=if(leaseAt==null)0L else heartbeatMs
            var failures=0
            while(isActive){
                delay(wait)
                val requestAt=clockMillis()
                try {
                    val room=rooms.heartbeat(id)
                    currentCoroutineContext().ensureActive()
                    acknowledge(requestAt);failures=0;wait=heartbeatMs
                    mutableState.update { it.copy(snapshot=it.snapshot.copy(room=room),error=null) }
                }catch(e: CancellationException){throw e}
                catch(e: Exception){
                    val reason=error(e);note("heartbeat-${reason.name.lowercase()}")
                    mutableState.update { it.copy(error=reason) }
                    if(reason !in setOf(RoomError.NETWORK,RoomError.SERVER,RoomError.VOICE_UNAVAILABLE)){
                        stopVoice();leaseGuard?.cancel();mutableState.update { it.copy(snapshot=it.snapshot.copy(room=null)) };break
                    }
                    // A short control-plane outage does not destroy a healthy media connection.
                    failures++;wait=(retryMs*failures).coerceAtMost(heartbeatMs)
                    if(leaseAt==null)acknowledge(requestAt)
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
        scope.launch {cleanup?.join();if(foreground)refresh()}
    }
    fun background(){
        foreground=false
        if(!state.value.callActive){
            syncPending=false;refreshPending=false;registrationPending=false;generation++;action?.cancel();heartbeat?.cancel();leaseGuard?.cancel()
            mutableState.update { it.copy(busy=false) }
        }
    }
    fun runtimeStopped(){if(state.value.callActive){note("runtime-stopped");stopVoice();heartbeat?.cancel();leaseGuard?.cancel();mutableState.update{it.copy(voice=VoiceState(status=VoiceStatus.ERROR,error=VoiceFailure.CONNECTION))}}}
    fun close(){stopVoice();scope.cancel()}
    suspend fun finishForTest(){
        stopVoice();action?.cancelAndJoin();heartbeat?.cancelAndJoin();leaseGuard?.cancelAndJoin()
        if(state.value.snapshot.tripId!=null)runCatching{rooms.endTrip()}
        scope.cancel()
    }
}
