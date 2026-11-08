package com.routemate.app.auth.domain

import java.time.Instant

data class AccountLease(val vehicleId: String, val maskedVin: String, val expiresAt: Instant)
data class RecoveryKey(val value: String, val maskedVin: String, val expiresAt: Instant) {
    override fun toString() = "RecoveryKey[value=<redacted>]"
}
enum class AccountFailure { NETWORK, UNAUTHORIZED, RECOVERY_REJECTED, VEHICLE_UNAVAILABLE, SERVER, CONFIGURATION, MOCK_DISABLED }
class AccountAccessException(val reason: AccountFailure) : Exception(reason.name)

interface VehicleAccountAccess {
    suspend fun renew(): AccountLease
    suspend fun createRecoveryKey(): RecoveryKey
    suspend fun recover(key: String): AccountLease
}
