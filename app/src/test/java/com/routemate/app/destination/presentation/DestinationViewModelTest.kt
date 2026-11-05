package com.routemate.app.destination.presentation

import androidx.lifecycle.ViewModelStore
import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure
import com.routemate.app.destination.domain.DestinationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationViewModelTest {
    @Test fun destinationChangeAndRemovalReachUiAndClearingCancelsSubscription() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val updates = MutableSharedFlow<Destination?>(replay = 1)
            var closed = 0
            val provider = object : DestinationProvider {
                override suspend fun currentDestination(): Destination? = error("Use observation")
                override fun observeDestinations(): Flow<Destination?> = flow {
                    try { emitAll(updates) } finally { closed++ }
                }
            }
            val model = DestinationViewModel(LoadDestination(provider))
            store.put("destination", model)
            assertEquals(DestinationState.Loading, model.state.value)
            runCurrent()
            updates.emit(Destination("서울역", 37.55, 126.97))
            runCurrent()
            assertEquals("서울역", (model.state.value as DestinationState.Loaded).destination.name)
            updates.emit(Destination("부산역", 35.11, 129.04))
            runCurrent()
            assertEquals("부산역", (model.state.value as DestinationState.Loaded).destination.name)
            updates.emit(null)
            runCurrent()
            assertEquals(DestinationState.Empty, model.state.value)
            store.clear()
            runCurrent()
            assertEquals(1, closed)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun failureCanBeRetriedWithoutLosingTypedUiError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var attempts = 0
            val provider = object : DestinationProvider {
                override suspend fun currentDestination(): Destination? = null
                override fun observeDestinations(): Flow<Destination?> = flow {
                    attempts++
                    if (attempts == 1) throw DestinationAccessException(DestinationFailure.CONNECTION)
                    emit(Destination("서울역", 37.55, 126.97))
                }
            }
            val model = DestinationViewModel(LoadDestination(provider))
            store.put("destination", model)
            model.refresh() // Loading prevents a duplicate session.
            runCurrent()
            assertEquals(DestinationState.Error(DestinationFailure.CONNECTION), model.state.value)
            model.refresh()
            runCurrent()
            assertEquals(2, attempts)
            assertTrue(model.state.value is DestinationState.Loaded)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
