package com.routemate.app.destination.infrastructure

import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationProvider

class MockDestinationProvider : DestinationProvider {
    override suspend fun currentDestination() = Destination("서울역 (샘플)", 37.5547, 126.9706)
}
