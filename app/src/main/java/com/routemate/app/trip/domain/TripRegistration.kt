package com.routemate.app.trip.domain

import com.routemate.app.destination.domain.Destination

data class RegisteredTrip(val id: String, val destination: Destination)

enum class TripFailure { NETWORK, SERVER, UNAUTHORIZED, INVALID_DESTINATION, CONFIGURATION }

class TripRegistrationException(val reason: TripFailure) : Exception(reason.name)

fun interface TripRegistration {
    suspend fun register(destination: Destination): RegisteredTrip
}
