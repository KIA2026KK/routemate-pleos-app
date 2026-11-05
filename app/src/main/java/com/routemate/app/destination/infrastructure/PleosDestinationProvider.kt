package com.routemate.app.destination.infrastructure

import ai.pleos.playground.navi.data.DestinationInfo
import android.content.Context
import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure
import com.routemate.app.destination.domain.DestinationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class PleosDestinationProvider internal constructor(
    private val clientFactory: () -> NaviClient,
    private val dispatcher: CoroutineDispatcher,
    private val timeoutMillis: Long,
) : DestinationProvider {
    constructor(context: Context) : this(
        clientFactory = naviClientFactory(context),
        dispatcher = Dispatchers.Main.immediate,
        timeoutMillis = 10_000,
    )

    init { require(timeoutMillis > 0) }

    override suspend fun currentDestination(): Destination? = observeDestinations().first()

    override fun observeDestinations(): Flow<Destination?> = callbackFlow {
        val client = try {
            clientFactory()
        } catch (failure: Exception) {
            throw failure.asAccessFailure(DestinationFailure.CONNECTION)
        }
        var connected = false
        var listenerRegistered = false
        var closed = false
        var querying = false
        var destinationRequested = false
        var refreshQueued = false
        var deadline: Job? = null

        fun startDeadline() {
            deadline?.cancel()
            deadline = launch {
                delay(timeoutMillis)
                close(DestinationAccessException(DestinationFailure.TIMEOUT))
            }
        }

        // SDK callbacks may run on Binder threads. Confine state and SDK calls to dispatcher.
        fun dispatch(action: () -> Unit) {
            launch {
                if (!closed) {
                    try {
                        action()
                    } catch (failure: Exception) {
                        close(failure.asAccessFailure())
                    }
                }
            }
        }

        fun beginQuery() {
            querying = true
            destinationRequested = false
            startDeadline()
            client.getRouteStateInfo()
        }

        fun completeQuery(destination: Destination?) {
            querying = false
            destinationRequested = false
            deadline?.cancel()
            if (refreshQueued) {
                // A change invalidates an in-flight snapshot. Finish it before querying again.
                refreshQueued = false
                beginQuery()
            } else {
                trySend(destination)
            }
        }

        val listener = object : NaviClient.Listener {
            override fun onDestination(info: DestinationInfo) = dispatch {
                if (querying && destinationRequested) completeQuery(info.toDestination())
            }

            override fun onRouteState(state: RouteAvailability) = dispatch {
                if (querying) {
                    when (state) {
                        RouteAvailability.NONE -> completeQuery(null)
                        RouteAvailability.CALCULATING -> Unit // Wait for change/completion; deadline remains active.
                        RouteAvailability.ACTIVE -> if (!destinationRequested) {
                            destinationRequested = true
                            client.getDestinationInfo()
                        }
                    }
                }
            }

            override fun onChanged() = dispatch {
                if (querying) {
                    refreshQueued = true
                    if (!destinationRequested) client.getRouteStateInfo()
                } else {
                    beginQuery()
                }
            }

            override fun onRouteCancelled() = dispatch {
                refreshQueued = false
                completeQuery(null)
            }

            override fun onFailure(cause: Throwable) = dispatch {
                close(cause.asAccessFailure())
            }
        }

        startDeadline()
        try {
            client.initialize(
                onConnected = {
                    dispatch {
                        if (!connected) {
                            connected = true
                            listenerRegistered = true
                            client.addListener(listener)
                            beginQuery()
                        }
                    }
                },
                onFailed = { failure -> dispatch { close(failure.asAccessFailure(DestinationFailure.CONNECTION)) } },
            )
        } catch (failure: Exception) {
            close(failure.asAccessFailure(DestinationFailure.CONNECTION))
        }

        awaitClose {
            closed = true
            deadline?.cancel()
            // Always release, including partial initialization and listener-registration failure.
            if (listenerRegistered) runCatching { client.removeListener(listener) }
            runCatching { client.release() }
        }
    }.flowOn(dispatcher).buffer(Channel.CONFLATED).distinctUntilChanged()
}

private fun naviClientFactory(context: Context): () -> NaviClient {
    val applicationContext = context.applicationContext
    return { AndroidNaviClient(applicationContext) }
}

private fun Throwable.asAccessFailure(
    fallback: DestinationFailure = DestinationFailure.UNKNOWN,
): DestinationAccessException = when (this) {
    is DestinationAccessException -> this
    is SecurityException -> DestinationAccessException(DestinationFailure.PERMISSION, this)
    else -> DestinationAccessException(fallback, this)
}
