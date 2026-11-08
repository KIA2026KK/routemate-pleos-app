package com.routemate.app.auth

import androidx.lifecycle.ViewModelStore
import com.routemate.app.auth.application.ManageVehicleSession
import com.routemate.app.auth.domain.*
import com.routemate.app.auth.presentation.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleAccountViewModelTest {
    private val lease=AccountLease("vehicle-a","*************0001",Instant.parse("2027-01-01T00:00:00Z"))
    private class Access : VehicleAccountAccess {
        var calls=0
        var operation: suspend () -> AccountLease = { error("Not configured") }
        override suspend fun renew(): AccountLease { calls++;return operation() }
        override suspend fun createRecoveryKey(): RecoveryKey { calls++; val result=operation();return RecoveryKey("a".repeat(43),result.maskedVin,result.expiresAt) }
        override suspend fun recover(key: String): AccountLease { calls++;return operation() }
    }
    @Test fun duplicateOperationsAreBlockedAndDisplayedKeyIsCleared()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val response=CompletableDeferred<AccountLease>();val access=Access().apply{operation={response.await()}}
            val vm=VehicleAccountViewModel(ManageVehicleSession(access));store.put("account",vm)
            vm.issueKey();vm.issueKey();runCurrent();assertEquals(1,access.calls)
            response.complete(lease);runCurrent()
            assertTrue(vm.state.value is AccountState.KeyIssued)
            assertFalse(vm.state.value.toString().contains("a".repeat(43)))
            vm.updateInput("secret");vm.forgetSecrets();assertEquals("",vm.input.value)
            assertEquals(AccountState.Idle,vm.state.value)
        } finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun invalidRecoveryInputDoesNotCallPortAndFailureCanBeRetried()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val access=Access().apply{operation={throw AccountAccessException(AccountFailure.NETWORK)}}
            val vm=VehicleAccountViewModel(ManageVehicleSession(access));store.put("account",vm)
            vm.updateInput("invalid");vm.recover();runCurrent();assertEquals(0,access.calls)
            assertEquals(AccountFailure.RECOVERY_REJECTED,(vm.state.value as AccountState.Error).reason)
            vm.updateInput("a".repeat(43));vm.recover();runCurrent();assertEquals("",vm.input.value)
            assertEquals(AccountFailure.NETWORK,(vm.state.value as AccountState.Error).reason)
            access.operation={lease};vm.updateInput("a".repeat(43));vm.recover();runCurrent()
            assertEquals(AccountState.Ready(lease,true),vm.state.value)
        } finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun leavingScreenCancelsPendingKeyIssuance()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            var cancelled=false
            val access=Access().apply{operation={try{awaitCancellation()}finally{cancelled=true}}}
            val vm=VehicleAccountViewModel(ManageVehicleSession(access));store.put("account",vm)
            vm.issueKey();runCurrent();vm.forgetSecrets();runCurrent()
            assertTrue(cancelled);assertEquals(AccountState.Idle,vm.state.value)
        } finally{store.clear();Dispatchers.resetMain()}
    }
}
