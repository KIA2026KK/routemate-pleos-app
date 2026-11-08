package com.routemate.app.trip.infrastructure

import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.domain.*
import com.routemate.app.vehicle.domain.*
import com.routemate.app.common.infrastructure.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class HttpTripRegistration(
    private val baseUrl: String,
    private val sessionStore: (VehicleIdentity) -> SessionStore,
    private val vehicleProvider: VehicleIdentityProvider,
    client: OkHttpClient = BackendJsonClient.client(),
    allowLocalHttp: Boolean = false,
) : TripRegistration {
    companion object {
        /** Canonical origin plus VIN/source prevents both cross-car reuse and URL spelling mismatches. */
        fun credentialScope(serverUrl: String, vehicle: VehicleIdentity): String {
            val origin = serverUrl.toHttpUrlOrNull() ?: throw TripRegistrationException(TripFailure.CONFIGURATION)
            return "${origin.scheme}://${origin.host}:${origin.port}|${vehicle.source.name}|${vehicle.vin.value}"
        }
    }
    private val http = BackendJsonClient(baseUrl, client, allowLocalHttp)
    private val mutex = Mutex()

    override suspend fun register(destination: Destination): RegisteredTrip = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Validate before reading credentials or creating an anonymous user.
                http.endpoint("/api/v1/trips")
                val identity = vehicleProvider.currentVehicle()
                val sessions = sessionStore(identity)
                val token = sessions.load() ?: http.post("/api/v1/auth/device-sessions").getString("accessToken").also {
                        check(it.matches(Regex("[A-Za-z0-9_-]{43}")))
                        sessions.save(it)
                    }
                val profile = JSONObject().put("vin", identity.vin.value).put("source", identity.source.name)
                    .put("vinAccessType", identity.vinAccessType?.name ?: JSONObject.NULL)
                val vehicle = http.post("/api/v1/vehicles/me", token, profile)
                check(vehicle.getString("source") == identity.source.name)
                check(vehicle.getString("maskedVin") == identity.vin.masked)
                val body = JSONObject().put("destination", JSONObject().put("name", destination.name)
                    .put("latitude", destination.latitude).put("longitude", destination.longitude))
                http.post("/api/v1/auth/device-sessions/renew", token)
                val response = http.post("/api/v1/trips", token, body)
                val saved = response.getJSONObject("destination")
                check(response.getString("status") == "ACTIVE")
                check(response.getString("vehicleId") == vehicle.getString("vehicleId"))
                RegisteredTrip(response.getString("tripId"), Destination(saved.getString("name"),
                    saved.getDouble("latitude"), saved.getDouble("longitude")),
                    vehicle.getString("vehicleId"), vehicle.getString("maskedVin"))
            } catch (e: CancellationException) { throw e }
            catch (e: TripRegistrationException) { throw e }
            catch (_: VehicleIdentityException) { throw TripRegistrationException(TripFailure.VEHICLE_UNAVAILABLE) }
            catch (_: BackendConfigurationException) { throw TripRegistrationException(TripFailure.CONFIGURATION) }
            catch (e: BackendHttpException) { throw TripRegistrationException(when (e.status) {
                401 -> TripFailure.UNAUTHORIZED
                403 -> TripFailure.MOCK_VEHICLE_DISABLED
                409 -> TripFailure.VEHICLE_CONFLICT
                428 -> TripFailure.VEHICLE_UNAVAILABLE
                400 -> if (e.path == "/api/v1/vehicles/me") TripFailure.VEHICLE_UNAVAILABLE else TripFailure.INVALID_DESTINATION
                else -> TripFailure.SERVER
            }) }
            catch (_: IOException) { throw TripRegistrationException(TripFailure.NETWORK) }
            catch (_: Exception) { throw TripRegistrationException(TripFailure.SERVER) }
        }
    }

}
