package com.routemate.app.voice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routemate.app.voice.application.JoinVoiceCall
import com.routemate.app.voice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VoiceViewModel(private val joinCall: JoinVoiceCall) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceState())
    val state = mutableState.asStateFlow()
    private var callJob: Job? = null
    private var micJob: Job? = null
    private var leaving = false

    fun join(url: String, token: String, microphonePermission: Boolean) {
        if (callJob?.isActive == true || leaving) return
        if (!microphonePermission) {
            mutableState.value = VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.PERMISSION)
            return
        }
        val connection = try { VoiceConnection(url.trim(), token.trim()) } catch (_: Exception) {
            mutableState.value = VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.INVALID_INPUT)
            return
        }
        mutableState.value = VoiceState(status = VoiceStatus.CONNECTING)
        callJob = viewModelScope.launch {
            try {
                joinCall(connection).collect { mutableState.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = VoiceState(status = VoiceStatus.ERROR, error = VoiceFailure.CONNECTION)
            }
        }
    }

    fun toggleMicrophone() {
        if (state.value.status != VoiceStatus.CONNECTED || micJob?.isActive == true || leaving) return
        val enabled = !state.value.microphoneEnabled
        micJob = viewModelScope.launch {
            try {
                joinCall.setMicrophoneEnabled(enabled)
                mutableState.update { it.copy(microphoneEnabled = enabled, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(error = VoiceFailure.MICROPHONE) }
            }
        }
    }

    fun leave() {
        if (leaving) return
        leaving = true
        val previousCall = callJob
        val previousMic = micJob
        previousMic?.cancel()
        previousCall?.cancel()
        mutableState.value = VoiceState()
        viewModelScope.launch {
            previousMic?.join()
            previousCall?.join()
            leaving = false
        }
    }
}
