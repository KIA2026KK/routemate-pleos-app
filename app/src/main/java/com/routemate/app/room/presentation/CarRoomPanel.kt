package com.routemate.app.room.presentation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routemate.app.design.*
import com.routemate.app.room.domain.RoomError
import com.routemate.app.voice.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CarRoomPanel(state:RoomUiState,configured:Boolean,settingsAllowed:Boolean,prototype:Boolean,
    onRequestSettings:()->Unit,onRefresh:()->Unit,onSearch:(Int)->Unit,onCreate:(String,Boolean,Int)->Unit,
    onJoin:(String)->Unit,onLeave:()->Unit,onEnd:()->Unit,onConnect:()->Unit,onMicrophone:()->Unit){
    var showCreate by rememberSaveable{mutableStateOf(false)}
    var showInvite by rememberSaveable{mutableStateOf(false)}
    var showInfo by rememberSaveable{mutableStateOf(false)}
    var showEnd by rememberSaveable{mutableStateOf(false)}
    var searched by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    var capacity by rememberSaveable{mutableIntStateOf(4)}
    var publicRoom by rememberSaveable{mutableStateOf(true)}
    var invitation by rememberSaveable{mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    val active=state.snapshot.tripId!=null
    val enabled=configured&&!state.busy
    val room=state.snapshot.room
    LaunchedEffect(state.snapshot.destination){searched=false}
    LaunchedEffect(settingsAllowed){if(!settingsAllowed){showCreate=false;showInvite=false;showInfo=false}}
    VehicleCard(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text("목적지 그룹 통화",style=MaterialTheme.typography.titleLarge)
            StatusPill(if(room==null)"가까운 목적지" else "참여 중",accent=room!=null)
        }
        if(active)Text("현재 운행 · ${state.snapshot.destination?.name.orEmpty()}",color=MaterialTheme.colorScheme.onSurfaceVariant)
        else Text("먼저 운행 목적지를 등록하세요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{error->
            Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)){
                Text(roomError(error),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if(room==null){
            Text("반경을 고르면 함께 갈 대화가 보여요",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                listOf(5,10).forEach{radius->
                    DriveButton("${radius}km 방 검색",{searched=true;onSearch(radius)},Modifier.weight(1f),
                        enabled=enabled&&active,secondary=state.radiusKm!=radius)
                }
            }
            if(!active)Text("목적지 등록 후 검색할 수 있습니다.",style=MaterialTheme.typography.bodyMedium)
            else if(state.nearby.isEmpty()&&!state.busy)Text(if(searched)"아직 참여할 수 있는 방이 없어요. 새 방을 만들어 보세요." else "최종 목적지를 기준으로 통화방을 찾아보세요.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.nearby.forEach{nearby->
                Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),shape=RoundedCornerShape(18.dp)){
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Text(nearby.room.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                        Text("${nearby.room.participants}/${nearby.room.capacity}명 · 목적지 간 약 ${"%.1f".format(nearby.distanceMeters/1000.0)}km",
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        DriveButton("${nearby.room.name} 참여",{onJoin(nearby.room.id)},Modifier.fillMaxWidth(),enabled=enabled&&active)
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                DriveButton("새 방 만들기",{if(settingsAllowed)showCreate=true else onRequestSettings()},Modifier.weight(1f),enabled=enabled&&active,secondary=true)
                DriveButton("초대받은 방",{if(settingsAllowed)showInvite=true else onRequestSettings()},Modifier.weight(1f),enabled=enabled&&active,secondary=true)
            }
            Text("방 생성·초대 ID 입력은 정차 후 이용하세요.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }else{
            Text(room.name,style=MaterialTheme.typography.headlineMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("참여 중 · ${room.name} · ${room.participants}/${room.capacity}명",color=MaterialTheme.colorScheme.onSurfaceVariant)
            val status=when(state.voice.status){
                VoiceStatus.IDLE->"음성 연결 대기"
                VoiceStatus.CONNECTING->"음성 연결 중"
                VoiceStatus.CONNECTED->if(prototype)"통화 화면 체험 중" else "음성 연결 완료"
                VoiceStatus.RECONNECTING->"음성 재연결 중"
                VoiceStatus.DISCONNECTED->"음성 연결 종료"
                VoiceStatus.ERROR->if(state.voice.error==VoiceFailure.PERMISSION)"마이크 권한이 필요합니다." else "음성 연결 오류 · 다시 연결하세요."
            }
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(18.dp)){
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text(status,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                    Text(if(prototype){if(state.voice.microphoneEnabled)"마이크 켜짐 상태 체험" else "마이크 꺼짐 상태 체험"}
                        else if(state.voice.microphoneEnabled)"마이크 켜짐 · 내 목소리가 전달됩니다" else "마이크 꺼짐 · 듣기만 하는 상태입니다")
                    if(prototype)Text("체험 모드에서는 실제 음성을 송수신하지 않습니다.",style=MaterialTheme.typography.bodyMedium)
                    else if(state.voice.status==VoiceStatus.CONNECTED)Text("통화 연결 ${state.voice.participants.size}명",style=MaterialTheme.typography.bodyMedium)
                    if(state.voice.error==VoiceFailure.MICROPHONE)Text("마이크 상태 변경에 실패했습니다. 권한·오디오 장치를 확인하고 다시 시도하세요.",
                        color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
                }
            }
            val connected=state.voice.status in setOf(VoiceStatus.CONNECTING,VoiceStatus.CONNECTED,VoiceStatus.RECONNECTING)
            if(!connected)DriveButton(if(prototype)"통화 화면 체험" else "그룹 음성 연결",onConnect,Modifier.fillMaxWidth(),enabled=enabled)
            if(connected)DriveButton(if(state.voice.microphoneEnabled)"마이크 끄기" else "마이크 켜기",onMicrophone,Modifier.fillMaxWidth(),
                enabled=state.voice.status==VoiceStatus.CONNECTED,secondary=state.voice.microphoneEnabled)
            DriveButton("통화방 퇴장",onLeave,Modifier.fillMaxWidth(),enabled=enabled,secondary=true)
            if(settingsAllowed)DriveButton("방 정보·초대",{showInfo=true},Modifier.fillMaxWidth(),enabled=enabled,secondary=true)
        }
        DriveButton("통화방 상태 확인",onRefresh,Modifier.fillMaxWidth(),enabled=enabled,secondary=true)
        Text("화면을 떠나면 통화를 종료하고 방에서 퇴장합니다.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        DriveButton("운행 종료",{showEnd=true},Modifier.fillMaxWidth(),enabled=enabled&&active,secondary=true)
    }
    if(showCreate&&settingsAllowed)AlertDialog(onDismissRequest={showCreate=false},title={Text("새 통화방 만들기")},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            OutlinedTextField(name,{name=it.take(60)},Modifier.fillMaxWidth().heightIn(min=76.dp),label={Text("새 통화방 이름")},singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                DriveButton("− 정원",{capacity--},enabled=capacity>2,secondary=true)
                Text("${capacity}명",fontWeight=FontWeight.Bold)
                DriveButton("+ 정원",{capacity++},enabled=capacity<8,secondary=true)
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Switch(publicRoom,{publicRoom=it},Modifier.sizeIn(minWidth=76.dp,minHeight=76.dp));Spacer(Modifier.width(12.dp));Text("검색에 공개")
            }
            Text("방의 기준 목적지는 생성 시 고정됩니다.",style=MaterialTheme.typography.bodyMedium)
        }
    },confirmButton={DriveButton("통화방 생성",{showCreate=false;onCreate(name,publicRoom,capacity)},enabled=enabled&&name.isNotBlank())},
        dismissButton={DriveButton("취소",{showCreate=false},secondary=true)})
    if(showInvite&&settingsAllowed)AlertDialog(onDismissRequest={showInvite=false},title={Text("초대받은 방 참여")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("공유받은 방 ID를 입력하세요. 비공개 방도 ID로 참여할 수 있습니다.")
            OutlinedTextField(invitation,{invitation=it},Modifier.fillMaxWidth().heightIn(min=76.dp),label={Text("초대받은 방 ID")},singleLine=true)
        }
    },confirmButton={DriveButton("방 ID로 참여",{showInvite=false;onJoin(invitation.trim())},enabled=enabled&&invitation.isNotBlank())},
        dismissButton={DriveButton("취소",{showInvite=false},secondary=true)})
    if(showInfo&&settingsAllowed&&room!=null){
        val context=LocalContext.current
        AlertDialog(onDismissRequest={showInfo=false},title={Text("통화방 정보")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(room.name);Text("공유 방 ID · ${room.id}")
                val expiry=runCatching{Instant.parse(room.expiresAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("확인 중")
                Text("방 만료 · $expiry")
            }
        },confirmButton={DriveButton("초대 ID 복사",{
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RouteMate room",room.id));showInfo=false
        })},dismissButton={DriveButton("닫기",{showInfo=false},secondary=true)})
    }
    if(showEnd)AlertDialog(onDismissRequest={showEnd=false},title={Text("운행을 종료할까요?")},text={Text("통화방에서 나가고 현재 통화도 종료합니다.")},
        confirmButton={DriveButton("운행 종료 확인",{showEnd=false;onEnd()},enabled=enabled)},dismissButton={DriveButton("계속 이용",{showEnd=false},secondary=true)})
}

private fun roomError(error:RoomError)=when(error){
    RoomError.NETWORK->"연결을 확인하고 통화방 상태를 다시 확인하세요."
    RoomError.UNAUTHORIZED->"계정 확인이 필요합니다. 정차 후 계정 관리에서 갱신·복구하세요."
    RoomError.TRIP_REQUIRED->"활성 운행이 없습니다. 목적지를 먼저 등록하세요."
    RoomError.ROOM_UNAVAILABLE->"종료되었거나 만료된 방입니다. 다시 검색하세요."
    RoomError.ROOM_FULL->"방이 가득 찼습니다. 다른 방을 선택하세요."
    RoomError.ALREADY_IN_ROOM->"기존 통화방에서 먼저 퇴장하세요."
    RoomError.NOT_IN_ROOM->"참가 상태가 만료됐습니다. 상태 확인 후 다시 참여하세요."
    RoomError.VOICE_UNAVAILABLE->"음성 서버에 연결할 수 없습니다. 잠시 후 다시 연결하세요."
    RoomError.CONFIGURATION->"서버 연결을 설정해야 합니다."
    RoomError.VEHICLE_UNAVAILABLE->"현재 차량 VIN을 확인할 수 없습니다."
    RoomError.MOCK_VEHICLE_DISABLED->"이 서버에서는 샘플 차량을 사용할 수 없습니다."
    RoomError.INVALID_REQUEST->"방 이름·정원·초대 ID를 확인하세요."
    RoomError.TRIP_CHANGED->"운행 정보가 변경됐습니다. 최신 목적지로 다시 확인하세요."
    else->"요청을 완료하지 못했습니다. 상태를 확인하고 다시 시도하세요."
}
