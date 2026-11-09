package com.routemate.app.trip.domain

import com.routemate.app.destination.domain.Destination

data class RegisteredTrip(val id: String, val destination: Destination, val vehicleId: String = "", val maskedVin: String = "")

enum class TripFailure {
    NETWORK, SERVER, UNAUTHORIZED, INVALID_DESTINATION, CONFIGURATION,
    VEHICLE_UNAVAILABLE, VEHICLE_CONFLICT, MOCK_VEHICLE_DISABLED, TRIP_CHANGED,
}

class TripRegistrationException(val reason: TripFailure) : Exception(reason.name)

fun interface TripRegistration {
    suspend fun register(destination: Destination): RegisteredTrip
}
