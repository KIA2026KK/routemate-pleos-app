package com.routemate.app.destination.infrastructure

import com.routemate.app.destination.domain.*

/** Explicit test fixtures; these are never substituted for a failed Pleos destination. */
object ExampleDestinations {
    val songjeong = DestinationPlace(Destination("광주송정역", 35.1374, 126.7916), "광주광역시 광산구 상무대로 201", true)
    val seoul = DestinationPlace(Destination("서울역", 37.5547, 126.9706), "서울특별시 용산구 한강대로 405", true)
    val all = listOf(songjeong, seoul)
    private fun key(value: String) = value.filterNot(Char::isWhitespace).lowercase()
    fun matching(query: String) = all.filter { key(query) in key(it.destination.name + it.address) }
}
