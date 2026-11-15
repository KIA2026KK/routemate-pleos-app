package com.routemate.app.destination.application

import com.routemate.app.destination.domain.DestinationSearch

class SearchDestinations(private val search: DestinationSearch) {
    suspend operator fun invoke(query: String) = search.search(query.trim().take(160))
}
