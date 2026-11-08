package com.routemate.app.trip.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.routemate.app.trip.domain.TripFailure

@Composable
fun TripRegistrationPanel(
    state: TripRegistrationState,
    destinationAvailable: Boolean,
    serverConfigured: Boolean,
    usePleos: Boolean,
    onRegister: () -> Unit,
) {
    Text("운행 목적지 등록", style = MaterialTheme.typography.titleLarge)
    if (!serverConfigured) Text("서버 연결이 설정되지 않았습니다.")
    if (!usePleos) Text("샘플 목적지를 서버에 등록합니다. 실제 운행 정보가 아닙니다.")
    when (state) {
        TripRegistrationState.Idle -> Text("표시된 목적지를 확인한 뒤 등록하세요.")
        TripRegistrationState.Submitting -> Text("목적지 등록 중")
        is TripRegistrationState.Registered -> Text("등록 완료 · ${state.trip.destination.name}")
        is TripRegistrationState.Error -> Text(when (state.reason) {
            TripFailure.NETWORK -> "서버에 연결할 수 없습니다. 연결을 확인하고 다시 시도하세요."
            TripFailure.SERVER -> "서버 응답을 확인할 수 없습니다. 다시 시도하세요."
            TripFailure.UNAUTHORIZED -> "사용자 인증이 유효하지 않습니다. 세션을 확인해야 합니다."
            TripFailure.INVALID_DESTINATION -> "목적지 이름과 좌표를 확인하세요."
            TripFailure.CONFIGURATION -> "서버 연결 설정을 확인해야 합니다."
        })
    }
    Button(onRegister, enabled = destinationAvailable && serverConfigured && state != TripRegistrationState.Submitting) {
        Text(if (state is TripRegistrationState.Error) "목적지 등록 재시도" else "목적지 등록")
    }
}
