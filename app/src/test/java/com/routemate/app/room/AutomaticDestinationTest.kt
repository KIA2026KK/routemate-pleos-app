package com.routemate.app.room

import androidx.lifecycle.ViewModelStore
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.domain.*
import com.routemate.app.room.presentation.RoomViewModel
import com.routemate.app.trip.domain.*
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticDestinationTest {
    private val first = Destination("광주송정역", 35.1374, 126.7916)
    private val next = Destination("서울역", 37.5547, 126.9706)
    private class Access : RoomAccess {
        var current = RoomSnapshot(null, null, null); val searches = mutableListOf<Int>()
        override suspend fun snapshot() = current
        override suspend fun nearby(radiusKm: Int): List<NearbyRoom> { searches += radiusKm; return emptyList() }
        override suspend fun endTrip() { current = RoomSnapshot(null, null, null) }
        override suspend fun create(name: String, isPublic: Boolean, capacity: Int): RoomInfo = error("Not expected")
        override suspend fun join(id: String): RoomInfo = error("Not expected")
        override suspend fun leave(id: String) { current=current.copy(room=null) }
        override suspend fun heartbeat(id: String): RoomInfo = error("Not expected")
        override suspend fun voice(id: String): VoiceConnection = error("Not expected")
    }
    private val voice = object : VoiceCall {
        override fun join(connection: VoiceConnection) = emptyFlow<VoiceState>()
        override suspend fun setMicrophoneEnabled(enabled: Boolean) = Unit
    }
    @Test fun startupRegistersOnceSearchesThreeKmAndDoesNotResurrectEndedTrip() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access = Access(); var registrations = 0
        val vm = RoomViewModel(ManageRooms(access), voice, TripRegistration { destination ->
            registrations++; access.current = RoomSnapshot("trip", destination, null); RegisteredTrip("trip", destination)
        }); val store = ViewModelStore().apply { put("rooms", vm) }
        try {
            vm.startDestination(first); runCurrent(); vm.startDestination(first); vm.foreground(); runCurrent()
            assertEquals(1, registrations); assertEquals(3, access.searches.first()); assertEquals(first, vm.state.value.snapshot.destination)
            vm.endTrip(); runCurrent(); vm.startDestination(first); vm.foreground(); runCurrent()
            assertNull(vm.state.value.snapshot.tripId); assertEquals(1, registrations)
            vm.registerDestination(first); runCurrent(); assertEquals(2, registrations)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun selectionDuringStartupRegistersLatestDestinationAfterPendingWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access = Access(); val gate = CompletableDeferred<Unit>(); val writes = mutableListOf<Destination>()
        val vm = RoomViewModel(ManageRooms(access), voice, TripRegistration { destination ->
            writes += destination; if (writes.size == 1) gate.await()
            access.current = RoomSnapshot("trip", destination, null); RegisteredTrip("trip", destination)
        }); val store = ViewModelStore().apply { put("rooms", vm) }
        try {
            vm.startDestination(first); runCurrent(); vm.registerDestination(next); runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals(listOf(first, next), writes); assertEquals(next, vm.state.value.snapshot.destination)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun unauthorizedStartupNeverLoopsOrSilentlyReplacesAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempts = 0
        val vm = RoomViewModel(ManageRooms(Access()), voice, TripRegistration {
            attempts++; throw TripRegistrationException(TripFailure.UNAUTHORIZED)
        }); val store = ViewModelStore().apply { put("rooms", vm) }
        try {
            vm.startDestination(first); runCurrent(); vm.startDestination(first); runCurrent()
            assertEquals(1, attempts); assertEquals(RoomError.UNAUTHORIZED, vm.state.value.error)
            vm.registerDestination(first); runCurrent(); assertEquals(2, attempts)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun removedTenKmRadiusIsRejectedBeforeTransport() = runTest {
        val access = Access(); val failure = runCatching { ManageRooms(access).nearby(10) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException); assertTrue(access.searches.isEmpty())
    }
    @Test fun startupLeavesPersistedRoomForAnotherDestinationBeforeWritingNewDestination()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val access=Access().apply {current=RoomSnapshot("existing-trip",next,RoomInfo("old-room","서울 동행",true,4,1,"later"))}
        val vm=RoomViewModel(ManageRooms(access),voice,TripRegistration { destination ->
            assertNull(access.current.room)
            access.current=access.current.copy(destination=destination)
            RegisteredTrip("existing-trip",destination)
        });val store=ViewModelStore().apply{put("rooms",vm)}
        try{vm.startDestination(first);runCurrent();assertNull(vm.state.value.snapshot.room);assertEquals(first,vm.state.value.snapshot.destination)}
        finally{store.clear();Dispatchers.resetMain()}
    }
}
