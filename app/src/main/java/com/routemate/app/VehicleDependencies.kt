package com.routemate.app

import android.content.Context
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.trip.infrastructure.KeystoreSessionStore
import com.routemate.app.vehicle.domain.VehicleIdentity
import com.routemate.app.vehicle.domain.VehicleIdentityProvider
import com.routemate.app.vehicle.infrastructure.MockVehicleIdentityProvider
import com.routemate.app.vehicle.infrastructure.PleosVehicleIdentityProvider

internal fun vehicleProvider(context: Context): VehicleIdentityProvider =
    if (BuildConfig.USE_PLEOS_VEHICLE) PleosVehicleIdentityProvider(context.applicationContext)
    else MockVehicleIdentityProvider(context.applicationContext)

internal fun vehicleStore(context: Context, identity: VehicleIdentity) = KeystoreSessionStore(context.applicationContext,
    HttpTripRegistration.credentialScope(BuildConfig.BACKEND_URL, identity))
