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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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
                    TripViewModel(RegisterTrip(HttpTripRegistration(BuildConfig.BACKEND_URL,
                        { vehicle -> vehicleStore(applicationContext, vehicle) },
                        vehicleProvider(applicationContext), allowLocalHttp = BuildConfig.DEBUG)))
                }
            })
            val destinationState = model.state.collectAsStateWithLifecycle().value
            val rooms: RoomViewModel = viewModel(factory=viewModelFactory {
                initializer { RoomViewModel(ManageRooms(HttpRoomAccess(BuildConfig.BACKEND_URL,
                    {vehicle->vehicleStore(applicationContext,vehicle)},vehicleProvider(applicationContext),allowLocalHttp=BuildConfig.DEBUG)),
                    LiveKitVoiceCall(applicationContext),HttpTripRegistration(BuildConfig.BACKEND_URL,
                    {vehicle->vehicleStore(applicationContext,vehicle)},vehicleProvider(applicationContext),allowLocalHttp=BuildConfig.DEBUG)) }
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
                    if(BuildConfig.BACKEND_URL.isNotBlank())rooms.syncDestination()
                }
            }
            LaunchedEffect(tripState) { if(tripState is com.routemate.app.trip.presentation.TripRegistrationState.Registered)rooms.refresh() }
            LaunchedEffect(Unit) { if(BuildConfig.BACKEND_URL.isNotBlank())rooms.foreground() }
            DestinationScreen(
                destinationState,
                model::refresh,
                BuildConfig.USE_PLEOS_DESTINATION,
                onVoicePoc = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "com.routemate.app.voice.presentation.VoicePocActivity"))
                }) else null,
                onAccount = { startActivity(Intent(this, VehicleAccountActivity::class.java)) },
                registrationContent = {
                    TripRegistrationPanel(tripState,
                        destination != null, BuildConfig.BACKEND_URL.isNotBlank(), BuildConfig.USE_PLEOS_DESTINATION,
                        usePleosVehicle = BuildConfig.USE_PLEOS_VEHICLE) {
                        trip.destinationChanged(destination)
                        trip.submit()
                    }
                    RoomPanel(roomState,BuildConfig.BACKEND_URL.isNotBlank(),rooms::refresh,rooms::search,rooms::create,
                        rooms::join,rooms::leave,{rooms.endTrip();trip.clearRegistration()},{
                            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)rooms.connect(true)
                            else microphone.launch(Manifest.permission.RECORD_AUDIO)
                        },rooms::toggleMicrophone)
                },
            )
        }
    }
    override fun onStart(){super.onStart();if(BuildConfig.BACKEND_URL.isNotBlank())roomsModel?.foreground()}
    override fun onStop(){roomsModel?.background();super.onStop()}
}
