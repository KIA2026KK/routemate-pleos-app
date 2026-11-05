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
        assertEquals("서울역 (샘플)", destination?.name)
        assertEquals(37.5547, destination!!.latitude, 0.0)
        assertEquals(126.9706, destination.longitude, 0.0)
        assertEquals(destination, useCase.observe().first())
    }
}
