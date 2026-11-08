package com.routemate.app.room

import androidx.lifecycle.ViewModelStore
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.domain.*
import com.routemate.app.room.presentation.*
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelTest {
    private val destination=Destination("서울역",37.55,126.97)
    private val room=RoomInfo("room","동행",true,3,1,"later")
    private class Access(var snapshot:RoomSnapshot):RoomAccess {
        var creates=0;var leaves=0;var heartbeats=0;var endings=0
        var failure:RoomError?=null
        var gate:CompletableDeferred<Unit>?=null
        override suspend fun snapshot()=snapshot
        override suspend fun nearby(radiusKm:Int)=listOf<NearbyRoom>()
        override suspend fun create(name:String,isPublic:Boolean,capacity:Int):RoomInfo {creates++;gate?.await();return snapshot.room!!}
        override suspend fun join(id:String)=snapshot.room!!
        override suspend fun leave(id:String){leaves++;snapshot=snapshot.copy(room=null)}
        override suspend fun heartbeat(id:String):RoomInfo {heartbeats++;failure?.let{throw RoomAccessException(it)};return snapshot.room!!}
        override suspend fun endTrip(){endings++;snapshot=RoomSnapshot(null,null,null)}
        override suspend fun voice(id:String)=VoiceConnection("ws://localhost:7880","token")
    }
    private class Call:VoiceCall {
        var closed=0;var mic=false
        override fun join(connection:VoiceConnection)=flow {try{emit(VoiceState(status=VoiceStatus.CONNECTED));awaitCancellation()}finally{closed++}}
        override suspend fun setMicrophoneEnabled(enabled:Boolean){mic=enabled}
    }
    private fun fixture(access:Access,call:Call=Call(),trips:TripRegistration=TripRegistration{error("Unexpected trip registration")}):Pair<RoomViewModel,ViewModelStore>{
        val vm=RoomViewModel(ManageRooms(access),call,trips,heartbeatMs=1000);val store=ViewModelStore();store.put("rooms",vm);return vm to store
    }
    @Test fun duplicateCreateBlockedAndBackgroundStopsCallAndLeavesRoom()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));access.gate=CompletableDeferred()
        val call=Call();val(vm,store)=fixture(access,call)
        try{
            vm.create("동행",true,3);vm.create("동행",true,3);runCurrent();assertEquals(1,access.creates)
            access.gate!!.complete(Unit);runCurrent();vm.connect(true);runCurrent();assertEquals(VoiceStatus.CONNECTED,vm.state.value.voice.status)
            vm.background();runCurrent();assertEquals(1,call.closed);assertEquals(1,access.leaves);assertNull(vm.state.value.snapshot.room)
            assertEquals(VoiceStatus.IDLE,vm.state.value.voice.status)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun heartbeatFailureReleasesAudioAndRetryUpdatesRoom()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));val call=Call();val(vm,store)=fixture(access,call)
        try{
            vm.refresh();runCurrent();vm.connect(true);runCurrent();access.failure=RoomError.NETWORK
            advanceTimeBy(1001);runCurrent();assertEquals(1,call.closed);assertEquals(RoomError.NETWORK,vm.state.value.error)
            access.failure=null;advanceTimeBy(1000);runCurrent();assertNull(vm.state.value.error)
            assertNotNull(vm.state.value.snapshot.room)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun deniedMicrophoneDoesNotConnectAndExpiredMembershipClearsRoom()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));val(vm,store)=fixture(access)
        try{
            vm.refresh();runCurrent();vm.connect(false);assertEquals(VoiceFailure.PERMISSION,vm.state.value.voice.error)
            access.failure=RoomError.NOT_IN_ROOM;advanceTimeBy(1001);runCurrent();assertNull(vm.state.value.snapshot.room)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun destinationChangeUpdatesExistingTripAndRemovalEndsIt()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,null));var updated:Destination?=null
        val(vm,store)=fixture(access,trips=TripRegistration { updated=it;RegisteredTrip("trip",it,"vehicle","masked") })
        try{
            val changed=Destination("부산역",35.1,129.0);vm.destinationChanged(changed);vm.syncDestination();runCurrent();assertEquals(changed,updated)
            vm.destinationChanged(null);vm.syncDestination();runCurrent();assertEquals(1,access.endings);assertNull(vm.state.value.snapshot.tripId)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun destinationChangeWhileAnotherRequestIsBusyIsAppliedAfterward()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));access.gate=CompletableDeferred()
        var updated:Destination?=null
        val(vm,store)=fixture(access,trips=TripRegistration{updated=it;RegisteredTrip("trip",it)})
        try{
            vm.create("동행",true,3);runCurrent()
            val changed=Destination("부산역",35.1,129.0);vm.destinationChanged(changed);vm.syncDestination();runCurrent()
            assertNull(updated);access.gate!!.complete(Unit);runCurrent();assertEquals(changed,updated)
        }finally{store.clear();Dispatchers.resetMain()}
    }
}
