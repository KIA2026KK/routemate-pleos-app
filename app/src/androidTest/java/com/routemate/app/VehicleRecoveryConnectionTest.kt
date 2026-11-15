package com.routemate.app

import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.auth.domain.*
import com.routemate.app.auth.infrastructure.HttpVehicleAccountAccess
import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.vehicle.domain.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class VehicleRecoveryConnectionTest {
    @Test fun reinstallRecoveryPreservesVehicleAndTripAndRevokesOldSession() = runBlocking {
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank() &&
            InstrumentationRegistry.getArguments().getString("tripE2E") == "true")
        val identity = VehicleIdentity(Vin("KNA" + UUID.randomUUID().toString().replace("-", "").take(14)), VehicleSource.MOCK, null)
        val provider = VehicleIdentityProvider { identity }
        val old = MemoryStore()
        val restored = MemoryStore()
        try {
        val initial = HttpTripRegistration(BuildConfig.BACKEND_URL, { old }, provider, allowLocalHttp = true)
            .register(Destination("서울역", 37.5547, 126.9706))
        val previous = HttpVehicleAccountAccess(BuildConfig.BACKEND_URL, { old }, provider, allowLocalHttp = true)
        val key = previous.createRecoveryKey()
        val current = HttpVehicleAccountAccess(BuildConfig.BACKEND_URL, { restored }, provider, allowLocalHttp = true)
        val recovered = current.recover(key.value)
        assertEquals(initial.vehicleId, recovered.vehicleId)
        assertNotNull(restored.load())
        assertFalse(old.load() == restored.load())
        try {
            previous.renew()
            fail("Recovery must revoke the old session")
        } catch (e: AccountAccessException) {
            assertEquals(AccountFailure.UNAUTHORIZED, e.reason)
        }
        val updated = HttpTripRegistration(BuildConfig.BACKEND_URL, { restored }, provider, allowLocalHttp = true)
            .register(Destination("부산역", 35.115, 129.042))
        assertEquals(initial.id, updated.id)
        assertEquals(initial.vehicleId, updated.vehicleId)
        assertEquals("부산역", updated.destination.name)
        // A lost recovery response can be retried with the same unexpired key.
        assertEquals(initial.vehicleId, current.recover(key.value).vehicleId)
        } finally {
            val latest=if(restored.load()!=null)restored else old
            runCatching { com.routemate.app.room.infrastructure.HttpRoomAccess(BuildConfig.BACKEND_URL,
                {latest},provider,allowLocalHttp=true).endTrip() }
        }
    }

    private class MemoryStore : SessionStore {
        private var token: String? = null
        override fun load(): String? = token
        override fun save(token: String) { this.token = token }
    }
}
