package com.routemate.app.voice.domain

/** Keeps a user-started call alive independently of a screen, without Android in the domain. */
interface CallRuntime {
    fun start()
    fun stop()
    fun event(code: String) {}
    object None : CallRuntime { override fun start() = Unit; override fun stop() = Unit }
}
