package com.routemate.app.destination.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.domain.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DestinationState {
    data object Empty : DestinationState
    data object Loading : DestinationState
    data class Loaded(val destination: Destination) : DestinationState
    data object Error : DestinationState
}

class DestinationViewModel(private val loadDestination: LoadDestination) : ViewModel() {
    private val mutableState = MutableStateFlow<DestinationState>(DestinationState.Empty)
    val state = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value == DestinationState.Loading) return
        mutableState.value = DestinationState.Loading
        viewModelScope.launch {
            try {
                mutableState.value = loadDestination()?.let(DestinationState::Loaded) ?: DestinationState.Empty
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DestinationState.Error
            }
        }
    }
}
