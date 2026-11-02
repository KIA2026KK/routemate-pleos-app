package com.routemate.app.destination

import com.routemate.app.destination.domain.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DestinationTest {
    @Test fun rejectsInvalidCoordinatesAndBlankNames() {
        for ((lat, lon) in listOf(90.1 to 0.0, 0.0 to -180.1, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Destination("invalid", lat, lon) }
        }
        assertThrows(IllegalArgumentException::class.java) { Destination(" ", 0.0, 0.0) }
    }

    @Test fun allowsCoordinateBoundaries() {
        assertEquals(90.0, Destination("North", 90.0, 180.0).latitude, 0.0)
        assertEquals(-180.0, Destination("South", -90.0, -180.0).longitude, 0.0)
    }
}
