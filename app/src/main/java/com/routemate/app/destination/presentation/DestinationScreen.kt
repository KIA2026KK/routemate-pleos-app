package com.routemate.app.destination.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routemate.app.R
import com.routemate.app.destination.domain.DestinationFailure

@Composable
fun DestinationScreen(state: DestinationState, onRefresh: () -> Unit, usePleos: Boolean = false) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("RouteMate", style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.destination_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (usePleos) R.string.pleos_notice else R.string.mock_notice))
                when (state) {
                    DestinationState.Empty -> Text(stringResource(R.string.empty_destination))
                    DestinationState.Loading -> Text(stringResource(R.string.loading_destination))
                    is DestinationState.Error -> Text(stringResource(when (state.reason) {
                        DestinationFailure.CONNECTION -> R.string.destination_connection_error
                        DestinationFailure.TIMEOUT -> R.string.destination_timeout
                        DestinationFailure.PERMISSION -> R.string.destination_permission_error
                        DestinationFailure.INVALID_DATA -> R.string.destination_invalid_data
                        DestinationFailure.UNKNOWN -> R.string.destination_error
                    }))
                    is DestinationState.Loaded -> {
                        Text(state.destination.name, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.destination_coordinates, state.destination.latitude, state.destination.longitude))
                    }
                }
                Button(onClick = onRefresh, enabled = state != DestinationState.Loading, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(if (usePleos) R.string.load_pleos_destination else R.string.load_destination))
                }
            }
        }
    }
}
