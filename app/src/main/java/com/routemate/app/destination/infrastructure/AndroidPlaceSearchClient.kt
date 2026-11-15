package com.routemate.app.destination.infrastructure

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.routemate.app.destination.domain.*
import java.util.Locale
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Platform geocoding: no embedded map-service credential and no location permission required. */
class AndroidPlaceSearchClient(context: Context) : PlaceSearchClient {
    private val geocoder = Geocoder(context.applicationContext, Locale.KOREA)
    override fun search(query: String, callback: (Result<List<DestinationPlace>>) -> Unit): AutoCloseable {
        val cancelled = AtomicBoolean(false)
        fun deliver(value: Result<List<DestinationPlace>>) { if (!cancelled.get()) callback(value) }
        if (!Geocoder.isPresent()) {
            deliver(Result.failure(DestinationSearchException(SearchFailure.UNAVAILABLE)))
            return AutoCloseable { cancelled.set(true) }
        }
        fun places(addresses: List<Address>) = addresses.mapNotNull { address ->
            if (!address.hasLatitude() || !address.hasLongitude()) null else {
                val road = address.getAddressLine(0).orEmpty().removePrefix("대한민국 ")
                mapPlace(address.featureName?.takeIf { value -> value.any(Char::isLetter) } ?: query, road, address.latitude, address.longitude)
            }
        }.distinctBy { it.destination }.take(5)
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) { deliver(Result.success(places(addresses))) }
                override fun onError(errorMessage: String?) { deliver(Result.failure(DestinationSearchException(SearchFailure.NETWORK))) }
            })
            return AutoCloseable { cancelled.set(true) }
        }
        // Bound legacy blocking Binder requests and their queue. Cancellation never blocks the caller.
        val task = FutureTask<Unit> {
            if (!cancelled.get()) {
                @Suppress("DEPRECATION")
                val result = runCatching { places(geocoder.getFromLocationName(query, 5).orEmpty()) }
                deliver(result)
            }
        }
        try { legacy.execute(task) } catch (_: RejectedExecutionException) {
            deliver(Result.failure(DestinationSearchException(SearchFailure.NETWORK)))
        }
        return AutoCloseable { cancelled.set(true); task.cancel(true); legacy.remove(task) }
    }
    companion object {
        private val legacy = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1),
            ThreadFactory { task -> Thread(task, "destination-geocoder").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
        internal fun mapPlace(name: String, address: String, latitude: Double, longitude: Double): DestinationPlace? =
            runCatching { DestinationPlace(Destination(name.trim().take(120), latitude, longitude), address) }.getOrNull()
    }
}
