package com.routemate.app.destination.application

import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationProvider

class LoadDestination(private val provider: DestinationProvider) {
    suspend operator fun invoke(): Destination? = provider.currentDestination()
}
