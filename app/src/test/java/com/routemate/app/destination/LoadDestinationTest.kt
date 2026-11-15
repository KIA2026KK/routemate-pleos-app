package com.routemate.app.destination

import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.domain.DestinationProvider
import com.routemate.app.destination.infrastructure.MockDestinationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LoadDestinationTest {
    @Test fun existingSamProviderStillSupportsOneShotAndDefaultObservation() = runTest {
        val useCase = LoadDestination(DestinationProvider { null })
        assertNull(useCase())
        assertNull(useCase.observe().first())
    }

    @Test fun mockPreservesUtf8NameAndCoordinates() = runTest {
        val useCase = LoadDestination(MockDestinationProvider())
        val destination = useCase()
        assertEquals("광주송정역", destination?.name)
        assertEquals(35.1374, destination!!.latitude, 0.0)
        assertEquals(126.7916, destination.longitude, 0.0)
        assertEquals(destination, useCase.observe().first())
    }
}
