package com.routemate.app.vehicle.infrastructure

import android.content.Context
import com.routemate.app.vehicle.domain.*
import java.util.UUID

/** Synthetic, installation-specific VIN, isolated from real VIN accounts on the server. */
class MockVehicleIdentityProvider(context: Context) : VehicleIdentityProvider {
    private val preferences = context.getSharedPreferences("routemate_mock_vehicle", Context.MODE_PRIVATE)
    override suspend fun currentVehicle(): VehicleIdentity {
        val value = preferences.getString("vin", null) ?: ("KNA" + UUID.randomUUID().toString().replace("-", "").take(14)).also {
            check(preferences.edit().putString("vin", it).commit())
        }
        return VehicleIdentity(Vin(value), VehicleSource.MOCK, null)
    }
}
