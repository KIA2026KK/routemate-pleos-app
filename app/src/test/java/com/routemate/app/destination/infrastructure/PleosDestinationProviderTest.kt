package com.routemate.app.destination.infrastructure

import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

internal class FakeNaviClient : NaviClient {
    lateinit var connected: () -> Unit
    lateinit var failed: (Throwable) -> Unit
    lateinit var listener: NaviClient.Listener
    var adds = 0
    var removes = 0
    var releases = 0
    var routeRequests = 0
    var destinationRequests = 0
    var registrationFailure: Exception? = null
    var removeFailure: Exception? = null

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
        connected = onConnected
        failed = onFailed
    }
    override fun addListener(listener: NaviClient.Listener) {
        this.listener = listener
        adds++
        registrationFailure?.let { throw it }
    }
    override fun removeListener(listener: NaviClient.Listener) {
        assertSame(this.listener, listener)
        removes++
        removeFailure?.let { throw it }
    }
    override fun getRouteStateInfo() { routeRequests++ }
    override fun getDestinationInfo() { destinationRequests++ }
    override fun release() { releases++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PleosDestinationProviderTest {
    @Test fun oneShotQueryWaitsForConnectionAndReleasesAfterFirstResponse() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { provider.currentDestination() }
        runCurrent()
        assertEquals(0, client.routeRequests)
        client.connected()
        client.connected() // Duplicate initialization callback must not register twice.
        runCurrent()
        assertEquals(1, client.adds)
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        assertEquals(1, client.destinationRequests)
        client.listener.onDestination(destinationInfo())
        client.listener.onDestination(destinationInfo())
        runCurrent()
        assertEquals("서울역", result.await()?.name)
        assertEquals(1, client.removes)
        assertEquals(1, client.releases)
        client.listener.onChanged() // Late callbacks after collection ended are harmless.
        client.connected()
        runCurrent()
        assertEquals(1, client.routeRequests)
    }

    @Test fun explicitNoRouteReturnsNullWithoutRequestingCoordinates() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { provider.currentDestination() }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.NONE)
        runCurrent()
        assertNull(result.await())
        assertEquals(0, client.destinationRequests)
        assertEquals(1, client.releases)
    }

    @Test fun observationHandlesDestinationChangesAndCancellation() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val values = mutableListOf<Destination?>()
        val job = backgroundScope.launch { provider.observeDestinations().collect { values += it } }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo())
        runCurrent()
        client.listener.onChanged()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo("부산역", 35.115, 129.042))
        runCurrent()
        client.listener.onRouteCancelled()
        runCurrent()
        assertEquals(listOf("서울역", "부산역", null), values.map { it?.name })
        job.cancelAndJoin()
        assertEquals(1, client.removes)
        assertEquals(1, client.releases)
    }

    @Test fun aChangeDuringAReadDiscardsTheInvalidatedSnapshot() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { provider.currentDestination() }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onChanged()
        runCurrent()
        client.listener.onDestination(destinationInfo("이전 목적지"))
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(2, client.routeRequests)
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo("변경 목적지"))
        runCurrent()
        assertEquals("변경 목적지", result.await()?.name)
    }

    @Test fun connectionFailureReleasesPartialSession() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { runCatching { provider.currentDestination() } }
        runCurrent()
        client.failed(IllegalStateException("service unavailable"))
        runCurrent()
        assertEquals(DestinationFailure.CONNECTION, (result.await().exceptionOrNull() as DestinationAccessException).reason)
        assertEquals(0, client.removes)
        assertEquals(1, client.releases)
    }

    @Test fun initializationCannotWaitForever() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { runCatching { provider.currentDestination() } }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(DestinationFailure.TIMEOUT, (result.await().exceptionOrNull() as DestinationAccessException).reason)
        assertEquals(1, client.releases)
    }

    @Test fun missingResponseIsTimeoutRatherThanNoDestination() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { runCatching { provider.currentDestination() } }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(DestinationFailure.TIMEOUT, (result.await().exceptionOrNull() as DestinationAccessException).reason)
        assertEquals(1, client.removes)
        assertEquals(1, client.releases)
    }

    @Test fun cancellationDuringConnectionReleasesAndIgnoresLateConnection() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { provider.currentDestination() }
        runCurrent()
        result.cancelAndJoin()
        client.connected()
        runCurrent()
        assertEquals(0, client.adds)
        assertEquals(1, client.releases)
    }

    @Test fun permissionFailureAndCleanupFailureStillReleaseClient() = runTest {
        val client = FakeNaviClient().apply {
            registrationFailure = SecurityException("missing permission")
            removeFailure = IllegalStateException("remove failed")
        }
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { runCatching { provider.currentDestination() } }
        runCurrent()
        client.connected()
        runCurrent()
        assertEquals(DestinationFailure.PERMISSION, (result.await().exceptionOrNull() as DestinationAccessException).reason)
        assertEquals(1, client.removes)
        assertEquals(1, client.releases)
    }

    @Test fun navigationErrorIsNotSilentlyConvertedToEmptyDestination() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { runCatching { provider.currentDestination() } }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onFailure(IllegalStateException("navigation error"))
        runCurrent()
        assertTrue(result.await().isFailure)
        assertEquals(1, client.releases)
    }

    @Test fun calculationWaitsForCompletionAndRequeries() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val result = async { provider.currentDestination() }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.CALCULATING)
        runCurrent()
        assertFalse(result.isCompleted)
        client.listener.onChanged()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo())
        runCurrent() // The change invalidated the first read, so confirm a fresh state.
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo())
        runCurrent()
        assertEquals("서울역", result.await()?.name)
    }

    @Test fun healthyIdleSubscriptionDoesNotTimeoutAndStillReceivesChanges() = runTest {
        val client = FakeNaviClient()
        val provider = PleosDestinationProvider({ client }, StandardTestDispatcher(testScheduler), 1000)
        val values = mutableListOf<Destination?>()
        val job = backgroundScope.launch { provider.observeDestinations().collect { values += it } }
        runCurrent()
        client.connected()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.NONE)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(job.isActive)
        assertEquals(0, client.releases)
        client.listener.onChanged()
        runCurrent()
        client.listener.onRouteState(RouteAvailability.ACTIVE)
        runCurrent()
        client.listener.onDestination(destinationInfo())
        runCurrent()
        assertEquals(listOf(null, "서울역"), values.map { it?.name })
        job.cancelAndJoin()
        assertEquals(1, client.releases)
    }

    @Test fun retryCreatesANewClientAfterTheFailedOneWasReleased() = runTest {
        val clients = mutableListOf<FakeNaviClient>()
        val provider = PleosDestinationProvider(
            { FakeNaviClient().also(clients::add) }, StandardTestDispatcher(testScheduler), 1000,
        )
        val first = async { runCatching { provider.currentDestination() } }
        runCurrent()
        clients[0].failed(IllegalStateException("first connection failed"))
        runCurrent()
        assertTrue(first.await().isFailure)
        assertEquals(1, clients[0].releases)
        val second = async { provider.currentDestination() }
        runCurrent()
        assertEquals(2, clients.size)
        clients[1].connected()
        runCurrent()
        clients[1].listener.onRouteState(RouteAvailability.NONE)
        runCurrent()
        assertNull(second.await())
        assertEquals(1, clients[1].releases)
    }
}
