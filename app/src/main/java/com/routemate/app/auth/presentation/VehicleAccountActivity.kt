package com.routemate.app.auth.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.BuildConfig
import com.routemate.app.auth.application.ManageVehicleSession
import com.routemate.app.auth.domain.AccountFailure
import com.routemate.app.auth.infrastructure.HttpVehicleAccountAccess
import com.routemate.app.vehicleProvider
import com.routemate.app.vehicleStore
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VehicleAccountActivity : ComponentActivity() {
    private var model: VehicleAccountViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val vm: VehicleAccountViewModel = viewModel(factory = viewModelFactory {
                initializer { VehicleAccountViewModel(ManageVehicleSession(HttpVehicleAccountAccess(BuildConfig.BACKEND_URL,
                    { identity -> vehicleStore(applicationContext, identity) }, vehicleProvider(applicationContext),
                    allowLocalHttp = BuildConfig.DEBUG))) }
            })
            model = vm
            val state by vm.state.collectAsStateWithLifecycle()
            val input by vm.input.collectAsStateWithLifecycle()
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("차량 계정 관리", style = MaterialTheme.typography.headlineMedium)
                        Text("현재 차량 VIN과 등록된 계정으로 세션을 관리합니다.")
                        if (BuildConfig.BACKEND_URL.isBlank()) Text("서버 연결이 설정되지 않았습니다.")
                        Text("복구키는 안전한 곳에 별도로 보관하세요. 재발급하면 이전 키는 무효화됩니다.")
                        Text("계정을 복구하면 기존 설치의 세션이 종료됩니다. VIN만으로는 복구할 수 없습니다.")
                        val enabled = BuildConfig.BACKEND_URL.isNotBlank() && state != AccountState.Working
                        Button(vm::renew, enabled = enabled) { Text("세션 갱신") }
                        OutlinedButton(vm::issueKey, enabled = enabled) { Text("복구키 발급·재발급") }
                        when (val value = state) {
                            AccountState.Working -> Text("계정 처리 중")
                            is AccountState.Ready -> {
                                Text(if(value.restored) "계정 복구 완료" else "세션 갱신 완료")
                                Text("차량 VIN · ${value.lease.maskedVin}")
                            }
                            is AccountState.KeyIssued -> {
                                Text("복구키 발급 완료")
                                Text(value.key.value)
                                Text("유효기간: ${value.key.expiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                                Text("이 키는 만료 또는 재발급 전까지 복구에 사용할 수 있습니다. 이 화면을 떠나면 표시가 사라집니다.")
                                OutlinedButton(onClick = {
                                    val clip = ClipData.newPlainText("RouteMate recovery key", value.key.value)
                                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                                    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                                    Toast.makeText(this@VehicleAccountActivity,"복구키를 복사했습니다.",Toast.LENGTH_SHORT).show()
                                }) { Text("복구키 복사") }
                            }
                            is AccountState.Error -> Text(when(value.reason) {
                                AccountFailure.UNAUTHORIZED -> "등록된 세션이 없습니다. 목적지를 등록하거나 복구키로 계정을 복구하세요."
                                AccountFailure.RECOVERY_REJECTED -> "복구키가 유효하지 않거나 현재 차량과 일치하지 않습니다."
                                AccountFailure.NETWORK -> "연결을 확인한 뒤 다시 시도하세요."
                                AccountFailure.VEHICLE_UNAVAILABLE -> "차량 VIN을 확인할 수 없습니다. 차량 연결과 등록 상태를 확인하세요."
                                AccountFailure.CONFIGURATION -> "서버 연결 설정을 확인해야 합니다."
                                AccountFailure.MOCK_DISABLED -> "이 서버에서는 샘플 차량을 사용할 수 없습니다."
                                AccountFailure.SERVER -> "계정 처리를 완료하지 못했습니다. 다시 시도하세요."
                            })
                            AccountState.Idle -> Unit
                        }
                        OutlinedTextField(input, vm::updateInput, enabled = state != AccountState.Working,
                            label = { Text("보관한 복구키") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation())
                        Button(vm::recover, enabled = enabled && input.isNotBlank()) { Text("복구키로 계정 복구") }
                        OutlinedButton(onClick = { finish() }) { Text("닫기") }
                    }
                }
            }
        }
    }
    override fun onStop() {
        model?.forgetSecrets()
        super.onStop()
    }
}
