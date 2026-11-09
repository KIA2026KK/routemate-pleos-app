package com.routemate.app.vehicle.infrastructure

import android.content.Context
import com.routemate.app.vehicle.domain.*
import java.util.UUID

/** Synthetic, installation-specific VIN, isolated from real VIN accounts on the server. */
class MockVehicleIdentityProvider internal constructor(private val store: MockVinStore) : VehicleIdentityProvider {
    constructor(context: Context) : this(PreferencesMockVinStore(context.applicationContext))
    companion object { private val creationLock=Any() }
    override suspend fun currentVehicle(): VehicleIdentity = synchronized(creationLock) {
        // Several adapters can query the same installation on their first IO request.
        val value = store.load() ?: ("KNA" + UUID.randomUUID().toString().replace("-", "").take(14)).also {
            if(!store.save(it))throw VehicleIdentityException(VehicleIdentityFailure.CONNECTION)
        }
        val vin=try {Vin(value)} catch(_: IllegalArgumentException) {throw VehicleIdentityException(VehicleIdentityFailure.INVALID_DATA)}
        VehicleIdentity(vin, VehicleSource.MOCK, null)
    }
}

internal interface MockVinStore {
    fun load(): String?
    fun save(vin: String): Boolean
}

private class PreferencesMockVinStore(context: Context) : MockVinStore {
    private val preferences=context.getSharedPreferences("routemate_mock_vehicle",Context.MODE_PRIVATE)
    override fun load()=preferences.getString("vin",null)
    override fun save(vin: String)=preferences.edit().putString("vin",vin).commit()
}
