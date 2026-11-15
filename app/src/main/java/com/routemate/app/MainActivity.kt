package com.routemate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.auth.presentation.VehicleAccountActivity
import com.routemate.app.design.DriveButton
import com.routemate.app.destination.application.*
import com.routemate.app.destination.infrastructure.*
import com.routemate.app.destination.presentation.*
import com.routemate.app.room.presentation.*
import com.routemate.app.voice.domain.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    private var roomsModel: RoomViewModel? = null
    private var clearParkedMode: (() -> Unit)? = null
    private val callRequests=MutableStateFlow(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph=(application as RouteMateApplication).graph
        setContent {
            val prototype = BuildConfig.PROTOTYPE_DEMO
            val configured = prototype || BuildConfig.BACKEND_URL.isNotBlank()
            var parked by remember { mutableStateOf(false) }
            var pendingParked by remember { mutableStateOf<(() -> Unit)?>(null) }
            var showSearch by remember { mutableStateOf(false) }
            var connectAfterJoin by remember { mutableStateOf(false) }
            var callScreen by rememberSaveable{mutableStateOf(intent.getBooleanExtra("showCall",false))}
            var pendingSwitch by rememberSaveable{mutableStateOf<String?>(null)}
            var notificationAsked by rememberSaveable{mutableStateOf(false)}
            val requireParked: (() -> Unit) -> Unit = { action -> if (parked) action() else pendingParked = action }
            val mock = remember { if (!BuildConfig.USE_PLEOS_DESTINATION) savedMockDestination(applicationContext) else null }
            val destination: DestinationViewModel = viewModel(factory = viewModelFactory { initializer {
                DestinationViewModel(LoadDestination(mock ?: PleosDestinationProvider(applicationContext)), mock?.let { it::select })
            } })
            val search: DestinationSearchViewModel = viewModel(factory = viewModelFactory { initializer {
                DestinationSearchViewModel(SearchDestinations(GeocodedDestinationSearch(AndroidPlaceSearchClient(applicationContext))))
            } })
            val rooms: RoomViewModel = viewModel(factory = viewModelFactory { initializer { RoomViewModel(graph.session) } })
            roomsModel = rooms
            clearParkedMode = { parked = false; pendingParked = null; showSearch = false; connectAfterJoin = false; search.cancel() }
            val destinationState by destination.state.collectAsStateWithLifecycle()
            val searchState by search.state.collectAsStateWithLifecycle()
            val visibleStates=remember(rooms,callScreen){rooms.state.map{value->
                if(callScreen)value else value.copy(voice=value.voice.copy(members=emptyList()))
            }.distinctUntilChanged()}
            val initialRoomState=remember(rooms){rooms.state.value}
            val state by visibleStates.collectAsStateWithLifecycle(initialValue=initialRoomState)
            val diagnostics by rooms.session.diagnostics.collectAsStateWithLifecycle()
            val request by callRequests.collectAsStateWithLifecycle()
            val selectedDestination = (destinationState as? DestinationState.Loaded)?.destination
            val destinationReady = state.snapshot.tripId != null && selectedDestination == state.snapshot.destination
            val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                rooms.connect(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
            }
            val connect: () -> Unit = {
                if(prototype)rooms.connect(true)
                else {
                    val missing=mutableListOf<String>()
                    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing+=Manifest.permission.RECORD_AUDIO
                    if(Build.VERSION.SDK_INT>=33&&!notificationAsked&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                        missing+=Manifest.permission.POST_NOTIFICATIONS;notificationAsked=true
                    }
                    if(missing.isEmpty())rooms.connect(true) else microphone.launch(missing.toTypedArray())
                }
            }
            val openCurrent:()->Unit={callScreen=true;if(!state.callActive&&!state.busy)connect()}
            val goHome:()->Unit={callScreen=false;if(destinationReady&&!state.busy)rooms.search(state.radiusKm)}
            BackHandler(callScreen){goHome()}
            LaunchedEffect(request){if(request>0)callScreen=true}
            LaunchedEffect(state.snapshot.room?.id){if(state.snapshot.room==null)callScreen=false}
            LaunchedEffect(destinationState) {
                when (val value = destinationState) {
                    is DestinationState.Loaded -> if (configured) rooms.startDestination(value.destination)
                    DestinationState.Empty -> { rooms.destinationChanged(null); if (configured) rooms.syncDestination() }
                    else -> Unit
                }
            }
            LaunchedEffect(connectAfterJoin,state.snapshot.room?.id, state.busy, state.error) {
                if (connectAfterJoin && !state.busy) {
                    if (state.error != null) connectAfterJoin = false
                    else if (state.snapshot.room != null) { connectAfterJoin = false; callScreen=true; connect() }
                }
            }
            LaunchedEffect(Unit) { if (configured) rooms.foreground() }
            val editDestination: () -> Unit = {
                if (BuildConfig.USE_PLEOS_DESTINATION) {
                    val target = (destinationState as? DestinationState.Loaded)?.destination
                    if (target != null && state.error != null) rooms.registerDestination(target) else destination.refresh()
                } else requireParked { search.query("", immediate = true); showSearch = true }
            }
            val homeState=remember(state.snapshot,state.nearby,state.radiusKm,state.busy,state.error,state.callActive){state.copy(voice=VoiceState(),reconnectAttempt=0)}
            val bannerState=remember(state.snapshot.room,state.voice.status,state.callActive,state.leaving){state.copy(voice=VoiceState(status=state.voice.status))}
            val requestJoin:(String)->Unit={target->
                val current=state.snapshot.room
                if(current!=null&&(current.id.equals(target,true)||current.inviteCode.isNotBlank()&&current.inviteCode.equals(target,true)))openCurrent()
                else if(current!=null)pendingSwitch=target
                else {connectAfterJoin=true;rooms.join(target)}
            }
            DestinationScreen(destinationState, BuildConfig.USE_PLEOS_DESTINATION, destinationReady, state.busy,
                configured, (!destinationReady && state.error != null) || destinationState is DestinationState.Error, editDestination,
                onAccount = { requireParked { startActivity(Intent(this, VehicleAccountActivity::class.java)) } },
                onVoicePoc = if (BuildConfig.DEBUG) ({ requireParked { startActivity(Intent().setClassName(this, "com.routemate.app.voice.presentation.VoicePocActivity")) } }) else null,
                prototype = prototype, roomContent = {
                    RoomListPanel(homeState, configured, destinationReady, parked, requireParked, rooms::refresh, rooms::search,
                        { name, publicRoom, capacity -> connectAfterJoin = true; rooms.create(name, publicRoom, capacity) },
                        requestJoin,openCurrent,{ connectAfterJoin=false;callScreen=false;rooms.endTrip() })
                }, overlayContent = {
                    if (showSearch) DestinationSearchDialog(searchState, { search.query(it) }, { search.query(searchState.query, immediate = true) },
                        { place -> showSearch = false; search.cancel(); destination.select(place.destination); if (configured) rooms.registerDestination(place.destination) },
                        { showSearch = false; search.cancel() })
                    if (pendingParked != null) AlertDialog(onDismissRequest = { pendingParked = null }, title = { Text("정차 후 이용해 주세요") },
                        text = { Text("목적지 검색과 입력은 정차 후 이용해 주세요. 주행 상태 자동 감지는 아직 연결되지 않았습니다.") },
                        confirmButton = { DriveButton("정차했어요", { val action = pendingParked; pendingParked = null; parked = true; action?.invoke() }) },
                        dismissButton = { TextButton({ pendingParked = null }) { Text("취소") } })
                    if(pendingSwitch!=null)AlertDialog(onDismissRequest={pendingSwitch=null},title={Text("통화방을 옮길까요?")},
                        text={Text("새 방에 참여하면 현재 방에서 나갑니다. 이동에 실패하면 현재 방을 유지합니다.")},
                        confirmButton={DriveButton("옮기고 참여",{val target=pendingSwitch;pendingSwitch=null;if(target!=null){connectAfterJoin=true;rooms.switchTo(target)}})},
                        dismissButton={TextButton({pendingSwitch=null}){Text("취소")}})
                },bannerContent={ActiveRoomBanner(bannerState,openCurrent)},
                callContent=if(callScreen&&state.snapshot.room!=null)({VoiceRoomScreen(state,prototype,diagnostics,goHome,rooms::toggleMicrophone,
                    {connectAfterJoin=false;callScreen=false;rooms.leave()},connect)})else null)
        }
    }
    override fun onStart() { super.onStart(); if (BuildConfig.PROTOTYPE_DEMO || BuildConfig.BACKEND_URL.isNotBlank()) roomsModel?.foreground() }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("showCall",false))callRequests.value++}
    override fun onStop() {
        // A density/orientation change must not disconnect an otherwise healthy foreground call.
        if (!isChangingConfigurations) { clearParkedMode?.invoke(); roomsModel?.background() }
        super.onStop()
    }
}
