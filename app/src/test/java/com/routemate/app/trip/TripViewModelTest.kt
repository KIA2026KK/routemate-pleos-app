package com.routemate.app.trip

import androidx.lifecycle.ViewModelStore
import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.application.RegisterTrip
import com.routemate.app.trip.domain.*
import com.routemate.app.trip.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private val seoul = Destination("서울역", 37.55, 126.97)
    private val busan = Destination("부산역", 35.11, 129.04)

    @Test fun submitRequiresDestinationAndPreventsDuplicateRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var requests = 0
            val response = CompletableDeferred<RegisteredTrip>()
            val vm = TripViewModel(RegisterTrip(TripRegistration { requests++; response.await() }))
            store.put("trip", vm)
            vm.submit()
            runCurrent()
            assertEquals(0, requests)
            vm.destinationChanged(seoul)
            vm.submit(); vm.submit()
            runCurrent()
            assertEquals(1, requests)
            response.complete(RegisteredTrip("trip-a", seoul))
            runCurrent()
            assertEquals(TripRegistrationState.Registered(RegisteredTrip("trip-a", seoul)), vm.state.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun destinationChangesCancelOldSubmissionAndClearStaleSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var cancelled = false
            val vm = TripViewModel(RegisterTrip(TripRegistration { destination ->
                if (destination == seoul) {
                    try { awaitCancellation() } finally { cancelled = true }
                }
                RegisteredTrip("trip-a", destination)
            }))
            store.put("trip", vm)
            vm.destinationChanged(seoul); vm.submit(); runCurrent()
            vm.destinationChanged(busan); runCurrent()
            assertTrue(cancelled)
            assertEquals(TripRegistrationState.Idle, vm.state.value)
            vm.submit(); runCurrent()
            assertEquals(busan, (vm.state.value as TripRegistrationState.Registered).trip.destination)
            vm.destinationChanged(null)
            assertEquals(TripRegistrationState.Idle, vm.state.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun networkFailureCanBeRetriedAndViewModelClearCancelsRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var attempts = 0
            var cancelled = false
            val vm = TripViewModel(RegisterTrip(TripRegistration { destination ->
                attempts++
                if (attempts == 1) throw TripRegistrationException(TripFailure.NETWORK)
                try { awaitCancellation() } finally { cancelled = true }
            }))
            store.put("trip", vm)
            vm.destinationChanged(seoul); vm.submit(); runCurrent()
            assertEquals(TripFailure.NETWORK, (vm.state.value as TripRegistrationState.Error).reason)
            vm.submit(); runCurrent()
            assertEquals(2, attempts)
            store.clear(); runCurrent()
            assertTrue(cancelled)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
