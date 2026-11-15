package com.routemate.app.destination.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationAccessException
import com.routemate.app.destination.domain.DestinationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DestinationState {
    data object Empty : DestinationState
    data object Loading : DestinationState
    data class Loaded(val destination: Destination) : DestinationState
    data class Error(val reason: DestinationFailure = DestinationFailure.UNKNOWN) : DestinationState
}

class DestinationViewModel(private val loadDestination: LoadDestination, private val onSelect: ((Destination) -> Unit)? = null) : ViewModel() {
    private val mutableState = MutableStateFlow<DestinationState>(DestinationState.Empty)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { refresh() }
    fun select(destination: Destination) { onSelect?.invoke(destination) }

    fun refresh() {
        if (mutableState.value == DestinationState.Loading) return
        observation?.cancel()
        mutableState.value = DestinationState.Loading
        observation = viewModelScope.launch {
            try {
                loadDestination.observe().collect { destination ->
                    mutableState.value = destination?.let(DestinationState::Loaded) ?: DestinationState.Empty
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = DestinationState.Error(
                    (failure as? DestinationAccessException)?.reason ?: DestinationFailure.UNKNOWN,
                )
            }
        }
    }
}
