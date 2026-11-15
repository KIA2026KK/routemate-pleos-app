package com.routemate.app.room

import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.*
import com.routemate.app.room.domain.*
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentRoomSessionTest {
    private val destination=Destination("광주송정역",35.1374,126.7916)
    private val room=RoomInfo("one","동행",true,4,1,"later","CODE0001")
    private class Access(var current:RoomSnapshot):RoomAccess {
        var failure:RoomError?=null;var heartbeats=0;var leaves=0;var grants=0
        var searchGate:CompletableDeferred<Unit>?=null;var switchFailure:RoomError?=null
        override suspend fun snapshot()=current
        override suspend fun nearby(radiusKm:Int):List<NearbyRoom>{searchGate?.await();return emptyList()}
        override suspend fun create(name:String,isPublic:Boolean,capacity:Int)=current.room!!
        override suspend fun join(id:String)=current.room!!
        override suspend fun switchTo(invitation:String):RoomInfo{
            switchFailure?.let{throw RoomAccessException(it)}
            return current.room!!.copy(id="two",inviteCode="CODE0002").also{current=current.copy(room=it)}
        }
        override suspend fun leave(id:String){leaves++;current=current.copy(room=null)}
        override suspend fun endTrip(){current=RoomSnapshot(null,null,null)}
        override suspend fun heartbeat(id:String):RoomInfo{heartbeats++;failure?.let{throw RoomAccessException(it)};return current.room!!}
        override suspend fun voice(id:String):VoiceConnection{grants++;return VoiceConnection("ws://localhost:7880","test-token")}
    }
    private class Call:VoiceCall {
        var joins=0;var closes=0;val microphone=mutableListOf<Boolean>();var disconnect:CompletableDeferred<Unit>?=null
        override fun join(connection:VoiceConnection)=flow {
            val attempt=++joins
            try{
                emit(VoiceState(status=VoiceStatus.CONNECTED,participants=listOf("me")))
                if(attempt==1&&disconnect!=null){disconnect!!.await();emit(VoiceState(status=VoiceStatus.DISCONNECTED,disconnectReason="SIGNAL_CLOSE"))}
                else awaitCancellation()
            }finally{closes++}
        }
        override suspend fun setMicrophoneEnabled(enabled:Boolean){microphone+=enabled}
    }
    private class Runtime:CallRuntime {
        var active=false
        override fun start(){active=true}
        override fun stop(){active=false}
    }
    private data class Fixture(val session:RoomSession,val access:Access,val call:Call,val runtime:Runtime)
    private fun TestScope.fixture():Fixture {
        val access=Access(RoomSnapshot("trip",destination,room));val call=Call();val runtime=Runtime()
        val session=RoomSession(ManageRooms(access),call,TripRegistration{RegisteredTrip("trip",it)},heartbeatMs=1000,
            leaseMs=3500,retryMs=250,clockMillis={testScheduler.currentTime},runtime=runtime)
        session.refresh();runCurrent();session.connect(true);runCurrent()
        return Fixture(session,access,call,runtime)
    }
    @Test fun transientHeartbeatFailureKeepsMediaAndBackgroundSessionAlive()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            f.access.failure=RoomError.SERVER;f.session.background();advanceTimeBy(1001);runCurrent()
            assertTrue(f.runtime.active);assertTrue(f.session.state.value.callActive);assertEquals(0,f.call.closes)
            f.access.failure=null;advanceTimeBy(251);runCurrent()
            assertNull(f.session.state.value.error);assertEquals(1,f.call.joins)
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun repeatedRefreshDoesNotPostponeHeartbeats()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            val before=f.access.heartbeats
            repeat(6){advanceTimeBy(500);f.session.refresh();runCurrent()}
            assertTrue(f.access.heartbeats>=before+3);assertTrue(f.runtime.active)
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun prolongedControlPlaneOutageStopsBeforeMembershipLeaseExpires()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            f.access.failure=RoomError.NETWORK;f.session.background();advanceTimeBy(3501);runCurrent()
            assertFalse(f.runtime.active);assertFalse(f.session.state.value.callActive);assertEquals(1,f.call.closes)
            assertTrue(f.session.diagnostics.value.any{it.code=="lease-safety-timeout"})
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun transientSdkDisconnectObtainsFreshGrantAndRestoresRequestedMicrophone()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            f.session.leave();runCurrent()
            f.access.current=RoomSnapshot("trip",destination,room);f.call.disconnect=CompletableDeferred();f.call.joins=0
            f.session.refresh();runCurrent();f.session.connect(true);runCurrent()
            f.session.toggleMicrophone();runCurrent();f.call.disconnect!!.complete(Unit);runCurrent()
            advanceTimeBy(251);runCurrent()
            assertEquals(2,f.call.joins);assertEquals(listOf(true,true),f.call.microphone)
            assertEquals(VoiceStatus.CONNECTED,f.session.state.value.voice.status)
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun rejectedRoomSwitchPreservesCurrentMediaAndMembership()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            f.access.switchFailure=RoomError.ROOM_FULL;f.session.switchTo("CODE0002");runCurrent()
            assertEquals("one",f.session.state.value.snapshot.room?.id);assertEquals(VoiceStatus.CONNECTED,f.session.state.value.voice.status)
            assertTrue(f.runtime.active);assertEquals(0,f.call.closes)
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun explicitLeaveStopsAudioImmediatelyAndSerializesBehindPendingRequest()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=fixture()
        try{
            f.access.searchGate=CompletableDeferred();f.session.search(3);runCurrent()
            f.session.leave();f.session.leave();runCurrent()
            assertFalse(f.runtime.active);assertTrue(f.session.state.value.leaving);assertEquals(1,f.call.closes)
            f.access.searchGate!!.complete(Unit);runCurrent()
            assertEquals(1,f.access.leaves);assertNull(f.session.state.value.snapshot.room);assertFalse(f.session.state.value.leaving)
        }finally{f.session.close();runCurrent();Dispatchers.resetMain()}
    }
}
