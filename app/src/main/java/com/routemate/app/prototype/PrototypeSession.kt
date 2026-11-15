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
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc11","광주송정역 동행",true,4,2,expiry,"DEMO0001"),1200),
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc12","여유로운 도심 드라이브",true,6,3,expiry,"DEMO0002"),3400),
        NearbyRoom(RoomInfo("8f394f60-c849-4dc7-b9b1-003ccf78bc13","함께하는 저녁 이동",true,4,1,expiry,"DEMO0003"),4800),
    )
    override suspend fun register(destination:Destination):RegisteredTrip{
        if(tripId==null)tripId=UUID.randomUUID().toString()
        this.destination=destination
        return RegisteredTrip(tripId!!,destination,"prototype-vehicle","*************0001")
    }
    override suspend fun snapshot()=RoomSnapshot(tripId,destination,current)
    override suspend fun nearby(radiusKm:Int):List<NearbyRoom>{require(radiusKm in RoomPolicy.radii);requireTrip();return examples.filter{it.distanceMeters<=radiusKm*1000}}
    override suspend fun create(name:String,isPublic:Boolean,capacity:Int):RoomInfo{
        requireTrip();return RoomInfo(UUID.randomUUID().toString(),name,isPublic,capacity,1,expiry,"DEMO"+UUID.randomUUID().toString().take(4).uppercase()).also{current=it}
    }
    override suspend fun join(id:String):RoomInfo{
        requireTrip();val target=example(id)
        if(current!=null&&current?.id!=target.id)throw RoomAccessException(RoomError.ALREADY_IN_ROOM)
        return current?:target.copy(participants=target.participants+1).also{current=it}
    }
    private fun example(invitation:String)=examples.find{it.room.id.equals(invitation,true)||it.room.inviteCode.equals(invitation,true)}?.room
        ?:current?.takeIf{it.id.equals(invitation,true)||it.inviteCode.equals(invitation,true)}?:throw RoomAccessException(RoomError.ROOM_UNAVAILABLE)
    override suspend fun switchTo(invitation:String):RoomInfo{requireTrip();val target=example(invitation);return if(current?.id==target.id)current!! else target.copy(participants=target.participants+1).also{current=it}}
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
        state.value=VoiceState(status=VoiceStatus.CONNECTED,participants=listOf("demo-me","demo-one","demo-two"),members=listOf(
            VoiceParticipant("demo-me","나",local=true),VoiceParticipant("demo-one","동행 1",speaking=true,microphoneEnabled=true,audioLevel=.6f),VoiceParticipant("demo-two","동행 2")))
        try{emitAll(state)}finally{state.value=VoiceState()}
    }
    override suspend fun setMicrophoneEnabled(enabled:Boolean){state.update{it.copy(microphoneEnabled=enabled,members=it.members.map{person->if(person.local)person.copy(microphoneEnabled=enabled,speaking=enabled,audioLevel=if(enabled).4f else 0f)else person})}}
}
