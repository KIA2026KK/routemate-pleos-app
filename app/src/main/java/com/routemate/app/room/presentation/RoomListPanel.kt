package com.routemate.app.room.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routemate.app.design.*
import com.routemate.app.room.domain.*

@Composable fun RoomListPanel(state:RoomUiState,configured:Boolean,destinationReady:Boolean,settingsAllowed:Boolean,
    onParked:(()->Unit)->Unit,onRefresh:()->Unit,onSearch:(Int)->Unit,onCreate:(String,Boolean,Int)->Unit,
    onJoin:(String)->Unit,onOpenCurrent:()->Unit,onEnd:()->Unit){
    var create by rememberSaveable{mutableStateOf(false)};var invite by rememberSaveable{mutableStateOf(false)}
    var end by rememberSaveable{mutableStateOf(false)};var menu by remember{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")}
    var name by rememberSaveable{mutableStateOf("")};var code by rememberSaveable{mutableStateOf("")}
    var capacity by rememberSaveable{mutableIntStateOf(4)};var publicRoom by rememberSaveable{mutableStateOf(true)}
    var advanced by remember{mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val active=state.snapshot.tripId!=null
    val enabled=configured&&!state.busy
    val canFind=enabled&&active&&destinationReady
    LaunchedEffect(settingsAllowed){if(!settingsAllowed){create=false;invite=false;search=false;advanced=false}}
    VehicleCard(Modifier.fillMaxWidth()){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("가까운 동행",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            RouteIconButton("통화방 새로고침",RouteIcons.Refresh,onRefresh,enabled&&active)
            Box{
                RouteIconButton("통화방 메뉴",RouteIcons.More,{menu=true})
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem({Text("초대 코드로 참여")},{menu=false;onParked{invite=true}},Modifier.heightIn(min=76.dp),enabled=canFind)
                    DropdownMenuItem({Text("운행 종료")},{menu=false;end=true},Modifier.heightIn(min=76.dp),enabled=enabled&&active)
                }
            }
        }
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(roomError(it),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text("목적지 주변",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            RoomPolicy.radii.forEach{radius->FilterChip(state.radiusKm==radius,{onSearch(radius)},label={Text("${radius}km")},
                modifier=Modifier.sizeIn(minWidth=76.dp,minHeight=76.dp).semantics{contentDescription="${radius}km 방 검색"},enabled=canFind)}
        }
        Surface(onClick={onParked{search=true}},modifier=Modifier.fillMaxWidth().heightIn(min=76.dp).semantics{contentDescription="방 검색 열기"},
            enabled=canFind,color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.large){
            Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(RouteIcons.Search,null);Text(query.ifBlank{"방 이름 검색"},style=MaterialTheme.typography.bodyMedium)
            }
        }
        val results=state.nearby.filter{it.room.name.contains(query,ignoreCase=true)}
        if(!active&&!state.busy)Text("목적지를 선택하면 동행을 찾습니다.",style=MaterialTheme.typography.bodyMedium)
        else if(results.isEmpty()&&!state.busy)Text(if(query.isBlank())"가까운 동행이 아직 없어요" else "이 반경에서 이름이 맞는 방이 없어요",style=MaterialTheme.typography.bodyMedium)
        results.forEach{nearby->
            val joined=nearby.room.id==state.snapshot.room?.id
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.large){
                Row(Modifier.fillMaxWidth().heightIn(min=88.dp).semantics{contentDescription="${nearby.room.name} ${if(joined)"열기" else "참여"}"}
                    .clickable(canFind){if(joined)onOpenCurrent() else onJoin(nearby.room.id)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(nearby.room.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(if(joined)"참여 중" else "약 ${nearby.distanceMeters/1000}km · ${nearby.room.participants}/${nearby.room.capacity}명",style=MaterialTheme.typography.bodyMedium)
                    };Icon(RouteIcons.Arrow,null)
                }
            }
        }
        if(state.snapshot.room==null)DriveButton("방 만들기",{onParked{name="${state.snapshot.destination?.name?:"목적지"} 동행".take(60);create=true}},Modifier.fillMaxWidth(),enabled=canFind)
    }
    if(search)AlertDialog(onDismissRequest={search=false},title={Text("가까운 방 검색")},text={
        OutlinedTextField(query,{query=it.take(60)},Modifier.fillMaxWidth().heightIn(min=76.dp),label={Text("검색할 방 이름")},singleLine=true)
    },confirmButton={DriveButton("검색",{keyboard?.hide();search=false})},dismissButton={TextButton({query="";search=false}){Text("전체 보기")}})
    if(create)AlertDialog(onDismissRequest={create=false},title={Text("방 만들기")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(name,{name=it.take(60)},Modifier.fillMaxWidth().heightIn(min=76.dp),label={Text("방 이름")},singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}))
            TextButton({advanced=!advanced},Modifier.heightIn(min=64.dp)){Text(if(advanced)"옵션 접기" else "${capacity}명 · ${if(publicRoom)"공개" else "비공개"} 방 변경")}
            if(advanced){
                Row(verticalAlignment=Alignment.CenterVertically){RouteIconButton("정원 줄이기",RouteIcons.Close,{capacity--},capacity>2);Text("${capacity}명",Modifier.weight(1f));RouteIconButton("정원 늘리기",RouteIcons.Plus,{capacity++},capacity<8)}
                Row(verticalAlignment=Alignment.CenterVertically){Text("공개 방",Modifier.weight(1f));Switch(publicRoom,{publicRoom=it})}
            }
        }
    },confirmButton={DriveButton("만들고 참여",{create=false;onCreate(name,publicRoom,capacity)},enabled=enabled&&name.isNotBlank())},dismissButton={TextButton({create=false}){Text("취소")}})
    if(invite)AlertDialog(onDismissRequest={invite=false},title={Text("초대 코드로 참여")},text={
        OutlinedTextField(code,{code=it.take(64)},Modifier.fillMaxWidth().heightIn(min=76.dp),label={Text("초대 코드 또는 방 ID")},singleLine=true)
    },confirmButton={DriveButton("참여",{invite=false;onJoin(code.trim())},enabled=enabled&&code.isNotBlank())},dismissButton={TextButton({invite=false}){Text("취소")}})
    if(end)AlertDialog(onDismissRequest={end=false},title={Text("운행을 종료할까요?")},text={Text("현재 통화와 방 참여를 종료합니다.")},
        confirmButton={DriveButton("운행 종료 확인",{end=false;onEnd()})},dismissButton={TextButton({end=false}){Text("취소")}})
}

internal fun roomError(error:RoomError)=when(error){
    RoomError.NETWORK->"연결을 확인하고 다시 시도해 주세요."
    RoomError.SERVER->"서버 응답이 지연되고 있어요. 다시 시도해 주세요."
    RoomError.UNAUTHORIZED->"더보기에서 계정을 확인해 주세요."
    RoomError.TRIP_REQUIRED->"목적지를 선택해 주세요."
    RoomError.ROOM_UNAVAILABLE->"종료된 방이거나 초대 코드가 올바르지 않습니다."
    RoomError.ROOM_FULL->"방이 가득 찼습니다. 현재 방 참여는 유지됩니다."
    RoomError.ALREADY_IN_ROOM->"현재 참여 중인 방이 있습니다."
    RoomError.NOT_IN_ROOM->"참가가 만료됐습니다. 다시 참여해 주세요."
    RoomError.VOICE_UNAVAILABLE->"음성 서버 연결을 다시 시도해 주세요."
    RoomError.CONFIGURATION->"서버 연결이 설정되지 않았습니다."
    RoomError.VEHICLE_UNAVAILABLE->"차량 VIN을 확인할 수 없습니다."
    RoomError.MOCK_VEHICLE_DISABLED->"샘플 차량을 사용할 수 없는 서버입니다."
    RoomError.INVALID_REQUEST->"입력한 코드와 설정을 확인해 주세요."
    RoomError.TRIP_CHANGED->"목적지를 다시 확인해 주세요."
}
