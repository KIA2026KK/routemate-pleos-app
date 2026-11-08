package com.routemate.app.trip.application

import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.domain.*

class RegisterTrip(private val registration: TripRegistration) {
    suspend operator fun invoke(destination: Destination): RegisteredTrip {
        if (destination.name.trim().length > 120) throw TripRegistrationException(TripFailure.INVALID_DESTINATION)
        return registration.register(destination)
    }
}
