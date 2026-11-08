package com.routemate.app.voice

import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveKitVoiceCallTest {
    private val connection = VoiceConnection("ws://127.0.0.1:7880", "test-token")

    private class Client : VoiceClient {
        override val updates = MutableSharedFlow<VoiceState>()
        var state = VoiceState(status = VoiceStatus.CONNECTED, participants = listOf("driver-a"))
        var connect: suspend () -> Unit = {}
        var micSuccess = true
        var closed = 0
        override fun snapshot() = state
        override suspend fun connect(connection: VoiceConnection) = connect()
        override suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean {
            if (micSuccess) state = state.copy(microphoneEnabled = enabled)
            return micSuccess
        }
        override fun close() { closed++ }
    }

    @Test fun joinsMutedUpdatesParticipantsAndReleasesOnCancellation() = runTest {
        val client = Client()
        val call = LiveKitVoiceCall({ client }, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<VoiceState>()
        val job = backgroundScope.launch { call.join(connection).toList(states) }
        runCurrent()
        assertEquals(VoiceStatus.CONNECTING, states.first().status)
        assertFalse(states.last().microphoneEnabled)
        call.setMicrophoneEnabled(true)
        runCurrent()
        assertTrue(states.last().microphoneEnabled)
        client.updates.emit(client.state.copy(participants = listOf("driver-a", "driver-b", "driver-c")))
        runCurrent()
        assertEquals(3, states.last().participants.size)
        client.updates.emit(client.state.copy(status = VoiceStatus.RECONNECTING))
        runCurrent()
        assertEquals(VoiceStatus.RECONNECTING, states.last().status)
        job.cancelAndJoin()
        assertEquals(1, client.closed)
    }

    @Test fun serverDisconnectFinishesAndReleasesSession() = runTest {
        val client = Client()
        val call = LiveKitVoiceCall({ client }, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<VoiceState>()
        val job = launch { call.join(connection).toList(states) }
        runCurrent()
        client.updates.emit(VoiceState(status = VoiceStatus.DISCONNECTED))
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(VoiceStatus.DISCONNECTED, states.last().status)
        assertEquals(1, client.closed)
    }

    @Test fun connectionFailureIsRedactedAndSessionCanBeRetried() = runTest {
        val clients = mutableListOf<Client>()
        val call = LiveKitVoiceCall({ Client().also {
            clients.add(it)
            it.connect = { throw IllegalStateException("sensitive-token") }
        } }, StandardTestDispatcher(testScheduler))
        repeat(2) {
            val states = call.join(connection).toList()
            assertEquals(VoiceFailure.CONNECTION, states.last().error)
            assertFalse(states.toString().contains("sensitive-token"))
        }
        assertEquals(listOf(1, 1), clients.map { it.closed })
    }

    @Test fun connectionTimeoutAndCancellationReleaseResources() = runTest {
        val client = Client().apply { connect = { awaitCancellation() } }
        val call = LiveKitVoiceCall({ client }, StandardTestDispatcher(testScheduler), 500)
        assertEquals(VoiceFailure.TIMEOUT, call.join(connection).toList().last().error)
        assertEquals(1, client.closed)
        val job = launch { call.join(connection).collect() }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(2, client.closed)
    }

    @Test fun rejectedMicrophoneChangeDoesNotClaimSuccess() = runTest {
        val client = Client().apply { micSuccess = false }
        val call = LiveKitVoiceCall({ client }, StandardTestDispatcher(testScheduler))
        val job = backgroundScope.launch { call.join(connection).collect() }
        runCurrent()
        try {
            call.setMicrophoneEnabled(true)
            fail("Expected SDK rejection")
        } catch (_: IllegalStateException) { }
        assertFalse(client.state.microphoneEnabled)
        job.cancelAndJoin()
    }
}
