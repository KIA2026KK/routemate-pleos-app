package com.routemate.app.destination.domain

data class DestinationPlace(val destination: Destination, val address: String, val example: Boolean = false)
fun interface DestinationSearch {
    suspend fun search(query: String): List<DestinationPlace>
}
enum class SearchFailure { UNAVAILABLE, TIMEOUT, NETWORK }
class DestinationSearchException(val reason: SearchFailure) : Exception(reason.name)
