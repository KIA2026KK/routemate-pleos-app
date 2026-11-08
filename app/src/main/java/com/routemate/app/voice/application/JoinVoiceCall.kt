package com.routemate.app.voice.application

import com.routemate.app.voice.domain.VoiceCall
import com.routemate.app.voice.domain.VoiceConnection

class JoinVoiceCall(private val call: VoiceCall) {
    operator fun invoke(connection: VoiceConnection) = call.join(connection)
    suspend fun setMicrophoneEnabled(enabled: Boolean) = call.setMicrophoneEnabled(enabled)
}
