package com.routemate.app.destination.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routemate.app.R

@Composable
fun DestinationScreen(state: DestinationState, onRefresh: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("RouteMate", style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.destination_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.mock_notice))
                when (state) {
                    DestinationState.Empty -> Text(stringResource(R.string.empty_destination))
                    DestinationState.Loading -> Text(stringResource(R.string.loading_destination))
                    DestinationState.Error -> Text(stringResource(R.string.destination_error))
                    is DestinationState.Loaded -> Text(state.destination.name, style = MaterialTheme.typography.titleLarge)
                }
                Button(onClick = onRefresh, enabled = state != DestinationState.Loading, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.load_destination))
                }
            }
        }
    }
}
