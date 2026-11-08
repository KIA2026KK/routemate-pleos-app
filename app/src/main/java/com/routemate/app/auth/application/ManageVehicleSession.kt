package com.routemate.app.auth.application

import com.routemate.app.auth.domain.*

class ManageVehicleSession(private val accounts: VehicleAccountAccess) {
    suspend fun renew() = accounts.renew()
    suspend fun createRecoveryKey() = accounts.createRecoveryKey()
    suspend fun recover(key: String): AccountLease {
        if (!key.matches(Regex("[A-Za-z0-9_-]{43}"))) throw AccountAccessException(AccountFailure.RECOVERY_REJECTED)
        return accounts.recover(key)
    }
}
