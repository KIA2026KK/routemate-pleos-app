package com.routemate.app

import android.location.Geocoder
import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.destination.infrastructure.AndroidPlaceSearchClient
import com.routemate.app.destination.infrastructure.GeocodedDestinationSearch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DestinationSearchDeviceTest {
    @Test fun deviceGeocoderResolvesStreetOutsideExampleCatalog() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("geocoderE2E") == "true")
        assumeTrue("Device has no geocoding service", Geocoder.isPresent())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val places = GeocodedDestinationSearch(AndroidPlaceSearchClient(context)).search("서울특별시 중구 세종대로 110")
        assertTrue("Geocoder returned no street result", places.isNotEmpty())
        assertTrue(places.any { !it.example && it.address.isNotBlank() && it.destination.latitude in 37.4..37.7 && it.destination.longitude in 126.8..127.1 })
    }
}
