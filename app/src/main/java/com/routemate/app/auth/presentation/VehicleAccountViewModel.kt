package com.routemate.app.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.auth.application.ManageVehicleSession
import com.routemate.app.auth.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface AccountState {
    data object Idle : AccountState
    data object Working : AccountState
    data class KeyIssued(val key: RecoveryKey) : AccountState
    data class Ready(val lease: AccountLease, val restored: Boolean) : AccountState
    data class Error(val reason: AccountFailure) : AccountState
}

class VehicleAccountViewModel(private val manage: ManageVehicleSession) : ViewModel() {
    private val mutableState = MutableStateFlow<AccountState>(AccountState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableInput = MutableStateFlow("")
    val input = mutableInput.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun updateInput(value: String) { if (state.value != AccountState.Working) mutableInput.value = value }
    fun renew() = perform { AccountState.Ready(manage.renew(), false) }
    fun issueKey() = perform { AccountState.KeyIssued(manage.createRecoveryKey()) }
    fun recover() {
        if (state.value == AccountState.Working) return
        val key = mutableInput.value.trim()
        mutableInput.value = ""
        perform { AccountState.Ready(manage.recover(key), true) }
    }
    private fun perform(action: suspend () -> AccountState) {
        if (state.value == AccountState.Working) return
        val current = generation
        mutableState.value = AccountState.Working
        job = viewModelScope.launch {
            try {
                val result = action()
                if (current == generation) mutableState.value = result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current == generation) mutableState.value = AccountState.Error(
                    (e as? AccountAccessException)?.reason ?: AccountFailure.SERVER)
            }
        }
    }
    fun forgetSecrets() {
        generation++
        job?.cancel()
        mutableInput.value = ""
        mutableState.value = AccountState.Idle
    }
}
