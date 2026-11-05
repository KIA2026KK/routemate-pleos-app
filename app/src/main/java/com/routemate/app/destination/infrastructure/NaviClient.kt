package com.routemate.app.destination.infrastructure

import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/** App-owned seam around the verified NaviHelper 3.0.5 API, not a vendor API. */
internal interface NaviClient {
    interface Listener {
        fun onDestination(info: DestinationInfo)
        fun onRouteState(state: RouteAvailability)
        fun onChanged()
        fun onRouteCancelled()
        fun onFailure(cause: Throwable)
    }

    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
    fun getRouteStateInfo()
    fun getDestinationInfo()
    fun release()
}

internal enum class RouteAvailability { NONE, CALCULATING, ACTIVE }

internal fun RouteStateInfo.availability(): RouteAvailability = routeAvailability(naviMode, isRouting)

internal fun routeAvailability(mode: NaviMode, isRouting: Boolean): RouteAvailability = when {
    isRouting -> RouteAvailability.CALCULATING
    mode == NaviMode.ROUTE_GUIDING || mode == NaviMode.SIMULATION_GUIDING -> RouteAvailability.ACTIVE
    else -> RouteAvailability.NONE
}

internal class AndroidNaviClient(context: Context) : NaviClient {
    private val helper = NaviHelper(context.applicationContext)
    private val listeners = mutableMapOf<NaviClient.Listener, NaviHelperEventListener>()

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) =
        helper.initialize(onConnected, onFailed)

    override fun addListener(listener: NaviClient.Listener) {
        check(listener !in listeners)
        val sdkListener = object : NaviHelperEventListener {
            override fun onDestinationInfo(destinationInfo: DestinationInfo) = listener.onDestination(destinationInfo)
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = listener.onRouteState(routeStateInfo.availability())
            override fun onDestinationChanged() = listener.onChanged()
            override fun onRoutingCompleted() = listener.onChanged()
            override fun onRouteCancelled() = listener.onRouteCancelled()
            override fun onError(errorCode: NaviErrorCode) =
                listener.onFailure(IllegalStateException("Navigation request failed: ${errorCode.name}"))
        }
        listeners[listener] = sdkListener
        helper.addListener(sdkListener)
    }

    override fun removeListener(listener: NaviClient.Listener) {
        listeners.remove(listener)?.let(helper::removeListener)
    }

    override fun getRouteStateInfo() = helper.getRouteStateInfo()
    override fun getDestinationInfo() = helper.getDestinationInfo()
    override fun release() = helper.release()
}
