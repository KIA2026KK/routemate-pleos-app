package com.routemate.app.auth.infrastructure

import com.routemate.app.auth.domain.*
import com.routemate.app.common.infrastructure.*
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.vehicle.domain.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject

class HttpVehicleAccountAccess(
    baseUrl: String,
    private val stores: (VehicleIdentity) -> SessionStore,
    private val provider: VehicleIdentityProvider,
    client: OkHttpClient = BackendJsonClient.client(),
    allowLocalHttp: Boolean = false,
) : VehicleAccountAccess {
    private val http = BackendJsonClient(baseUrl, client, allowLocalHttp)
    override suspend fun renew() = access(false) { vehicle ->
        val token = stores(vehicle).load() ?: throw AccountAccessException(AccountFailure.UNAUTHORIZED)
        lease(http.post("/api/v1/auth/device-sessions/renew", token), vehicle)
    }
    override suspend fun createRecoveryKey() = access(false) { vehicle ->
        val token = stores(vehicle).load() ?: throw AccountAccessException(AccountFailure.UNAUTHORIZED)
        val response = http.post("/api/v1/auth/recovery-keys", token)
        check(response.getString("maskedVin") == vehicle.vin.masked)
        val key = response.getString("recoveryKey")
        check(key.matches(Regex("[A-Za-z0-9_-]{43}")))
        RecoveryKey(key, response.getString("maskedVin"), Instant.parse(response.getString("expiresAt")))
    }
    override suspend fun recover(key: String) = access(true) { vehicle ->
        if (!key.matches(Regex("[A-Za-z0-9_-]{43}"))) throw AccountAccessException(AccountFailure.RECOVERY_REJECTED)
        val response = http.post("/api/v1/auth/device-sessions/recover", body = JSONObject()
            .put("vin", vehicle.vin.value).put("source", vehicle.source.name).put("recoveryKey", key))
        val restored = lease(response, vehicle)
        val token = response.getString("accessToken")
        check(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        stores(vehicle).save(token)
        restored
    }
    private fun lease(response: JSONObject, vehicle: VehicleIdentity): AccountLease {
        check(response.getString("maskedVin") == vehicle.vin.masked)
        return AccountLease(response.getString("vehicleId"), response.getString("maskedVin"), Instant.parse(response.getString("expiresAt")))
    }
    private suspend fun <T> access(recovering: Boolean, action: suspend (VehicleIdentity) -> T): T = withContext(Dispatchers.IO) {
        try {
            http.endpoint("/")
            action(provider.currentVehicle())
        } catch (e: CancellationException) { throw e }
        catch (e: AccountAccessException) { throw e }
        catch (_: VehicleIdentityException) { throw AccountAccessException(AccountFailure.VEHICLE_UNAVAILABLE) }
        catch (_: BackendConfigurationException) { throw AccountAccessException(AccountFailure.CONFIGURATION) }
        catch (e: BackendHttpException) { throw AccountAccessException(when (e.status) {
            401 -> if (recovering) AccountFailure.RECOVERY_REJECTED else AccountFailure.UNAUTHORIZED
            403 -> AccountFailure.MOCK_DISABLED
            428 -> AccountFailure.VEHICLE_UNAVAILABLE
            else -> AccountFailure.SERVER
        }) }
        catch (_: IOException) { throw AccountAccessException(AccountFailure.NETWORK) }
        catch (_: Exception) { throw AccountAccessException(AccountFailure.SERVER) }
    }
}
