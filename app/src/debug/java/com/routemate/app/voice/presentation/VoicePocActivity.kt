package com.routemate.app.voice.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.voice.application.JoinVoiceCall
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall

/** Manual PoC entry only; tokens stay in memory and are never saved to instance state. */
class VoicePocActivity : ComponentActivity() {
    private var model: VoiceViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val vm: VoiceViewModel = viewModel(factory = viewModelFactory {
                initializer { VoiceViewModel(JoinVoiceCall(LiveKitVoiceCall(applicationContext))) }
            })
            model = vm
            var url by remember { mutableStateOf("ws://10.0.2.2:7880") }
            var token by remember { mutableStateOf("") }
            val state by vm.state.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                vm.join(url, token, granted)
                if (granted) token = ""
            }
            VoicePocScreen(state, url, token, { url = it }, { token = it }, {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    vm.join(url, token, true)
                    token = ""
                } else permission.launch(Manifest.permission.RECORD_AUDIO)
            }, vm::toggleMicrophone, vm::leave)
        }
    }

    override fun onStop() {
        // Foreground-only PoC: no background microphone service or silent ongoing call.
        model?.leave()
        super.onStop()
    }
}

@Composable
internal fun VoicePocScreen(
    state: VoiceState, url: String, token: String,
    onUrl: (String) -> Unit, onToken: (String) -> Unit,
    onJoin: () -> Unit, onMicrophone: () -> Unit, onLeave: () -> Unit,
) {
    val active = state.status in setOf(VoiceStatus.CONNECTING, VoiceStatus.CONNECTED, VoiceStatus.RECONNECTING)
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("음성 통화 PoC", style = MaterialTheme.typography.headlineMedium)
                Text("개발용 실제 음성 통화 · 목적지 매칭과 연결되지 않은 테스트입니다.")
                OutlinedTextField(url, onUrl, enabled = !active, label = { Text("서버 URL") }, singleLine = true)
                OutlinedTextField(token, onToken, enabled = !active, label = { Text("참가자 토큰") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text(when (state.status) {
                    VoiceStatus.IDLE -> "연결 전"
                    VoiceStatus.CONNECTING -> "연결 중"
                    VoiceStatus.CONNECTED -> "연결됨"
                    VoiceStatus.RECONNECTING -> "재접속 중"
                    VoiceStatus.DISCONNECTED -> "연결 종료"
                    VoiceStatus.ERROR -> "연결 실패"
                })
                if (state.error != null) Text(when (state.error) {
                    VoiceFailure.INVALID_INPUT -> "서버 URL과 참가자 토큰을 확인하세요."
                    VoiceFailure.PERMISSION -> "마이크 권한을 허용한 뒤 다시 입장하세요."
                    VoiceFailure.MICROPHONE -> "마이크 변경 실패. 권한을 확인하거나 퇴장 후 다시 입장하세요."
                    VoiceFailure.TIMEOUT -> "연결 시간 초과. 서버 주소와 네트워크를 확인하세요."
                    VoiceFailure.CONNECTION -> "연결 실패. 서버와 토큰 유효기간을 확인하세요."
                })
                Button(onJoin, enabled = !active && url.isNotBlank() && token.isNotBlank()) { Text("통화 입장") }
                if (active) {
                    Text("방: ${state.roomName}")
                    Text("참가자 ${state.participants.size}명")
                    Text("수신 오디오 ${state.subscribedAudioTracks}개")
                    state.participants.forEach { Text(it) }
                    Button(onMicrophone, enabled = state.status == VoiceStatus.CONNECTED) {
                        Text(if (state.microphoneEnabled) "마이크 끄기" else "마이크 켜기")
                    }
                    OutlinedButton(onLeave) { Text("통화 퇴장") }
                }
            }
        }
    }
}
