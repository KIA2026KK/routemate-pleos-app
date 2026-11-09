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
import kotlinx.coroutines.flow.flow

internal class LiveKitVoiceClient(context: Context) : VoiceClient {
    // WebRTC's legacy emulator detection forces 8 kHz even on modern AVDs. That path
    // triggered an AudioRecordJava native span assertion during repeated API 36 calls.
    // Keep real-device audio defaults; modern Android emulator audio supports 48 kHz.
    private val room = LiveKit.create(context, overrides = LiveKitOverrides(audioOptions = AudioOptions(
        javaAudioDeviceModuleCustomizer = { builder ->
            if (Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu") builder.setSampleRate(48_000)
        },
    )))
    override val updates = flow {
        room.events.collect { event ->
            emit(if (event is RoomEvent.FailedToConnect) {
                VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.CONNECTION)
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
