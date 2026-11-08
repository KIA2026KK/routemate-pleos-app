package com.routemate.app.vehicle

import com.routemate.app.vehicle.domain.*
import com.routemate.app.vehicle.infrastructure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosVehicleIdentityProviderTest {
    private val vin = "KNA00000000000001"
    private class Client : VehicleClient {
        var access: VinReadAccess? = VinReadAccess.READ
        var vin: String? = "KNA00000000000001"
        var reads = 0
        var released = 0
        var wait = false
        var failure: Exception? = null
        var callback: ((String?) -> Unit)? = null
        override fun initialize() { failure?.let { throw it } }
        override fun checkVinAccess(onComplete: (VinReadAccess?) -> Unit, onFailed: (Exception) -> Unit) = onComplete(access)
        override fun getVin(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit) {
            reads++
            callback = onComplete
            if (!wait) { onComplete(vin); onComplete(vin) }
        }
        override fun release() { released++ }
    }

    @Test fun readsConfirmedCapabilityAndIgnoresDuplicateCallbacks() = runTest {
        val client = Client()
        val provider = PleosVehicleIdentityProvider({client}, StandardTestDispatcher(testScheduler), 1000)
        val identity = provider.currentVehicle()
        assertEquals(vin, identity.vin.value)
        assertEquals(VehicleSource.PLEOS_SDK, identity.source)
        assertEquals(VinReadAccess.READ, identity.vinAccessType)
        assertEquals(1, client.released)
        assertFalse(identity.toString().contains(vin))
    }

    @Test fun unsupportedCapabilityDoesNotReadVin() = runTest {
        val client = Client().apply { access = null }
        val provider = PleosVehicleIdentityProvider({client}, StandardTestDispatcher(testScheduler), 1000)
        try { provider.currentVehicle(); fail("Expected unsupported VIN") }
        catch (e: VehicleIdentityException) { assertEquals(VehicleIdentityFailure.UNSUPPORTED, e.reason) }
        assertEquals(0, client.reads)
        assertEquals(1, client.released)
    }

    @Test fun timeoutAndCancellationReleaseClientAndIgnoreLateResponse() = runTest {
        val client = Client().apply { wait = true }
        val provider = PleosVehicleIdentityProvider({client}, StandardTestDispatcher(testScheduler), 100)
        try { provider.currentVehicle(); fail("Expected timeout") }
        catch (e: VehicleIdentityException) { assertEquals(VehicleIdentityFailure.TIMEOUT, e.reason) }
        assertEquals(1, client.released)
        val job = launch { provider.currentVehicle() }
        runCurrent()
        job.cancelAndJoin()
        client.callback?.invoke("KNA00000000000002")
        assertTrue(job.isCancelled)
        assertEquals(2, client.released)
    }

    @Test fun permissionFailureAndInvalidVinNeverFallBackToMock() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val denied = Client().apply { failure = SecurityException() }
        try { PleosVehicleIdentityProvider({denied}, dispatcher, 100).currentVehicle(); fail("Expected permission failure") }
        catch (e: VehicleIdentityException) { assertEquals(VehicleIdentityFailure.PERMISSION, e.reason) }
        for (value in listOf("UNKNOWN", null)) {
            val invalid = Client().apply { vin = value }
            try { PleosVehicleIdentityProvider({invalid}, dispatcher, 100).currentVehicle(); fail("Expected invalid VIN") }
            catch (e: VehicleIdentityException) { assertEquals(VehicleIdentityFailure.INVALID_DATA, e.reason) }
            assertEquals(1, invalid.released)
        }
        assertEquals(1, denied.released)
    }

    @Test fun canonicalVinValidationAndMasking() {
        assertEquals(Vin(vin), Vin(" ${vin.lowercase()} "))
        for (raw in listOf("SHORT", "00000000000000000", "KNA0000000000000I", "KNA0000000000000O", "KNA0000000000000Q")) {
            try { Vin(raw); fail("Expected invalid VIN") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("*************0001", Vin(vin).masked)
    }
}
