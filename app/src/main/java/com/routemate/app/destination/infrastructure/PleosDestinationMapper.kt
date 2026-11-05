package com.routemate.app.destination.infrastructure

import ai.pleos.playground.navi.data.DestinationInfo
import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure

internal fun DestinationInfo.toDestination(): Destination {
    // Use final-destination coordinates, never current location or display coordinates.
    val destinationName = name.trim().ifEmpty { address.trim() }.ifEmpty { "목적지" }
    return try {
        Destination(destinationName, latitude, longitude)
    } catch (invalid: IllegalArgumentException) {
        throw DestinationAccessException(DestinationFailure.INVALID_DATA, invalid)
    }
}
