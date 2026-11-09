package com.routemate.app

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.routemate.app.design.*
import com.routemate.app.prototype.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.infrastructure.MockDestinationProvider
import com.routemate.app.destination.infrastructure.PleosDestinationProvider
import com.routemate.app.destination.presentation.DestinationScreen
import com.routemate.app.destination.presentation.DestinationViewModel
import com.routemate.app.destination.presentation.DestinationState
import com.routemate.app.trip.application.RegisterTrip
import com.routemate.app.trip.infrastructure.HttpTripRegistration
import com.routemate.app.trip.presentation.TripViewModel
import com.routemate.app.trip.presentation.TripRegistrationPanel
import com.routemate.app.auth.presentation.VehicleAccountActivity
import com.routemate.app.room.application.ManageRooms
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.room.presentation.*
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall

class MainActivity : ComponentActivity() {
    private var roomsModel: RoomViewModel?=null
    private var clearParkedMode:(()->Unit)?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prototype=BuildConfig.PROTOTYPE_DEMO
            val serverConfigured=prototype||BuildConfig.BACKEND_URL.isNotBlank()
            var settingsAllowed by remember{mutableStateOf(false)}
            var settingsPrompt by remember{mutableStateOf(false)}
            clearParkedMode={settingsAllowed=false}
            val demo=remember{if(prototype)PrototypeSession() else null}
            val registration:com.routemate.app.trip.domain.TripRegistration=remember{
                demo?:HttpTripRegistration(BuildConfig.BACKEND_URL,{vehicle->vehicleStore(applicationContext,vehicle)},
                    vehicleProvider(applicationContext),allowLocalHttp=BuildConfig.DEBUG)
            }
            val roomAccess:com.routemate.app.room.domain.RoomAccess=remember{
                demo?:HttpRoomAccess(BuildConfig.BACKEND_URL,{vehicle->vehicleStore(applicationContext,vehicle)},
                    vehicleProvider(applicationContext),allowLocalHttp=BuildConfig.DEBUG)
            }
            val voice:com.routemate.app.voice.domain.VoiceCall=remember{
                if(prototype)PrototypeVoiceCall() else LiveKitVoiceCall(applicationContext)
            }
            val model: DestinationViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val provider = if (BuildConfig.USE_PLEOS_DESTINATION) {
                        PleosDestinationProvider(applicationContext)
                    } else {
                        MockDestinationProvider()
                    }
                    DestinationViewModel(LoadDestination(provider))
                }
            })
            val trip: TripViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    TripViewModel(RegisterTrip(registration))
                }
            })
            val destinationState = model.state.collectAsStateWithLifecycle().value
            val rooms: RoomViewModel = viewModel(factory=viewModelFactory {
                initializer { RoomViewModel(ManageRooms(roomAccess),voice,registration) }
            })
            roomsModel=rooms
            val roomState=rooms.state.collectAsStateWithLifecycle().value
            val tripState=trip.state.collectAsStateWithLifecycle().value
            val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){rooms.connect(it)}
            val destination = (destinationState as? DestinationState.Loaded)?.destination
            LaunchedEffect(destination) { trip.destinationChanged(destination) }
            LaunchedEffect(destinationState) {
                if(destinationState is DestinationState.Loaded || destinationState==DestinationState.Empty){
                    rooms.destinationChanged(destination)
                    if(serverConfigured)rooms.syncDestination()
                }
            }
            LaunchedEffect(tripState) { if(tripState is com.routemate.app.trip.presentation.TripRegistrationState.Registered)rooms.refresh() }
            LaunchedEffect(Unit) { if(serverConfigured)rooms.foreground() }
            DestinationScreen(
                destinationState,
                model::refresh,
                BuildConfig.USE_PLEOS_DESTINATION,
                onVoicePoc = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "com.routemate.app.voice.presentation.VoicePocActivity"))
                }) else null,
                onAccount = { startActivity(Intent(this, VehicleAccountActivity::class.java)) },
                prototype=prototype,radiusKm=roomState.radiusKm,settingsAllowed=settingsAllowed,onOpenSettings={settingsPrompt=true},
                tripContent = {
                    TripRegistrationPanel(tripState,
                        destination != null,serverConfigured, BuildConfig.USE_PLEOS_DESTINATION,
                        usePleosVehicle = BuildConfig.USE_PLEOS_VEHICLE) {
                        trip.destinationChanged(destination)
                        trip.submit()
                    }
                },
                roomContent={
                    CarRoomPanel(roomState,serverConfigured,settingsAllowed,prototype,{settingsPrompt=true},rooms::refresh,rooms::search,rooms::create,
                        rooms::join,rooms::leave,{rooms.endTrip();trip.clearRegistration()},{
                            if(prototype||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)rooms.connect(true)
                            else microphone.launch(Manifest.permission.RECORD_AUDIO)
                        },rooms::toggleMicrophone)
                },
                overlayContent={
                    if(settingsPrompt)AlertDialog(onDismissRequest={settingsPrompt=false},title={Text("정차 후 설정을 이용하세요")},
                        text={Text("이 프로토타입은 실제 주행 상태를 감지하지 않습니다. 안전하게 정차한 뒤 방 생성·초대 입력·계정 관리를 이용하세요.")},
                        confirmButton={DriveButton("정차 후 설정 열기",{settingsAllowed=true;settingsPrompt=false})},
                        dismissButton={DriveButton("돌아가기",{settingsPrompt=false},secondary=true)})
                },
            )
        }
    }
    override fun onStart(){super.onStart();if(BuildConfig.PROTOTYPE_DEMO||BuildConfig.BACKEND_URL.isNotBlank())roomsModel?.foreground()}
    override fun onStop(){clearParkedMode?.invoke();roomsModel?.background();super.onStop()}
}
