package com.routemate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.auth.presentation.VehicleAccountActivity
import com.routemate.app.design.DriveButton
import com.routemate.app.destination.application.*
import com.routemate.app.destination.infrastructure.*
import com.routemate.app.destination.presentation.*
import com.routemate.app.prototype.*
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.room.presentation.*
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall

class MainActivity : ComponentActivity() {
    private var roomsModel: RoomViewModel? = null
    private var clearParkedMode: (() -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prototype = BuildConfig.PROTOTYPE_DEMO
            val configured = prototype || BuildConfig.BACKEND_URL.isNotBlank()
            var parked by remember { mutableStateOf(false) }
            var pendingParked by remember { mutableStateOf<(() -> Unit)?>(null) }
            var showSearch by remember { mutableStateOf(false) }
            var connectAfterJoin by remember { mutableStateOf(false) }
            val requireParked: (() -> Unit) -> Unit = { action -> if (parked) action() else pendingParked = action }
            val demo = remember { if (prototype) PrototypeSession() else null }
            val registration = remember { demo ?: HttpTripRegistration(BuildConfig.BACKEND_URL,
                { vehicle -> vehicleStore(applicationContext, vehicle) }, vehicleProvider(applicationContext), allowLocalHttp = BuildConfig.DEBUG) }
            val roomAccess = remember { demo ?: HttpRoomAccess(BuildConfig.BACKEND_URL,
                { vehicle -> vehicleStore(applicationContext, vehicle) }, vehicleProvider(applicationContext), allowLocalHttp = BuildConfig.DEBUG) }
            val voice = remember { if (prototype) PrototypeVoiceCall() else LiveKitVoiceCall(applicationContext) }
            val mock = remember { if (!BuildConfig.USE_PLEOS_DESTINATION) savedMockDestination(applicationContext) else null }
            val destination: DestinationViewModel = viewModel(factory = viewModelFactory { initializer {
                DestinationViewModel(LoadDestination(mock ?: PleosDestinationProvider(applicationContext)), mock?.let { it::select })
            } })
            val search: DestinationSearchViewModel = viewModel(factory = viewModelFactory { initializer {
                DestinationSearchViewModel(SearchDestinations(GeocodedDestinationSearch(AndroidPlaceSearchClient(applicationContext))))
            } })
            val rooms: RoomViewModel = viewModel(factory = viewModelFactory { initializer { RoomViewModel(ManageRooms(roomAccess), voice, registration) } })
            roomsModel = rooms
            clearParkedMode = { parked = false; pendingParked = null; showSearch = false; connectAfterJoin = false; search.cancel() }
            val destinationState by destination.state.collectAsStateWithLifecycle()
            val searchState by search.state.collectAsStateWithLifecycle()
            val state by rooms.state.collectAsStateWithLifecycle()
            val selectedDestination = (destinationState as? DestinationState.Loaded)?.destination
            val destinationReady = state.snapshot.tripId != null && selectedDestination == state.snapshot.destination
            val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rooms.connect(it) }
            val connect: () -> Unit = {
                if (prototype || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) rooms.connect(true)
                else microphone.launch(Manifest.permission.RECORD_AUDIO)
            }
            LaunchedEffect(destinationState) {
                when (val value = destinationState) {
                    is DestinationState.Loaded -> if (configured) rooms.startDestination(value.destination)
                    DestinationState.Empty -> { rooms.destinationChanged(null); if (configured) rooms.syncDestination() }
                    else -> Unit
                }
            }
            LaunchedEffect(state.snapshot.room?.id, state.busy, state.error) {
                if (connectAfterJoin && !state.busy) {
                    if (state.error != null) connectAfterJoin = false
                    else if (state.snapshot.room != null) { connectAfterJoin = false; connect() }
                }
            }
            LaunchedEffect(Unit) { if (configured) rooms.foreground() }
            val editDestination: () -> Unit = {
                if (BuildConfig.USE_PLEOS_DESTINATION) {
                    val target = (destinationState as? DestinationState.Loaded)?.destination
                    if (target != null && state.error != null) rooms.registerDestination(target) else destination.refresh()
                } else requireParked { search.query("", immediate = true); showSearch = true }
            }
            DestinationScreen(destinationState, BuildConfig.USE_PLEOS_DESTINATION, destinationReady, state.busy,
                configured, (!destinationReady && state.error != null) || destinationState is DestinationState.Error, editDestination,
                onAccount = { requireParked { startActivity(Intent(this, VehicleAccountActivity::class.java)) } },
                onVoicePoc = if (BuildConfig.DEBUG) ({ requireParked { startActivity(Intent().setClassName(this, "com.routemate.app.voice.presentation.VoicePocActivity")) } }) else null,
                prototype = prototype, roomContent = {
                    CarRoomPanel(state, configured, prototype, destinationReady, parked, requireParked, rooms::refresh, rooms::search,
                        { name, publicRoom, capacity -> connectAfterJoin = true; rooms.create(name, publicRoom, capacity) },
                        { id -> connectAfterJoin = true; rooms.join(id) }, { connectAfterJoin = false; rooms.leave() },
                        { connectAfterJoin = false; rooms.endTrip() }, connect, rooms::toggleMicrophone)
                }, overlayContent = {
                    if (showSearch) DestinationSearchDialog(searchState, { search.query(it) }, { search.query(searchState.query, immediate = true) },
                        { place -> showSearch = false; search.cancel(); destination.select(place.destination); if (configured) rooms.registerDestination(place.destination) },
                        { showSearch = false; search.cancel() })
                    if (pendingParked != null) AlertDialog(onDismissRequest = { pendingParked = null }, title = { Text("정차 후 이용해 주세요") },
                        text = { Text("목적지 검색과 입력은 정차 후 이용해 주세요. 주행 상태 자동 감지는 아직 연결되지 않았습니다.") },
                        confirmButton = { DriveButton("정차했어요", { val action = pendingParked; pendingParked = null; parked = true; action?.invoke() }) },
                        dismissButton = { TextButton({ pendingParked = null }) { Text("취소") } })
                })
        }
    }
    override fun onStart() { super.onStart(); if (BuildConfig.PROTOTYPE_DEMO || BuildConfig.BACKEND_URL.isNotBlank()) roomsModel?.foreground() }
    override fun onStop() {
        // A density/orientation change must not disconnect an otherwise healthy foreground call.
        if (!isChangingConfigurations) { clearParkedMode?.invoke(); roomsModel?.background() }
        super.onStop()
    }
}
