package com.routemate.app.destination.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Port implemented by navigation SDK adapters; null means no destination is set. */
fun interface DestinationProvider {
    suspend fun currentDestination(): Destination?

    /** A subscription owns its resources until the collector is cancelled. */
    fun observeDestinations(): Flow<Destination?> = flow { emit(currentDestination()) }
}
