package com.routemate.app.voice.infrastructure

import com.routemate.app.voice.domain.VoiceConnection
import com.routemate.app.voice.domain.VoiceState
import kotlinx.coroutines.flow.Flow

/** SDK boundary, allowing lifecycle/failure tests without loading native WebRTC. */
internal interface VoiceClient {
    val updates: Flow<VoiceState>
    fun snapshot(): VoiceState
    suspend fun connect(connection: VoiceConnection)
    suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean
    fun close()
}
