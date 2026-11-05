package com.routemate.app.destination.infrastructure

import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationInfo
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure
import org.junit.Assert.*
import org.junit.Test

internal fun destinationInfo(
    name: String = "서울역",
    latitude: Double = 37.5547,
    longitude: Double = 126.9706,
    address: String = "서울특별시 용산구",
) = DestinationInfo(
    latitude = latitude,
    longitude = longitude,
    displayLatitude = 35.0,
    displayLongitude = 129.0,
    name = name,
    address = address,
    remainDistance = 1000,
    remainTime = 120,
    tollFare = 0,
    poiId = "test-poi",
    poiSubId = null,
)

class PleosDestinationMapperTest {
    @Test fun usesFinalDestinationLatitudeAndLongitudeInSdk305Order() {
        val destination = destinationInfo().toDestination()
        assertEquals("서울역", destination.name)
        assertEquals(37.5547, destination.latitude, 0.0)
        assertEquals(126.9706, destination.longitude, 0.0)
    }

    @Test fun unnamedDestinationUsesAddressThenExplicitFallback() {
        assertEquals("서울특별시 용산구", destinationInfo(name = " ").toDestination().name)
        assertEquals("목적지", destinationInfo(name = "", address = "").toDestination().name)
    }

    @Test fun invalidSdkCoordinatesAreErrorsRatherThanNoDestination() {
        for ((lat, lon) in listOf(Double.NaN to 127.0, 91.0 to 0.0, 0.0 to 181.0, 0.0 to Double.POSITIVE_INFINITY)) {
            val failure = assertThrows(DestinationAccessException::class.java) {
                destinationInfo(latitude = lat, longitude = lon).toDestination()
            }
            assertEquals(DestinationFailure.INVALID_DATA, failure.reason)
        }
    }

    @Test fun zeroCoordinatesAreNotTreatedAsAnUndocumentedEmptySentinel() {
        assertEquals(0.0, destinationInfo(latitude = 0.0, longitude = 0.0).toDestination().latitude, 0.0)
    }

    @Test fun distinguishesNoRouteActiveRouteAndRouteCalculation() {
        assertEquals(RouteAvailability.NONE, routeAvailability(NaviMode.NOT_WORKING, false))
        assertEquals(RouteAvailability.NONE, routeAvailability(NaviMode.SAFE_GUIDING, false))
        assertEquals(RouteAvailability.ACTIVE, routeAvailability(NaviMode.ROUTE_GUIDING, false))
        assertEquals(RouteAvailability.ACTIVE, routeAvailability(NaviMode.SIMULATION_GUIDING, false))
        for (mode in NaviMode.entries) {
            assertEquals(RouteAvailability.CALCULATING, routeAvailability(mode, true))
        }
    }
}
