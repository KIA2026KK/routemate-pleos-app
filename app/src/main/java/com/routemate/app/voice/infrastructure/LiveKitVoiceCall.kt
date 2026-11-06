package com.routemate.app.voice.infrastructure

import android.content.Context
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LiveKitVoiceCall internal constructor(
    private val createClient: () -> VoiceClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val timeoutMs: Long = 20_000,
) : VoiceCall {
    constructor(context: Context) : this({ LiveKitVoiceClient(context.applicationContext) })

    private var client: VoiceClient? = null
    private var notifyState: (() -> Unit)? = null

    override fun join(connection: VoiceConnection): Flow<VoiceState> = channelFlow {
        check(client == null) { "A call is already active" }
        val current = createClient()
        client = current
        notifyState = { trySend(current.snapshot()) }
        try {
            send(VoiceState(status = VoiceStatus.CONNECTING))
            val ended = CompletableDeferred<Unit>()
            val events = launch(start = CoroutineStart.UNDISPATCHED) {
                current.updates.collect { state ->
                    send(state)
                    if (state.status == VoiceStatus.DISCONNECTED || state.status == VoiceStatus.ERROR) {
                        // Finish the flow to release native resources on server-side disconnect too.
                        ended.complete(Unit)
                    }
                }
            }
            try {
                withTimeout(timeoutMs) { current.connect(connection) }
                send(current.snapshot()) // Join muted; user explicitly enables the microphone.
                ended.await()
            } finally {
                events.cancelAndJoin()
            }
        } catch (e: TimeoutCancellationException) {
            send(VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.TIMEOUT))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.CONNECTION))
        } finally {
            notifyState = null
            client = null
            current.close()
        }
    }.flowOn(dispatcher)

    override suspend fun setMicrophoneEnabled(enabled: Boolean) = withContext(dispatcher) {
        val current = checkNotNull(client)
        check(withTimeout(timeoutMs) { current.setMicrophoneEnabled(enabled) })
        notifyState?.invoke()
        Unit
    }
}
