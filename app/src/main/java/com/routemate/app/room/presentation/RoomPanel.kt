package com.routemate.app.room.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import com.routemate.app.room.domain.RoomError
import com.routemate.app.voice.domain.*

@Composable
fun RoomPanel(state: RoomUiState,configured: Boolean,onRefresh: ()->Unit,onSearch: (Int)->Unit,
    onCreate: (String,Boolean,Int)->Unit,onJoin: (String)->Unit,onLeave: ()->Unit,onEnd: ()->Unit,
    onConnect: ()->Unit,onMicrophone: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var capacity by rememberSaveable { mutableStateOf("4") }
    var publicRoom by rememberSaveable { mutableStateOf(true) }
    var invitation by rememberSaveable { mutableStateOf("") }
    val active=state.snapshot.tripId!=null
    val enabled=configured&&!state.busy
    HorizontalDivider()
    Text("목적지 그룹 통화",style=MaterialTheme.typography.titleLarge)
    Text("화면을 떠나면 통화를 종료하고 방에서 퇴장합니다.")
    if(active)Text("현재 운행 · ${state.snapshot.destination?.name.orEmpty()}") else Text("먼저 운행 목적지를 등록하세요.")
    if(state.busy)Text("통화방 처리 중")
    state.error?.let { Text(when(it){
        RoomError.NETWORK->"서버 연결이 끊겼습니다. 연결을 확인하고 상태를 다시 확인하세요."
        RoomError.UNAUTHORIZED->"등록된 세션이 없거나 만료됐습니다. 목적지를 등록하거나 계정 관리에서 복구하세요."
        RoomError.TRIP_REQUIRED->"활성 운행이 없습니다. 목적지를 먼저 등록하세요."
        RoomError.ROOM_UNAVAILABLE->"종료되었거나 만료된 방입니다. 방을 다시 검색하세요."
        RoomError.ROOM_FULL->"방 정원이 가득 찼습니다. 다른 방을 선택하세요."
        RoomError.ALREADY_IN_ROOM->"이미 참여한 방이 있습니다. 기존 방에서 먼저 퇴장하세요."
        RoomError.NOT_IN_ROOM->"참가 상태가 만료됐습니다. 상태를 확인하고 다시 참여하세요."
        RoomError.VOICE_UNAVAILABLE->"음성 서버에 연결할 수 없습니다. 잠시 후 다시 연결하세요."
        RoomError.CONFIGURATION->"서버 연결을 설정해야 합니다."
        RoomError.VEHICLE_UNAVAILABLE->"현재 차량 VIN을 확인할 수 없습니다."
        RoomError.MOCK_VEHICLE_DISABLED->"이 서버에서는 샘플 차량을 사용할 수 없습니다."
        RoomError.INVALID_REQUEST->"방 이름·정원·초대 방 ID를 확인하세요."
        RoomError.SERVER->"서버 응답을 확인하지 못했습니다. 상태를 다시 확인하세요."
    }) }
    OutlinedButton(onRefresh,enabled=enabled){Text("통화방 상태 확인")}
    val room=state.snapshot.room
    if(room!=null){
        Text("참여 중 · ${room.name} · ${room.participants}/${room.capacity}명",style=MaterialTheme.typography.titleMedium)
        Text("공유 방 ID · ${room.id}")
        Text("방 만료 · ${room.expiresAt}")
        Text(when(state.voice.status){
            VoiceStatus.IDLE->"음성 연결 대기"
            VoiceStatus.CONNECTING->"음성 연결 중"
            VoiceStatus.CONNECTED->"음성 연결 완료"
            VoiceStatus.RECONNECTING->"음성 재연결 중"
            VoiceStatus.DISCONNECTED->"음성 연결 종료"
            VoiceStatus.ERROR->if(state.voice.error==VoiceFailure.PERMISSION)"마이크 권한이 필요합니다." else "음성 연결 오류 · 다시 연결하세요."
        })
        state.voice.participants.forEachIndexed { index,_ -> Text("통화 참가자 ${index+1}") }
        val connected=state.voice.status in setOf(VoiceStatus.CONNECTING,VoiceStatus.CONNECTED,VoiceStatus.RECONNECTING)
        Button(onConnect,enabled=enabled&&!connected){Text("그룹 음성 연결")}
        Button(onMicrophone,enabled=state.voice.status==VoiceStatus.CONNECTED){Text(if(state.voice.microphoneEnabled)"마이크 끄기" else "마이크 켜기")}
        OutlinedButton(onLeave,enabled=enabled){Text("통화방 퇴장")}
    } else {
        Row(horizontalArrangement=Arrangement.SpaceEvenly){
            OutlinedButton({onSearch(5)},enabled=enabled&&active){Text("5km 방 검색")}
            OutlinedButton({onSearch(10)},enabled=enabled&&active){Text("10km 방 검색")}
        }
        Text("목적지 반경 ${state.radiusKm}km · ${state.nearby.size}개 방")
        state.nearby.forEach { nearby ->
            Text("${nearby.room.name} · ${nearby.room.participants}/${nearby.room.capacity}명 · ${nearby.distanceMeters}m")
            OutlinedButton({onJoin(nearby.room.id)},enabled=enabled&&active){Text("${nearby.room.name} 참여")}
        }
        OutlinedTextField(name,{name=it},label={Text("새 통화방 이름")},singleLine=true,enabled=enabled)
        OutlinedTextField(capacity,{capacity=it},label={Text("정원 2~8명")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=enabled)
        Row { Checkbox(publicRoom,{publicRoom=it},enabled=enabled);Text("검색에 공개") }
        Text("비공개 방은 공유한 방 ID로 참여할 수 있습니다.")
        Button({onCreate(name,publicRoom,capacity.toIntOrNull()?:0)},enabled=enabled&&active&&name.isNotBlank()&&name.length<=60&&(capacity.toIntOrNull()?:0) in 2..8){Text("통화방 생성")}
        OutlinedTextField(invitation,{invitation=it},label={Text("초대받은 방 ID")},singleLine=true,enabled=enabled)
        OutlinedButton({onJoin(invitation.trim())},enabled=enabled&&active&&invitation.isNotBlank()){Text("방 ID로 참여")}
    }
    OutlinedButton(onEnd,enabled=enabled&&active){Text("운행 종료")}
}
