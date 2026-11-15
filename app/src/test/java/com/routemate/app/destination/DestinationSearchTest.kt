package com.routemate.app.destination

import androidx.lifecycle.ViewModelStore
import com.routemate.app.destination.application.SearchDestinations
import com.routemate.app.destination.domain.*
import com.routemate.app.destination.infrastructure.*
import com.routemate.app.destination.presentation.DestinationSearchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationSearchTest {
    private class Client : PlaceSearchClient {
        var calls = 0; var closed = 0
        lateinit var callback: (Result<List<DestinationPlace>>) -> Unit
        override fun search(query: String, callback: (Result<List<DestinationPlace>>) -> Unit): AutoCloseable {
            calls++; this.callback = callback; return AutoCloseable { closed++ }
        }
    }
    @Test fun exampleNameAndRoadAddressReturnSameDestinationWithoutNetwork() = runTest {
        val client = Client(); val search = GeocodedDestinationSearch(client)
        assertEquals(ExampleDestinations.songjeong, search.search("광주 송정역").single())
        assertEquals(ExampleDestinations.songjeong, search.search("상무대로 201").single())
        assertEquals(2, search.search("").size); assertEquals(0, client.calls)
    }
    @Test fun remoteResultsCompleteOnceAndReleaseHandle() = runTest {
        val client = Client(); val search = GeocodedDestinationSearch(client)
        val work = async { search.search("다른 도로명 110") }; runCurrent()
        client.callback(Result.success(listOf(ExampleDestinations.seoul)))
        client.callback(Result.success(emptyList()))
        assertEquals(listOf(ExampleDestinations.seoul), work.await()); assertEquals(1, client.closed)
    }
    @Test fun synchronousEmptyResultAlsoClosesHandle() = runTest {
        var closed = 0
        val search = GeocodedDestinationSearch(PlaceSearchClient { _, done -> done(Result.success(emptyList())); AutoCloseable { closed++ } })
        assertTrue(search.search("없는 주소 999").isEmpty()); assertEquals(1, closed)
    }
    @Test fun timeoutDisposesRequestAndIgnoresLateCallback() = runTest {
        val client = Client(); val search = GeocodedDestinationSearch(client, 800)
        val work = async { runCatching { search.search("긴 도로명 110") } }; runCurrent()
        advanceTimeBy(801); runCurrent()
        assertEquals(SearchFailure.TIMEOUT, (work.await().exceptionOrNull() as DestinationSearchException).reason)
        client.callback(Result.success(emptyList())); assertEquals(1, client.closed)
    }
    @Test fun cancellationDisposesPendingCallback() = runTest {
        val client = Client(); val work = launch { GeocodedDestinationSearch(client).search("취소할 도로 10") }
        runCurrent(); work.cancelAndJoin(); client.callback(Result.success(emptyList())); assertEquals(1, client.closed)
    }
    @Test fun platformUnavailabilityIsPreservedAndInvalidCoordinateIsRejected() = runTest {
        val client = PlaceSearchClient { _, done -> done(Result.failure(DestinationSearchException(SearchFailure.UNAVAILABLE))); AutoCloseable {} }
        val failure = runCatching { GeocodedDestinationSearch(client).search("새 도로명 1") }.exceptionOrNull()
        assertEquals(SearchFailure.UNAVAILABLE, (failure as DestinationSearchException).reason)
        assertNull(AndroidPlaceSearchClient.mapPlace("잘못된 위치", "도로명", 91.0, 127.0))
        assertEquals("도로명", AndroidPlaceSearchClient.mapPlace("시청", "도로명", 37.5, 127.0)?.address)
    }
    @Test fun lateCancelledSearchCannotOverwriteNewQueryAndDismissCancelsWork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val search = SearchDestinations(DestinationSearch { query ->
            if (query == "old") withContext(NonCancellable) { delay(1000) }
            listOf(if (query == "old") ExampleDestinations.seoul else ExampleDestinations.songjeong)
        })
        val vm = DestinationSearchViewModel(search, 0); val store = ViewModelStore().apply { put("search", vm) }
        try {
            vm.query("old"); runCurrent(); vm.query("new"); runCurrent()
            advanceTimeBy(1001); runCurrent()
            assertEquals("new", vm.state.value.query); assertEquals(ExampleDestinations.songjeong, vm.state.value.places.single())
            vm.query("old"); runCurrent(); vm.cancel(); advanceTimeBy(1001); runCurrent()
            assertTrue(vm.state.value.places.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun selectedMockDestinationRemainsObservableAndIsSaved() = runTest {
        var saved: Destination? = null
        val provider = MockDestinationProvider(save = { saved = it })
        provider.select(ExampleDestinations.seoul.destination)
        assertEquals(saved, provider.currentDestination()); assertEquals("서울역", saved?.name)
    }
}
