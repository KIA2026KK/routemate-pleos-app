package com.routemate.app.trip.infrastructure

import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.domain.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HttpTripRegistration(
    private val baseUrl: String,
    private val sessions: SessionStore,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val allowLocalHttp: Boolean = false,
) : TripRegistration {
    private val mutex = Mutex()
    private fun endpoint(path: String): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull() ?: throw TripRegistrationException(TripFailure.CONFIGURATION)
        val parts = url.host.split('.')
        val octets = parts.mapNotNull { it.toIntOrNull() }
        val local = url.host == "localhost" || url.host == "::1" || (parts.size == 4 && octets.size == 4 &&
            octets.all { it in 0..255 } && (octets[0] == 127 || octets[0] == 10 ||
                (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)))
        if ((!url.isHttps && !(allowLocalHttp && local)) || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.encodedPath != "/") {
            throw TripRegistrationException(TripFailure.CONFIGURATION)
        }
        return url.newBuilder().encodedPath(path).build()
    }

    override suspend fun register(destination: Destination): RegisteredTrip = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Validate before reading credentials or creating an anonymous user.
                val tripUrl = endpoint("/api/v1/trips")
                val token = sessions.load() ?: execute(Request.Builder().url(endpoint("/api/v1/auth/anonymous"))
                    .post(ByteArray(0).toRequestBody()).build()).getString("accessToken").also {
                        check(it.matches(Regex("[A-Za-z0-9_-]{43}")))
                        sessions.save(it)
                    }
                val body = JSONObject().put("destination", JSONObject().put("name", destination.name)
                    .put("latitude", destination.latitude).put("longitude", destination.longitude)).toString()
                val response = execute(Request.Builder().url(tripUrl).header("Authorization", "Bearer $token")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build())
                val saved = response.getJSONObject("destination")
                check(response.getString("status") == "ACTIVE")
                RegisteredTrip(response.getString("tripId"), Destination(saved.getString("name"),
                    saved.getDouble("latitude"), saved.getDouble("longitude")))
            } catch (e: CancellationException) { throw e }
            catch (e: TripRegistrationException) { throw e }
            catch (_: IOException) { throw TripRegistrationException(TripFailure.NETWORK) }
            catch (_: Exception) { throw TripRegistrationException(TripFailure.SERVER) }
        }
    }

    private suspend fun execute(request: Request): JSONObject {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val json = response.use {
                            if (!it.isSuccessful) throw TripRegistrationException(when (it.code) {
                                401 -> TripFailure.UNAUTHORIZED
                                400 -> TripFailure.INVALID_DESTINATION
                                else -> TripFailure.SERVER
                            })
                            JSONObject(it.body?.string() ?: throw IOException("Empty server response"))
                        }
                        if (continuation.isActive) continuation.resume(json)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
