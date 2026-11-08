package com.routemate.app

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.domain.*
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.trip.infrastructure.*
import com.routemate.app.vehicle.domain.*
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real backend + PostGIS + LiveKit; skips explicitly unless the local stack is provided. */
class MvpConnectionTest {
    private class Store:SessionStore {
        var token:String?=null
        override fun load()=token
        override fun save(token:String){this.token=token}
    }
    @Test fun threeDriversRegisterMatchJoinPublishSubscribeLeaveAndEndTrip()=runBlocking {
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank()&&InstrumentationRegistry.getArguments().getString("mvpE2E")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}").close()
        val stores=List(3){Store()}
        val providers=List(3){VehicleIdentityProvider { VehicleIdentity(Vin("KNA"+UUID.randomUUID().toString().replace("-","").take(14)),VehicleSource.MOCK,null) }}
        // Each driver keeps a stable synthetic VIN for the entire scenario.
        val identities=providers.map{it.currentVehicle()}
        val stable=identities.map{identity->VehicleIdentityProvider{identity}}
        val trips=List(3){index->HttpTripRegistration(BuildConfig.BACKEND_URL,{stores[index]},stable[index],allowLocalHttp=true)}
        val rooms=List(3){index->HttpRoomAccess(BuildConfig.BACKEND_URL,{stores[index]},stable[index],allowLocalHttp=true)}
        val calls=List(3){LiveKitVoiceCall(context)}
        val states=List(3){MutableStateFlow(VoiceState())}
        val jobs=mutableListOf<Job>()
        var room:RoomInfo?=null
        try{
            withTimeout(120_000){
                trips.forEachIndexed{index,trip->trip.register(Destination("MVP 목적지",36.0+index*0.001,127.0))}
                room=rooms[0].create("MVP 연결 ${UUID.randomUUID().toString().take(8)}",true,3)
                val id=room!!.id
                assertTrue(rooms[1].nearby(5).any{it.room.id==id})
                rooms[1].join(id);rooms[2].join(id)
                assertEquals(3,rooms[0].snapshot().room!!.participants)
                val connections=rooms.map{it.voice(id)}
                calls.forEachIndexed{index,call->jobs+=launch{call.join(connections[index]).collect{states[index].value=it}}}
                states.forEach{it.first{state->state.status==VoiceStatus.CONNECTED&&state.participants.size==3}}
                calls.forEach{it.setMicrophoneEnabled(true)}
                states.forEach{it.first{state->state.microphoneEnabled&&state.subscribedAudioTracks==2}}
                calls[0].setMicrophoneEnabled(false);states[0].first{!it.microphoneEnabled}
                // A real backend leave must remove the third SDK session, even if the client does not disconnect itself.
                rooms[2].leave(id)
                states[2].first{it.status in setOf(VoiceStatus.DISCONNECTED,VoiceStatus.ERROR)}
                states.take(2).forEach{it.first{state->state.participants.size==2}}
                assertEquals(2,rooms[0].heartbeat(id).participants)
                jobs[2].cancelAndJoin()
                // A new join obtains a new membership and can reconnect after a previous leave.
                rooms[2].join(id)
                val retry=rooms[2].voice(id)
                jobs[2]=launch{calls[2].join(retry).collect{states[2].value=it}}
                states[2].first{it.status==VoiceStatus.CONNECTED&&it.participants.size==3}
                rooms.forEach{it.endTrip()}
                states.forEach{it.first{state->state.status in setOf(VoiceStatus.DISCONNECTED,VoiceStatus.ERROR)}}
                assertNull(rooms[0].snapshot().tripId)
                assertNull(rooms[0].snapshot().room)
            }
        }finally{
            jobs.forEach{it.cancel()};jobs.joinAll()
            withContext(NonCancellable){rooms.forEach{runCatching{it.endTrip()}}}
        }
    }
}
