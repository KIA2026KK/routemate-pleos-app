package com.routemate.app.vehicle.domain

import java.util.Locale

class Vin(raw: String) {
    val value = raw.trim().uppercase(Locale.ROOT)
    init { require(value.matches(Regex("[A-HJ-NPR-Z0-9]{17}")) && value != "0".repeat(17)) { "Invalid VIN" } }
    val masked get() = "*************" + value.takeLast(4)
    override fun equals(other: Any?) = other is Vin && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "Vin[$masked]"
}

enum class VehicleSource { PLEOS_SDK, MOCK }
enum class VinReadAccess { READ, READ_WRITE }
data class VehicleIdentity(val vin: Vin, val source: VehicleSource, val vinAccessType: VinReadAccess?)

fun interface VehicleIdentityProvider {
    suspend fun currentVehicle(): VehicleIdentity
}

enum class VehicleIdentityFailure { CONNECTION, PERMISSION, UNSUPPORTED, TIMEOUT, INVALID_DATA }
class VehicleIdentityException(val reason: VehicleIdentityFailure) : Exception(reason.name)
