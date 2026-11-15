package com.routemate.app.destination.infrastructure

import com.routemate.app.destination.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

fun interface PlaceSearchClient {
    fun search(query: String, callback: (Result<List<DestinationPlace>>) -> Unit): AutoCloseable
}

class GeocodedDestinationSearch(private val client: PlaceSearchClient, private val timeoutMs: Long = 8_000) : DestinationSearch {
    override suspend fun search(query: String): List<DestinationPlace> {
        val examples = ExampleDestinations.matching(query)
        if (examples.isNotEmpty() || query.isBlank()) return examples
        try {
            return withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val completed = AtomicBoolean(false)
                    val resource = AtomicReference<AutoCloseable?>(null)
                    val handle = client.search(query) { result ->
                        if (completed.compareAndSet(false, true)) {
                            resource.getAndSet(null)?.close()
                            result.fold(continuation::resume, continuation::resumeWithException)
                        }
                    }
                    resource.set(handle)
                    continuation.invokeOnCancellation { completed.set(true); resource.getAndSet(null)?.close() }
                    // A synchronous callback can complete before the handle is installed.
                    if (completed.get()) resource.getAndSet(null)?.close()
                }
            }
        } catch (e: TimeoutCancellationException) { throw DestinationSearchException(SearchFailure.TIMEOUT) }
        catch (e: CancellationException) { throw e }
        catch (e: DestinationSearchException) { throw e }
        catch (_: Exception) { throw DestinationSearchException(SearchFailure.NETWORK) }
    }
}
