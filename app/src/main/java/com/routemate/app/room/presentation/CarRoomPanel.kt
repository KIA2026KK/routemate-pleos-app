package com.routemate.app.room.presentation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routemate.app.design.*
import com.routemate.app.room.domain.*
import com.routemate.app.voice.domain.*

@Composable fun CarRoomPanel(state: RoomUiState, configured: Boolean, prototype: Boolean, destinationReady: Boolean, settingsAllowed: Boolean, onParked: (() -> Unit) -> Unit,
    onRefresh: () -> Unit, onSearch: (Int) -> Unit, onCreate: (String, Boolean, Int) -> Unit, onJoin: (String) -> Unit,
    onLeave: () -> Unit, onEnd: () -> Unit, onConnect: () -> Unit, onMicrophone: () -> Unit) {
    var create by rememberSaveable { mutableStateOf(false) }; var invite by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf(false) }; var end by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }; var invitation by rememberSaveable { mutableStateOf("") }
    var capacity by rememberSaveable { mutableIntStateOf(4) }; var publicRoom by rememberSaveable { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    LaunchedEffect(settingsAllowed) { if (!settingsAllowed) { create = false; invite = false; advanced = false } }
    val keyboard = LocalSoftwareKeyboardController.current
    val active = state.snapshot.tripId != null
    val enabled = configured && !state.busy
    val canFind = enabled && active && destinationReady
    val room = state.snapshot.room
    VehicleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (room == null) "가까운 동행" else "함께 가는 중", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            RouteIconButton("통화방 새로고침", RouteIcons.Refresh, onRefresh, enabled && active)
            Box {
                RouteIconButton("통화방 메뉴", RouteIcons.More, { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    if (room == null) DropdownMenuItem({ Text("초대 ID로 참여") }, { menu = false; onParked { invite = true } }, Modifier.heightIn(min = 76.dp), enabled = canFind)
                    else DropdownMenuItem({ Text("방 정보·초대") }, { menu = false; info = true }, Modifier.heightIn(min = 76.dp))
                    DropdownMenuItem({ Text("운행 종료") }, { menu = false; end = true }, Modifier.heightIn(min = 76.dp), enabled = enabled && active)
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(roomError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (room == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("목적지 주변", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RoomPolicy.radii.forEach { radius -> FilterChip(state.radiusKm == radius, { onSearch(radius) }, label = { Text("${radius}km") },
                    modifier = Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp).semantics { contentDescription = "${radius}km 방 검색" }, enabled = canFind) }
            }
            if (!active && !state.busy) Text("목적지를 선택하면 동행을 찾습니다.", style = MaterialTheme.typography.bodyMedium)
            else if (state.nearby.isEmpty() && !state.busy) Text("가까운 동행이 아직 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.nearby.forEach { nearby -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).semantics { contentDescription = "${nearby.room.name} 참여" }
                    .clickable(canFind) { onJoin(nearby.room.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(nearby.room.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("약 ${nearby.distanceMeters / 1000}km · ${nearby.room.participants}/${nearby.room.capacity}명", style = MaterialTheme.typography.bodyMedium)
                    }; Icon(RouteIcons.Arrow, null)
                }
            } }
            DriveButton("방 만들기", { onParked { name = "${state.snapshot.destination?.name ?: "목적지"} 동행".take(60); create = true } },
                Modifier.fillMaxWidth(), enabled = canFind)
        } else {
            Text(room.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(RouteIcons.People, null, Modifier.size(24.dp)); Text("${room.participants}/${room.capacity}명", style = MaterialTheme.typography.bodyLarge)
                Text(when (state.voice.status) {
                    VoiceStatus.CONNECTED -> if (prototype) "통화 체험 중" else "음성 연결 완료"
                    VoiceStatus.CONNECTING -> "연결 중"
                    VoiceStatus.RECONNECTING -> "재연결 중"
                    VoiceStatus.ERROR -> if (state.voice.error == VoiceFailure.PERMISSION) "마이크 권한이 필요합니다." else "연결을 다시 시도해 주세요"
                    else -> "연결 대기"
                }, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.voice.status in setOf(VoiceStatus.IDLE, VoiceStatus.DISCONNECTED, VoiceStatus.ERROR))
                DriveButton(if (state.voice.error == VoiceFailure.PERMISSION) "마이크 허용" else "다시 연결", onConnect,
                    Modifier.fillMaxWidth().semantics { contentDescription = "음성 다시 연결" }, enabled = enabled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onMicrophone, Modifier.size(100.dp), enabled = state.voice.status == VoiceStatus.CONNECTED) {
                        Icon(if (state.voice.microphoneEnabled) RouteIcons.Mic else RouteIcons.MicOff, if (state.voice.microphoneEnabled) "마이크 끄기" else "마이크 켜기", Modifier.size(40.dp))
                    }; Spacer(Modifier.height(8.dp)); Text(if (state.voice.microphoneEnabled) "마이크 켜짐" else "마이크 꺼짐", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedIconButton(onLeave, Modifier.size(100.dp), enabled = enabled) { Icon(RouteIcons.Leave, "통화방 퇴장", Modifier.size(36.dp)) }
                    Spacer(Modifier.height(8.dp)); Text("나가기", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.voice.error == VoiceFailure.MICROPHONE) Text("마이크 권한·오디오 장치를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("방 만들기") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().heightIn(min = 76.dp), label = { Text("방 이름") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            TextButton({ advanced = !advanced }, Modifier.heightIn(min = 64.dp)) { Text(if (advanced) "옵션 접기" else "${capacity}명 · ${if (publicRoom) "공개" else "비공개"} 방 변경") }
            if (advanced) {
                Row(verticalAlignment = Alignment.CenterVertically) { RouteIconButton("정원 줄이기", RouteIcons.Close, { capacity-- }, capacity > 2)
                    Text("${capacity}명", Modifier.weight(1f)); RouteIconButton("정원 늘리기", RouteIcons.Plus, { capacity++ }, capacity < 8) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("공개 방", Modifier.weight(1f)); Switch(publicRoom, { publicRoom = it }, Modifier.sizeIn(minHeight = 76.dp)) }
            }
        }
    }, confirmButton = { DriveButton("만들고 참여", { create = false; onCreate(name, publicRoom, capacity) }, enabled = enabled && name.isNotBlank()) },
        dismissButton = { TextButton({ create = false }, Modifier.heightIn(min = 64.dp)) { Text("취소") } })
    if (invite) AlertDialog(onDismissRequest = { invite = false }, title = { Text("초대 ID로 참여") }, text = {
        OutlinedTextField(invitation, { invitation = it.take(64) }, Modifier.fillMaxWidth().heightIn(min = 76.dp), label = { Text("방 ID") }, singleLine = true)
    }, confirmButton = { DriveButton("참여", { invite = false; onJoin(invitation.trim()) }, enabled = enabled && invitation.isNotBlank()) },
        dismissButton = { TextButton({ invite = false }, Modifier.heightIn(min = 64.dp)) { Text("취소") } })
    if (info && room != null) {
        val context = LocalContext.current
        AlertDialog(onDismissRequest = { info = false }, title = { Text("방 정보·초대") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(room.name); Text(room.id); Text("참가 중 유지 · 마지막 참가자 퇴장 시 종료") }
        }, confirmButton = { DriveButton("초대 ID 복사", { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RouteMate room", room.id)); info = false }) },
            dismissButton = { TextButton({ info = false }, Modifier.heightIn(min = 64.dp)) { Text("닫기") } })
    }
    if (end) AlertDialog(onDismissRequest = { end = false }, title = { Text("운행을 종료할까요?") }, text = { Text("통화와 방 참여를 종료합니다.") },
        confirmButton = { DriveButton("운행 종료 확인", { end = false; onEnd() }, enabled = enabled) }, dismissButton = { TextButton({ end = false }, Modifier.heightIn(min = 64.dp)) { Text("취소") } })
}
internal fun roomError(error: RoomError) = when (error) {
    RoomError.NETWORK -> "연결을 확인하고 새로고침해 주세요."
    RoomError.UNAUTHORIZED -> "계정 확인이 필요합니다. 더보기에서 계정을 확인해 주세요."
    RoomError.TRIP_REQUIRED -> "목적지를 선택해 주세요."
    RoomError.ROOM_UNAVAILABLE -> "종료된 방입니다. 새로 검색해 주세요."
    RoomError.ROOM_FULL -> "방이 가득 찼습니다. 다른 방을 선택해 주세요."
    RoomError.ALREADY_IN_ROOM -> "현재 방에서 먼저 나가 주세요."
    RoomError.NOT_IN_ROOM -> "참가가 만료됐습니다. 다시 참여해 주세요."
    RoomError.VOICE_UNAVAILABLE -> "음성 서버 연결을 다시 시도해 주세요."
    RoomError.CONFIGURATION -> "서버 연결이 설정되지 않았습니다."
    RoomError.VEHICLE_UNAVAILABLE -> "차량 VIN을 확인할 수 없습니다."
    RoomError.MOCK_VEHICLE_DISABLED -> "샘플 차량을 사용할 수 없는 서버입니다."
    RoomError.INVALID_REQUEST -> "입력한 내용을 확인해 주세요."
    RoomError.TRIP_CHANGED -> "운행이 변경됐습니다. 목적지를 다시 확인해 주세요."
    RoomError.SERVER -> "요청을 완료하지 못했습니다. 다시 시도해 주세요."
}
