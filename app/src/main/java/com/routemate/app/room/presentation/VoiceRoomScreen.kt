package com.routemate.app.room.presentation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routemate.app.design.*
import com.routemate.app.room.application.CallDiagnostic
import com.routemate.app.voice.domain.*

@Composable fun ActiveRoomBanner(state:RoomUiState,onOpen:()->Unit){
    if(state.leaving)return
    val room=state.snapshot.room?:return
    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).semantics{contentDescription="현재 통화방 열기"}.clickable(onClick=onOpen),
        color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.large){
        Row(Modifier.heightIn(min=76.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Icon(RouteIcons.People,null,tint=MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f)){
                Text(room.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(voiceLabel(state.voice.status,state.callActive),style=MaterialTheme.typography.bodyMedium)
            }
            Icon(RouteIcons.Arrow,null)
        }
    }
}

internal fun voiceLabel(status:VoiceStatus,active:Boolean)=when(status){
    VoiceStatus.CONNECTED->"음성 연결 완료"
    VoiceStatus.CONNECTING->"음성 연결 중"
    VoiceStatus.RECONNECTING->"연결 복구 중"
    else->if(active)"연결 확인 중" else "방 참여 중 · 다시 연결"
}

@Composable fun VoiceRoomScreen(state:RoomUiState,prototype:Boolean,diagnostics:List<CallDiagnostic>,
    onHome:()->Unit,onMic:()->Unit,onLeave:()->Unit,onConnect:()->Unit){
    val room=state.snapshot.room?:return
    var menu by remember{mutableStateOf(false)}
    var invite by remember{mutableStateOf(false)}
    var diagnostic by remember{mutableStateOf(false)}
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            RouteIconButton("홈으로",RouteIcons.Back,onHome)
            Column(Modifier.weight(1f)){
                Text(room.name,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(prototype)"체험 모드 · 실제 음성 없음" else voiceLabel(state.voice.status,state.callActive),style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box{
                RouteIconButton("통화방 메뉴",RouteIcons.More,{menu=true})
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem({Text("초대 코드")},{menu=false;invite=true},Modifier.heightIn(min=76.dp))
                    DropdownMenuItem({Text("연결 정보")},{menu=false;diagnostic=true},Modifier.heightIn(min=76.dp))
                }
            }
        }
        state.error?.let{Text(if(it in setOf(com.routemate.app.room.domain.RoomError.NETWORK,com.routemate.app.room.domain.RoomError.SERVER)&&state.callActive)
            "연결 상태를 확인하고 있어요" else roomError(it),Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
        val members=state.voice.members.ifEmpty{
            state.voice.participants.mapIndexed{index,id->VoiceParticipant(id,local=index==0,microphoneEnabled=if(index==0)state.voice.microphoneEnabled else false)}
                .ifEmpty{listOf(VoiceParticipant("connecting-self",local=true))}
        }
        Text("${members.size}명 음성 연결 · 정원 ${room.capacity}명",Modifier.padding(horizontal=12.dp,vertical=12.dp),
            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Adaptive(140.dp),Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(vertical=16.dp)){
            itemsIndexed(members,key={_,person->person.id}){index,person->
                SpeakerTile(person,if(person.local)"나" else person.name.ifBlank{"동행 ${members.take(index).count{!it.local}+1}"})
            }
        }
        if(!state.callActive&&state.voice.status!=VoiceStatus.CONNECTED)
            DriveButton(if(state.voice.error==VoiceFailure.PERMISSION)"마이크 허용" else "다시 연결",onConnect,
                Modifier.fillMaxWidth().semantics{contentDescription="음성 다시 연결"},enabled=!state.busy)
        Row(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalArrangement=Arrangement.SpaceEvenly){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                FilledIconButton(onMic,Modifier.size(88.dp),enabled=state.voice.status==VoiceStatus.CONNECTED){
                    Icon(if(state.voice.microphoneEnabled)RouteIcons.Mic else RouteIcons.MicOff,
                        if(state.voice.microphoneEnabled)"마이크 끄기" else "마이크 켜기",Modifier.size(36.dp))
                }
                Text(if(state.voice.microphoneEnabled)"마이크 켜짐" else "마이크 꺼짐",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                OutlinedIconButton({invite=true},Modifier.size(88.dp)){Icon(RouteIcons.Plus,"초대 코드 보기",Modifier.size(32.dp))}
                Text("초대",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                FilledIconButton(onLeave,Modifier.size(88.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.error,
                    contentColor=MaterialTheme.colorScheme.onError)) {Icon(RouteIcons.Phone,"통화방 퇴장",Modifier.size(34.dp))}
                Text("나가기",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if(invite)AlertDialog(onDismissRequest={invite=false},title={Text("초대 코드")},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text(room.name)
            Text(room.inviteCode.ifBlank{room.id},style=MaterialTheme.typography.headlineMedium)
        }
    },confirmButton={DriveButton("코드 복사",{
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RouteMate 초대 코드",room.inviteCode.ifBlank{room.id}));invite=false
    })},dismissButton={TextButton({invite=false}){Text("닫기")}})
    if(diagnostic)AlertDialog(onDismissRequest={diagnostic=false},title={Text("연결 정보")},text={
        Text(diagnostics.takeLast(6).joinToString("\n"){"${it.elapsedMs/1000}s · ${it.code}"}.ifBlank{"기록된 연결 이벤트가 없습니다."},style=MaterialTheme.typography.bodyMedium)
    },confirmButton={DriveButton("진단 복사",{
        val report="RouteMate ${com.routemate.app.BuildConfig.VERSION_NAME}\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}\n"+
            diagnostics.joinToString("\n"){"${it.elapsedMs/1000}s ${it.code}"}
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RouteMate 연결 진단",report));diagnostic=false
    })},dismissButton={TextButton({diagnostic=false}){Text("닫기")}})
}

@Composable internal fun SpeakerTile(person:VoiceParticipant,label:String){
    val color by animateColorAsState(if(person.speaking)Color(0xFF55D6A2) else MaterialTheme.colorScheme.outline.copy(alpha=.35f),label="speaker-ring")
    val scale by animateFloatAsState(if(person.speaking)1.03f+person.audioLevel.coerceIn(0f,1f)*.08f else 1f,label="speaker-level")
    val description=when{person.speaking->"말하는 중";!person.microphoneEnabled->"음소거";else->"듣는 중"}
    Column(Modifier.fillMaxWidth().heightIn(min=156.dp).semantics(mergeDescendants=true){contentDescription=label;stateDescription=description},
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(116.dp),contentAlignment=Alignment.Center){
            Surface(Modifier.size(96.dp).graphicsLayer{scaleX=scale;scaleY=scale},shape=CircleShape,
                color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(if(person.speaking)3.dp else 1.dp,color)){
                Box(contentAlignment=Alignment.Center){Icon(RouteIcons.People,null,Modifier.size(38.dp))}
            }
            if(!person.microphoneEnabled)Icon(RouteIcons.MicOff,null,Modifier.align(Alignment.BottomEnd).size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=if(person.speaking)color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
