package com.routemate.app

import android.app.Application
import com.routemate.app.room.application.*
import com.routemate.app.room.domain.RoomAccess
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.trip.domain.TripRegistration
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.prototype.*
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.*

class RouteMateApplication : Application() {
    val graph by lazy { RouteMateGraph(this) }
}

class RouteMateGraph(private val application:Application) {
    private val runtime:CallRuntime = if(BuildConfig.PROTOTYPE_DEMO)CallRuntime.None else AndroidCallRuntime(application)
    var session=buildSession()
        private set
    private fun buildSession():RoomSession {
        val demo=if(BuildConfig.PROTOTYPE_DEMO)PrototypeSession() else null
        val identity=vehicleProvider(application)
        val registration:TripRegistration=demo?:HttpTripRegistration(BuildConfig.BACKEND_URL,
            {vehicleStore(application,it)},identity,allowLocalHttp=BuildConfig.DEBUG)
        val access:RoomAccess=demo?:HttpRoomAccess(BuildConfig.BACKEND_URL,
            {vehicleStore(application,it)},identity,allowLocalHttp=BuildConfig.DEBUG)
        val voice:VoiceCall=if(demo!=null)PrototypeVoiceCall() else LiveKitVoiceCall(application)
        return RoomSession(ManageRooms(access),voice,registration,runtime=runtime)
    }
    /** Instrumentation fixture isolation; never erases a VIN, preference or credential. */
    internal fun resetForTest(){check(BuildConfig.DEBUG);session.close();session=buildSession()}
}
