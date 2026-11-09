package com.routemate.app.prototype

import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.domain.*
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.*

/** Offline, explicitly labelled presentation prototype. No network, VIN lookup or real audio. */
class PrototypeSession : RoomAccess,TripRegistration {
    private var tripId:String?=null
    private var destination:Destination?=null
    private var current:RoomInfo?=null
    private val expiry=Instant.now().plusSeconds(7200).toString()
    private val examples=listOf(
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc11","서울역으로 가는 길",true,4,2,expiry),1200),
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc12","여유로운 도심 드라이브",true,6,3,expiry),3400),
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc13","함께하는 저녁 이동",true,4,1,expiry),8200),
    )
    override suspend fun register(destination:Destination):RegisteredTrip{
        if(tripId==null)tripId=UUID.randomUUID().toString()
        this.destination=destination
        return RegisteredTrip(tripId!!,destination,"prototype-vehicle","*************0001")
    }
    override suspend fun snapshot()=RoomSnapshot(tripId,destination,current)
    override suspend fun nearby(radiusKm:Int):List<NearbyRoom>{requireTrip();return examples.filter{it.distanceMeters<=radiusKm*1000}}
    override suspend fun create(name:String,isPublic:Boolean,capacity:Int):RoomInfo{
        requireTrip();return RoomInfo(UUID.randomUUID().toString(),name,isPublic,capacity,1,expiry).also{current=it}
    }
    override suspend fun join(id:String):RoomInfo{
        requireTrip();return (examples.find{it.room.id==id}?.room?.let{it.copy(participants=it.participants+1)}
            ?:throw RoomAccessException(RoomError.ROOM_UNAVAILABLE)).also{current=it}
    }
    override suspend fun leave(id:String){current=null}
    override suspend fun heartbeat(id:String)=current?.takeIf{it.id==id}?:throw RoomAccessException(RoomError.NOT_IN_ROOM)
    override suspend fun endTrip(){tripId=null;destination=null;current=null}
    override suspend fun voice(id:String):VoiceConnection{
        if(current?.id!=id)throw RoomAccessException(RoomError.NOT_IN_ROOM)
        return VoiceConnection("wss://prototype.invalid","prototype-only")
    }
    private fun requireTrip(){if(tripId==null)throw RoomAccessException(RoomError.TRIP_REQUIRED)}
}

class PrototypeVoiceCall:VoiceCall {
    private val state=MutableStateFlow(VoiceState())
    override fun join(connection:VoiceConnection):Flow<VoiceState> = flow {
        state.value=VoiceState(status=VoiceStatus.CONNECTED,participants=listOf("체험 사용자","동행 1","동행 2"))
        try{emitAll(state)}finally{state.value=VoiceState()}
    }
    override suspend fun setMicrophoneEnabled(enabled:Boolean){state.update{it.copy(microphoneEnabled=enabled)}}
}
