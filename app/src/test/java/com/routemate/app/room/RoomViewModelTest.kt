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
        var searchGate:CompletableDeferred<Unit>?=null
        var nearbyResults:List<NearbyRoom> = emptyList()
        override suspend fun snapshot()=snapshot
        override suspend fun nearby(radiusKm:Int):List<NearbyRoom>{searchGate?.await();return nearbyResults}
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
    @Test fun duplicateCreateBlockedAndBackgroundKeepsCallUntilExplicitLeave()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));access.gate=CompletableDeferred()
        val call=Call();val(vm,store)=fixture(access,call)
        try{
            vm.create("동행",true,3);vm.create("동행",true,3);runCurrent();assertEquals(1,access.creates)
            access.gate!!.complete(Unit);runCurrent();vm.connect(true);runCurrent();assertEquals(VoiceStatus.CONNECTED,vm.state.value.voice.status)
            vm.background();runCurrent();assertEquals(0,call.closed);assertEquals(0,access.leaves);assertNotNull(vm.state.value.snapshot.room)
            vm.leave();runCurrent();assertEquals(1,call.closed);assertEquals(1,access.leaves);assertNull(vm.state.value.snapshot.room)
            assertEquals(VoiceStatus.IDLE,vm.state.value.voice.status)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun transientHeartbeatFailurePreservesAudioAndRetryUpdatesRoom()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));val call=Call();val(vm,store)=fixture(access,call)
        try{
            vm.refresh();runCurrent();vm.connect(true);runCurrent();access.failure=RoomError.NETWORK
            advanceTimeBy(1001);runCurrent();assertEquals(0,call.closed);assertEquals(RoomError.NETWORK,vm.state.value.error)
            access.failure=null;advanceTimeBy(2001);runCurrent();assertNull(vm.state.value.error)
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
    @Test fun changingDestinationImmediatelyClearsOldSearchResults()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,null)).apply {nearbyResults=listOf(NearbyRoom(room,100))}
        val(vm,store)=fixture(access)
        try{
            vm.destinationChanged(destination);vm.search(5);runCurrent();assertEquals(1,vm.state.value.nearby.size)
            vm.destinationChanged(Destination("부산역",35.1,129.0));assertTrue(vm.state.value.nearby.isEmpty())
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun oldSearchResponseCannotRepopulateMatchesAfterDestinationChanges()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,null)).apply {
            nearbyResults=listOf(NearbyRoom(room,100));searchGate=CompletableDeferred()
        }
        val(vm,store)=fixture(access)
        try{
            vm.destinationChanged(destination);vm.search(5);runCurrent()
            vm.destinationChanged(Destination("부산역",35.1,129.0));access.searchGate!!.complete(Unit);runCurrent()
            assertTrue(vm.state.value.nearby.isEmpty())
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun authoritativeRoomChangeClosesThePreviousVoiceSession()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));val call=Call();val(vm,store)=fixture(access,call)
        try{
            vm.refresh();runCurrent();vm.connect(true);runCurrent()
            access.snapshot=access.snapshot.copy(room=room.copy(id="another-room"));vm.refresh();runCurrent()
            assertEquals(1,call.closed);assertEquals(VoiceStatus.IDLE,vm.state.value.voice.status)
            assertEquals("another-room",vm.state.value.snapshot.room?.id)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun selectingAnotherDestinationLeavesOldRoomAndStopsAudioBeforeRegistration()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,room));val call=Call()
        val changed=Destination("광주송정역",35.1374,126.7916)
        val(vm,store)=fixture(access,call,TripRegistration { value ->
            assertEquals(1,access.leaves);assertEquals(1,call.closed)
            access.snapshot=access.snapshot.copy(destination=value)
            RegisteredTrip("trip",value)
        })
        try{
            vm.refresh();runCurrent();vm.connect(true);runCurrent();vm.registerDestination(changed);runCurrent()
            assertNull(vm.state.value.snapshot.room);assertEquals(changed,vm.state.value.snapshot.destination)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun destinationSyncPreservesNetworkAuthenticationAndConflictFailures()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failure=TripFailure.NETWORK
        val access=Access(RoomSnapshot("trip",destination,null))
        val(vm,store)=fixture(access,trips=TripRegistration {throw TripRegistrationException(failure)})
        try{
            vm.destinationChanged(Destination("부산역",35.1,129.0))
            for((source,expected) in listOf(TripFailure.NETWORK to RoomError.NETWORK,
                TripFailure.UNAUTHORIZED to RoomError.UNAUTHORIZED,TripFailure.TRIP_CHANGED to RoomError.TRIP_CHANGED)){
                failure=source;vm.syncDestination();runCurrent();assertEquals(expected,vm.state.value.error)
            }
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun authoritativeDestinationChangeInvalidatesPreviouslySearchedMatches()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access(RoomSnapshot("trip",destination,null)).apply{nearbyResults=listOf(NearbyRoom(room,100))}
        val(vm,store)=fixture(access)
        try{
            vm.refresh();runCurrent();vm.search(5);runCurrent();assertEquals(1,vm.state.value.nearby.size)
            access.snapshot=access.snapshot.copy(destination=Destination("부산역",35.1,129.0))
            val refreshed=listOf(NearbyRoom(room.copy(id="new-destination-room"),200))
            access.nearbyResults=refreshed;vm.refresh();runCurrent()
            assertEquals(refreshed,vm.state.value.nearby)
        }finally{store.clear();Dispatchers.resetMain()}
    }
}
