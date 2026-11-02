package com.routemate.app.destination.domain

/** A valid geographic destination. This layer has no Android or SDK dependencies. */
data class Destination(val name: String, val latitude: Double, val longitude: Double) {
    init {
        require(name.isNotBlank()) { "Destination name must not be blank" }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}
