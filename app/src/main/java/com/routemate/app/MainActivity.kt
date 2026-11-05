package com.routemate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routemate.app.destination.application.LoadDestination
import com.routemate.app.destination.infrastructure.MockDestinationProvider
import com.routemate.app.destination.infrastructure.PleosDestinationProvider
import com.routemate.app.destination.presentation.DestinationScreen
import com.routemate.app.destination.presentation.DestinationViewModel

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
            DestinationScreen(model.state.collectAsStateWithLifecycle().value, model::refresh, BuildConfig.USE_PLEOS_DESTINATION)
        }
    }
}
