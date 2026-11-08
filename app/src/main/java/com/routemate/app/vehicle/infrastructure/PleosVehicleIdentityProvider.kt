package com.routemate.app.vehicle.infrastructure

import android.content.Context
import com.routemate.app.vehicle.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

class PleosVehicleIdentityProvider internal constructor(
    private val createClient: () -> VehicleClient,
    private val dispatcher: CoroutineDispatcher,
    private val timeoutMillis: Long,
) : VehicleIdentityProvider {
    constructor(context: Context) : this({ AndroidVehicleClient(context.applicationContext) }, Dispatchers.Main.immediate, 10_000)

    override suspend fun currentVehicle(): VehicleIdentity = withContext(dispatcher) {
        var client: VehicleClient? = null
        try {
            client = createClient()
            val current = client
            withTimeout(timeoutMillis) {
                current.initialize()
                val access = awaitCallback<VinReadAccess?> { success, failure -> current.checkVinAccess(success, failure) }
                    ?: throw VehicleIdentityException(VehicleIdentityFailure.UNSUPPORTED)
                val raw = awaitCallback<String?> { success, failure -> current.getVin(success, failure) }
                    ?: throw VehicleIdentityException(VehicleIdentityFailure.INVALID_DATA)
                val vin = try { Vin(raw) } catch (_: IllegalArgumentException) {
                    throw VehicleIdentityException(VehicleIdentityFailure.INVALID_DATA)
                }
                VehicleIdentity(vin, VehicleSource.PLEOS_SDK, access)
            }
        } catch (_: TimeoutCancellationException) { throw VehicleIdentityException(VehicleIdentityFailure.TIMEOUT) }
        catch (e: CancellationException) { throw e }
        catch (e: VehicleIdentityException) { throw e }
        catch (_: SecurityException) { throw VehicleIdentityException(VehicleIdentityFailure.PERMISSION) }
        catch (_: Exception) { throw VehicleIdentityException(VehicleIdentityFailure.CONNECTION) }
        finally { client?.let { runCatching { it.release() } } }
    }

    private suspend fun <T> awaitCallback(start: ((T) -> Unit, (Exception) -> Unit) -> Unit): T =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            continuation.invokeOnCancellation { finished.set(true) }
            start({ value ->
                if (finished.compareAndSet(false, true) && continuation.isActive) continuation.resume(value)
            }, { error ->
                if (finished.compareAndSet(false, true) && continuation.isActive) continuation.resumeWithException(error)
            })
        }
}
