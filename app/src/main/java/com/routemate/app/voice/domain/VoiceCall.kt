package com.routemate.app.voice.domain

import java.net.URI
import kotlinx.coroutines.flow.Flow

/** Short-lived participant token only. Never include a LiveKit API secret. */
class VoiceConnection(val url: String, val token: String) {
    init {
        val uri = URI(url)
        val host = requireNotNull(uri.host) { "Server host required" }
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        val local = host == "localhost" || host == "[::1]" ||
            (host.split('.').size == 4 && octets.size == 4 && octets.all { it in 0..255 } &&
                (octets[0] == 127 || octets[0] == 10 ||
                    (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 172 && octets[1] in 16..31)))
        require(uri.scheme == "wss" || (uri.scheme == "ws" && local))
        require(uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(token.isNotBlank() && token.none { it.isWhitespace() })
    }
    override fun toString() = "VoiceConnection(token=<redacted>)"
}

enum class VoiceStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR }
data class VoiceParticipant(val id: String, val name: String = "", val local: Boolean = false,
    val speaking: Boolean = false, val microphoneEnabled: Boolean = false, val audioLevel: Float = 0f)

data class VoiceState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val roomName: String = "",
    val participants: List<String> = emptyList(),
    val microphoneEnabled: Boolean = false,
    val subscribedAudioTracks: Int = 0,
    val error: VoiceFailure? = null,
    val members: List<VoiceParticipant> = emptyList(),
    val disconnectReason: String? = null,
)

enum class VoiceFailure { CONNECTION, TIMEOUT, MICROPHONE, PERMISSION, INVALID_INPUT }

interface VoiceCall {
    /** Collection owns the call. Cancelling collection releases the microphone and connection. */
    fun join(connection: VoiceConnection): Flow<VoiceState>
    suspend fun setMicrophoneEnabled(enabled: Boolean)
}
