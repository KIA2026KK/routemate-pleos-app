package com.routemate.app.trip.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.domain.Destination
import com.routemate.app.trip.application.RegisterTrip
import com.routemate.app.trip.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface TripRegistrationState {
    data object Idle : TripRegistrationState
    data object Submitting : TripRegistrationState
    data class Registered(val trip: RegisteredTrip) : TripRegistrationState
    data class Error(val reason: TripFailure) : TripRegistrationState
}

class TripViewModel(private val register: RegisterTrip) : ViewModel() {
    private val mutableState = MutableStateFlow<TripRegistrationState>(TripRegistrationState.Idle)
    val state = mutableState.asStateFlow()
    private var destination: Destination? = null
    private var request: Job? = null
    private var generation = 0

    fun destinationChanged(value: Destination?) {
        if (destination == value) return
        generation++
        destination = value
        request?.cancel()
        mutableState.value = TripRegistrationState.Idle
    }

    fun submit() {
        if (mutableState.value == TripRegistrationState.Submitting) return
        val target = destination ?: return
        val currentGeneration = generation
        mutableState.value = TripRegistrationState.Submitting
        request = viewModelScope.launch {
            try {
                val registered = register(target)
                if (currentGeneration == generation) mutableState.value = TripRegistrationState.Registered(registered)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (currentGeneration == generation) mutableState.value = TripRegistrationState.Error(
                    (e as? TripRegistrationException)?.reason ?: TripFailure.SERVER)
            }
        }
    }
}
