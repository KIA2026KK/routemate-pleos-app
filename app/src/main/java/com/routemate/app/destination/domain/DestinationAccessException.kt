package com.routemate.app.destination.domain

enum class DestinationFailure {
    CONNECTION, TIMEOUT, PERMISSION, INVALID_DATA, UNKNOWN
}

class DestinationAccessException(
    val reason: DestinationFailure,
    cause: Throwable? = null,
) : Exception("Destination access failed: $reason", cause)
