package com.routemate.app.common.infrastructure

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class BackendConfigurationException : Exception("Invalid backend configuration")
internal class BackendHttpException(val status: Int, val path: String) : Exception("HTTP $status")

internal class BackendJsonClient(
    private val baseUrl: String,
    client: OkHttpClient,
    private val allowLocalHttp: Boolean,
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    companion object {
        fun client() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).build()
    }
    fun endpoint(path: String): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull() ?: throw BackendConfigurationException()
        val parts = url.host.split('.')
        val octets = parts.mapNotNull { it.toIntOrNull() }
        val local = url.host == "localhost" || url.host == "::1" || (parts.size == 4 && octets.size == 4 &&
            octets.all { it in 0..255 } && (octets[0] == 127 || octets[0] == 10 ||
                (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)))
        if ((!url.isHttps && !(allowLocalHttp && local)) || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.encodedPath != "/") throw BackendConfigurationException()
        return url.newBuilder().encodedPath(path).build()
    }
    suspend fun post(path: String, token: String? = null, body: JSONObject? = null): JSONObject {
        val request = Request.Builder().url(endpoint(path)).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.post(body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()) ?: ByteArray(0).toRequestBody()).build()
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
                            if (!it.isSuccessful) throw BackendHttpException(it.code, path)
                            JSONObject(it.body?.string() ?: throw IOException("Empty server response"))
                        }
                        if (continuation.isActive) continuation.resume(json)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}
