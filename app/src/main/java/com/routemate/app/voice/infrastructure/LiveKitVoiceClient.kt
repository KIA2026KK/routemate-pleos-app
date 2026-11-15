package com.routemate.app.voice.infrastructure

import android.content.Context
import android.os.Build
import com.routemate.app.voice.domain.*
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.AudioOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.util.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
internal class LiveKitVoiceClient(context: Context) : VoiceClient {
    // WebRTC's legacy emulator detection forces 8 kHz even on modern AVDs. That path
    // triggered an AudioRecordJava native span assertion during repeated API 36 calls.
    // Keep real-device audio defaults; modern Android emulator audio supports 48 kHz.
    private val room = LiveKit.create(context, overrides = LiveKitOverrides(audioOptions = AudioOptions(
        javaAudioDeviceModuleCustomizer = { builder ->
            if (Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu") builder.setSampleRate(48_000)
        },
    )))
    override val updates = channelFlow {
        // isMicrophoneEnabled is an asynchronously derived SDK StateFlow. Its final
        // value can arrive after TrackPublished and after setMicrophoneEnabled returns.
        // Observe it directly so a successful publication cannot leave the UI muted.
        launch {
            room.localParticipant::isMicrophoneEnabled.flow.collect {
                if (room.state != Room.State.DISCONNECTED) send(snapshot())
            }
        }
        launch {
            room::remoteParticipants.flow.flatMapLatest { remotes ->
                merge(*(listOf(room.localParticipant) + remotes.values).map { participant ->
                    merge(participant::isSpeaking.flow.map { Unit }, participant::audioLevel.flow.map { Unit },
                        participant::isMicrophoneEnabled.flow.map { Unit }, participant::audioTrackPublications.flow.map { Unit })
                }.toTypedArray())
            }.sample(100).collect { if (room.state != Room.State.DISCONNECTED) send(snapshot()) }
        }
        room.events.collect { event ->
            send(if (event is RoomEvent.FailedToConnect) {
                VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.CONNECTION)
            } else if(event is RoomEvent.Disconnected) {
                snapshot().copy(disconnectReason=event.reason.name)
            } else snapshot())
        }
    }

    override fun snapshot(): VoiceState = VoiceState(
        status = when (room.state) {
            Room.State.CONNECTING -> VoiceStatus.CONNECTING
            Room.State.CONNECTED -> VoiceStatus.CONNECTED
            Room.State.RECONNECTING -> VoiceStatus.RECONNECTING
            Room.State.DISCONNECTED -> VoiceStatus.DISCONNECTED
        },
        roomName = room.name.orEmpty(),
        participants = (listOf(room.localParticipant) + room.remoteParticipants.values)
            .mapNotNull { it.identity?.value }.sorted(),
        microphoneEnabled = room.localParticipant.isMicrophoneEnabled,
        members = (listOf(room.localParticipant) + room.remoteParticipants.values).mapNotNull { participant ->
            participant.identity?.value?.let { id -> VoiceParticipant(id, participant.name.orEmpty().take(40),
                participant === room.localParticipant, participant.isSpeaking&&participant.isMicrophoneEnabled, participant.isMicrophoneEnabled,
                participant.audioLevel.coerceIn(0f, 1f)) }
        }.sortedWith(compareByDescending<VoiceParticipant> { it.local }.thenBy { it.id }),
        subscribedAudioTracks = room.remoteParticipants.values.sumOf { participant ->
            participant.trackPublications.values.count { it.track is RemoteAudioTrack }
        },
    )

    override suspend fun connect(connection: VoiceConnection) {
        room.connect(connection.url, connection.token)
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean) =
        room.localParticipant.setMicrophoneEnabled(enabled)

    override fun close() = room.release()
}
