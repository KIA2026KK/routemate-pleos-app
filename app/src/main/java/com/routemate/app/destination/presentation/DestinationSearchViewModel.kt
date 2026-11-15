package com.routemate.app.destination.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.destination.application.SearchDestinations
import com.routemate.app.destination.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DestinationSearchState(val query: String = "", val loading: Boolean = false,
    val places: List<DestinationPlace> = emptyList(), val error: SearchFailure? = null)

class DestinationSearchViewModel(private val search: SearchDestinations, private val debounceMs: Long = 350) : ViewModel() {
    private val mutableState = MutableStateFlow(DestinationSearchState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun query(value: String, immediate: Boolean = false) {
        job?.cancel()
        val current = ++generation
        val query = value.take(160)
        mutableState.value = DestinationSearchState(query, loading = true)
        job = viewModelScope.launch {
            try {
                if (!immediate) delay(debounceMs)
                val places = search(query)
                if (current == generation) mutableState.value = DestinationSearchState(query, places = places)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current == generation) mutableState.value = DestinationSearchState(query,
                    error = (e as? DestinationSearchException)?.reason ?: SearchFailure.NETWORK)
            }
        }
    }
    fun cancel() { generation++; job?.cancel() }
}
