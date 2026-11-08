package com.routemate.app

import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.trip.infrastructure.KeystoreSessionStore
import com.routemate.app.vehicle.domain.*
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TripBackendConnectionTest {
    @Test fun encryptedCredentialSurvivesReloadAndDestinationUpdateKeepsTripId() = runBlocking {
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank() &&
            InstrumentationRegistry.getArguments().getString("tripE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "trip-e2e-${UUID.randomUUID()}"
        val identity = VehicleIdentity(Vin("KNA" + UUID.randomUUID().toString().replace("-", "").take(14)), VehicleSource.MOCK, null)
        val provider = VehicleIdentityProvider { identity }
        val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
        try {
            val store = KeystoreSessionStore(context, scope)
            val first = HttpTripRegistration(BuildConfig.BACKEND_URL, {store}, provider, allowLocalHttp = true)
                .register(Destination("서울역", 37.5547, 126.9706))
            val token = store.load()
            assertNotNull(token)
            val encrypted = context.getSharedPreferences("routemate_session_$hash", 0).getString("credential", null)
            assertTrue(encrypted != token && encrypted?.contains(':') == true)
            val reloadedStore = KeystoreSessionStore(context, scope)
            assertTrue(token == reloadedStore.load())
            val reloaded = HttpTripRegistration(BuildConfig.BACKEND_URL, {reloadedStore}, provider, allowLocalHttp = true)
            val retry = reloaded.register(Destination("서울역", 37.5547, 126.9706))
            val changed = reloaded.register(Destination("부산역", 35.115, 129.042))
            assertEquals(first.id, retry.id)
            assertEquals(first.id, changed.id)
            assertEquals(first.vehicleId, changed.vehicleId)
            assertEquals(identity.vin.masked, changed.maskedVin)
            assertEquals("부산역", changed.destination.name)
            assertEquals(129.042, changed.destination.longitude, 0.0)
        } finally {
            context.deleteSharedPreferences("routemate_session_$hash")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("routemate_session_key_$hash") }
        }
    }
}
