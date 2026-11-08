package com.routemate.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.routemate.app.trip.infrastructure.KeystoreSessionStore
import com.routemate.app.trip.presentation.TripViewModel
import com.routemate.app.trip.presentation.TripRegistrationPanel

class MainActivity : ComponentActivity() {
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
                initializer { TripViewModel(RegisterTrip(HttpTripRegistration(BuildConfig.BACKEND_URL,
                    KeystoreSessionStore(applicationContext, BuildConfig.BACKEND_URL), allowLocalHttp = BuildConfig.DEBUG))) }
            })
            val destinationState = model.state.collectAsStateWithLifecycle().value
            val destination = (destinationState as? DestinationState.Loaded)?.destination
            LaunchedEffect(destination) { trip.destinationChanged(destination) }
            DestinationScreen(
                destinationState,
                model::refresh,
                BuildConfig.USE_PLEOS_DESTINATION,
                onVoicePoc = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "com.routemate.app.voice.presentation.VoicePocActivity"))
                }) else null,
                registrationContent = {
                    TripRegistrationPanel(trip.state.collectAsStateWithLifecycle().value,
                        destination != null, BuildConfig.BACKEND_URL.isNotBlank(), BuildConfig.USE_PLEOS_DESTINATION) {
                        trip.destinationChanged(destination)
                        trip.submit()
                    }
                },
            )
        }
    }
}
