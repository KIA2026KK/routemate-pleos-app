package com.routemate.app.destination.domain

/** Port implemented by navigation SDK adapters; null means no destination is set. */
fun interface DestinationProvider {
    suspend fun currentDestination(): Destination?
}
