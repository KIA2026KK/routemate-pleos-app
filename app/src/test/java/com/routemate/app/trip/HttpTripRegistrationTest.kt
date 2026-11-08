package com.routemate.app.trip

import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.domain.*
import com.routemate.app.trip.infrastructure.*
import com.routemate.app.vehicle.domain.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HttpTripRegistrationTest {
    private fun server() = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    private val destination = Destination("서울역", 37.5547, 126.9706)
    private val token = "a".repeat(43)
    private class Store(var token: String? = null) : SessionStore {
        override fun load() = token
        override fun save(token: String) { this.token = token }
    }
    private val identity = VehicleIdentity(Vin("KNA00000000000001"), VehicleSource.PLEOS_SDK, VinReadAccess.READ)
    private val provider = VehicleIdentityProvider { identity }
    private fun vehicle() = MockResponse().setBody("""{"vehicleId":"vehicle-a","maskedVin":"*************0001","source":"PLEOS_SDK","ownershipVerified":false}""")
    private fun success() = MockResponse().setBody("""{"tripId":"trip-a","vehicleId":"vehicle-a","status":"ACTIVE","destination":{"name":"서울역","latitude":37.5547,"longitude":126.9706}}""")
    private fun registration(url: String, store: Store, client: OkHttpClient? = null) =
        if (client == null) HttpTripRegistration(url, {store}, provider, allowLocalHttp = true)
        else HttpTripRegistration(url, {store}, provider, client, true)
    private fun MockWebServer.localUrl() = url("/").newBuilder().host("127.0.0.1").build().toString()

    @Test fun createsSessionOnceAndSendsAuthenticatedUtf8Destination() = runBlocking {
        server().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"accessToken":"$token"}"""))
            server.enqueue(vehicle()); server.enqueue(success()); server.enqueue(vehicle()); server.enqueue(success())
            val store = Store()
            val registration = registration(server.localUrl(), store)
            assertEquals(destination, registration.register(destination).destination)
            registration.register(destination)
            assertEquals("/api/v1/auth/device-sessions", server.takeRequest().path)
            val binding = server.takeRequest()
            assertEquals("/api/v1/vehicles/me", binding.path)
            assertEquals(identity.vin.value, JSONObject(binding.body.readUtf8()).getString("vin"))
            val request = server.takeRequest()
            assertEquals("/api/v1/trips", request.path)
            assertEquals("Bearer $token", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8()).getJSONObject("destination")
            assertEquals("서울역", body.getString("name"))
            assertEquals(126.9706, body.getDouble("longitude"), 0.0)
            assertEquals(token, store.token)
            assertEquals("/api/v1/vehicles/me", server.takeRequest().path)
            assertEquals("/api/v1/trips", server.takeRequest().path)
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun unauthorizedSessionIsNotSilentlyReplacedWithAnotherUser() = runBlocking {
        server().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            val store = Store(token)
            assertFailure(TripFailure.UNAUTHORIZED) {
                registration(server.localUrl(), store).register(destination)
            }
            assertEquals(token, store.token)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun invalidInputServerErrorsAndBrokenResponsesHaveTypedErrors() = runBlocking {
        for ((response, reason) in listOf(
            MockResponse().setResponseCode(400) to TripFailure.INVALID_DESTINATION,
            MockResponse().setResponseCode(503) to TripFailure.SERVER,
            MockResponse().setBody("broken-json") to TripFailure.SERVER,
        )) server().use { server ->
            server.enqueue(vehicle())
            server.enqueue(response)
            assertFailure(reason) {
                registration(server.localUrl(), Store(token)).register(destination)
            }
        }
    }

    @Test fun insecureOrCredentialBearingConfigurationNeverSendsRequests() = runBlocking {
        for (url in listOf("", "http://example.com", "http://10.x.0.2.2", "https://user:secret@example.com", "https://example.com?token=x")) {
            val store = Store()
            assertFailure(TripFailure.CONFIGURATION) { registration(url, store).register(destination) }
            assertNull(store.token)
        }
    }

    @Test fun redirectsDoNotForwardCredentials() = runBlocking {
        server().use { destinationServer -> server().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destinationServer.url("/")))
            assertFailure(TripFailure.SERVER) {
                registration(server.localUrl(), Store(token)).register(destination)
            }
            assertEquals(0, destinationServer.requestCount)
        } }
    }

    @Test fun timeoutAndCoroutineCancellationStopNetworkCalls() = runBlocking {
        server().use { server ->
            val client = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertFailure(TripFailure.NETWORK) {
                registration(server.localUrl(), Store(token), client).register(destination)
            }
            server.takeRequest(5, TimeUnit.SECONDS)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(Dispatchers.IO) {
                registration(server.localUrl(), Store(token),
                    client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()).register(destination)
            }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            withTimeout(2_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
        }
    }

    @Test fun vinReadFailureNeverCreatesAnonymousFallbackAccount() = runBlocking {
        server().use { server ->
            val store = Store()
            val denied = VehicleIdentityProvider { throw VehicleIdentityException(VehicleIdentityFailure.PERMISSION) }
            assertFailure(TripFailure.VEHICLE_UNAVAILABLE) {
                HttpTripRegistration(server.localUrl(), {store}, denied, allowLocalHttp = true).register(destination)
            }
            assertNull(store.token)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun vinConflictAndMockRejectionNeverIssueAnotherCredential() = runBlocking {
        for ((code, reason) in listOf(409 to TripFailure.VEHICLE_CONFLICT, 403 to TripFailure.MOCK_VEHICLE_DISABLED)) {
            server().use { server ->
                val store = Store(token)
                server.enqueue(MockResponse().setResponseCode(code))
                assertFailure(reason) { registration(server.localUrl(), store).register(destination) }
                assertEquals(1, server.requestCount)
                assertEquals(token, store.token)
            }
        }
    }

    private suspend fun assertFailure(reason: TripFailure, operation: suspend () -> Unit) {
        try { operation(); fail("Expected $reason") }
        catch (e: TripRegistrationException) { assertEquals(reason, e.reason) }
    }

    @Test fun credentialScopeIsStableForOriginAndIsolatedByVinAndSource() {
        val scope = HttpTripRegistration.credentialScope("https://EXAMPLE.com:443/", identity)
        assertEquals(scope, HttpTripRegistration.credentialScope("https://example.com", identity))
        assertNotEquals(scope, HttpTripRegistration.credentialScope("https://example.com",
            identity.copy(vin = Vin("KNA00000000000002"))))
        assertNotEquals(scope, HttpTripRegistration.credentialScope("https://example.com",
            identity.copy(source = VehicleSource.MOCK, vinAccessType = null)))
        assertNotEquals(scope, HttpTripRegistration.credentialScope("https://another.example", identity))
    }
}
