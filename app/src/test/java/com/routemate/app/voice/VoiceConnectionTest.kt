package com.routemate.app.voice

import com.routemate.app.voice.domain.VoiceConnection
import org.junit.Assert.*
import org.junit.Test

class VoiceConnectionTest {
    @Test fun permitsSecureServersAndPrivateDevelopmentAddresses() {
        listOf("wss://example.com", "ws://10.0.2.2:7880", "ws://192.168.0.2:7880", "ws://127.0.0.1:7880")
            .forEach { VoiceConnection(it, "token") }
        assertFalse(VoiceConnection("wss://example.com", "private-token").toString().contains("private-token"))
    }

    @Test fun rejectsPublicCleartextAndUrlCredentials() {
        listOf("https://example.com", "ws://example.com", "ws://8.8.8.8", "ws://10.x.0.2.2", "wss://user:secret@example.com", "wss://example.com?token=secret")
            .forEach { url ->
                try { VoiceConnection(url, "token"); fail("Accepted unsafe URL") }
                catch (_: IllegalArgumentException) { }
            }
    }

    @Test fun rejectsEmptyTokens() {
        try { VoiceConnection("wss://example.com", " "); fail("Accepted empty token") }
        catch (_: IllegalArgumentException) { }
    }
}
