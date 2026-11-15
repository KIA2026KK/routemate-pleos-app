package com.routemate.app.destination.infrastructure

import com.routemate.app.destination.domain.Destination
import com.routemate.app.destination.domain.DestinationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockDestinationProvider(
    initial: Destination = ExampleDestinations.songjeong.destination,
    private val save: (Destination) -> Unit = {},
) : DestinationProvider {
    private val selected = MutableStateFlow(initial)
    override suspend fun currentDestination() = selected.value
    override fun observeDestinations() = selected.asStateFlow()
    fun select(destination: Destination) { save(destination); selected.value = destination }
}
