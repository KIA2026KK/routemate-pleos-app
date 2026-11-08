package com.routemate.app.trip.infrastructure

/** Opaque credential; implementations must not log or back up its plaintext. */
interface SessionStore {
    fun load(): String?
    fun save(token: String)
}
