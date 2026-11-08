package com.routemate.app

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real-server test. No tokens or signing secrets are stored in source. */
class LiveKitConnectionTest {
    @Test fun threeParticipantsPublishSubscribeMuteAndLeave() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("livekitUrl")
        val tokens = listOf("livekitTokenA", "livekitTokenB", "livekitTokenC").map { args.getString(it) }
        assumeTrue("Requires local LiveKit server and three short-lived tokens", url != null && tokens.all { it != null })
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}").close()
        val calls = List(3) { LiveKitVoiceCall(context) }
        val states = List(3) { MutableStateFlow(VoiceState()) }
        val jobs = calls.mapIndexed { index, call ->
            launch { call.join(VoiceConnection(url!!, tokens[index]!!)).collect { states[index].value = it } }
        }
        try {
            withTimeout(60_000) {
                states.forEach { state -> state.first { it.status == VoiceStatus.CONNECTED && it.participants.size == 3 } }
                calls.forEach { it.setMicrophoneEnabled(true) }
                states.forEach { state -> state.first { it.microphoneEnabled && it.subscribedAudioTracks == 2 } }
                calls[0].setMicrophoneEnabled(false)
                states[0].first { !it.microphoneEnabled }
                jobs[2].cancelAndJoin()
                states.take(2).forEach { state -> state.first { it.participants.size == 2 } }
            }
            assertEquals(VoiceStatus.CONNECTED, states[0].value.status)
        } finally {
            jobs.forEach { it.cancel() }
            jobs.joinAll()
        }
    }
}
