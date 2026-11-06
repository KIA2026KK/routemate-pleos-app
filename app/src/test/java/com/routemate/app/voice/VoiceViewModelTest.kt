package com.routemate.app.voice

import androidx.lifecycle.ViewModelStore
import com.routemate.app.voice.application.JoinVoiceCall
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.presentation.VoiceViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private class Call : VoiceCall {
        var joins = 0
        var closed = 0
        var micFails = false
        val changes = MutableSharedFlow<VoiceState>()
        override fun join(connection: VoiceConnection) = flow {
            joins++
            try {
                emit(VoiceState(status = VoiceStatus.CONNECTED))
                emitAll(changes)
            } finally { closed++ }
        }
        override suspend fun setMicrophoneEnabled(enabled: Boolean) { check(!micFails) }
    }

    @Test fun permissionAndInvalidInputNeverOpenConnection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val call = Call()
            val vm = VoiceViewModel(JoinVoiceCall(call))
            store.put("voice", vm)
            vm.join("ws://10.0.2.2:7880", "token", false)
            assertEquals(VoiceFailure.PERMISSION, vm.state.value.error)
            vm.join("bad url", "token", true)
            assertEquals(VoiceFailure.INVALID_INPUT, vm.state.value.error)
            runCurrent()
            assertEquals(0, call.joins)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun duplicateJoinMuteFailureLeaveRejoinAndViewModelCleanup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val call = Call()
            val vm = VoiceViewModel(JoinVoiceCall(call))
            store.put("voice", vm)
            vm.join("ws://10.0.2.2:7880", "token", true)
            vm.join("ws://10.0.2.2:7880", "token", true)
            runCurrent()
            assertEquals(1, call.joins)
            vm.toggleMicrophone()
            runCurrent()
            assertTrue(vm.state.value.microphoneEnabled)
            call.micFails = true
            vm.toggleMicrophone()
            runCurrent()
            assertTrue(vm.state.value.microphoneEnabled)
            assertEquals(VoiceFailure.MICROPHONE, vm.state.value.error)
            vm.leave()
            runCurrent()
            assertEquals(1, call.closed)
            assertEquals(VoiceStatus.IDLE, vm.state.value.status)
            vm.join("ws://10.0.2.2:7880", "another-token", true)
            runCurrent()
            assertEquals(2, call.joins)
            store.clear()
            runCurrent()
            assertEquals(2, call.closed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
