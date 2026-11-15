package com.routemate.app.destination.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.routemate.app.design.*
import com.routemate.app.destination.domain.*

@Composable fun DestinationSearchDialog(state: DestinationSearchState, onQuery: (String) -> Unit,
    onSearch: () -> Unit, onSelect: (DestinationPlace) -> Unit, onDismiss: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); onSearch() }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth(.94f).heightIn(min = 300.dp, max = 620.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("목적지 선택", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    RouteIconButton("목적지 검색 닫기", RouteIcons.Close, onDismiss)
                }
                OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    label = { Text("장소명 또는 도로명") }, singleLine = true,
                    trailingIcon = { RouteIconButton("목적지 검색", RouteIcons.Search, submit) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }))
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(when (it) {
                    SearchFailure.UNAVAILABLE -> "이 기기에서는 주소 검색을 지원하지 않습니다. 검색어를 지우면 예시 목적지를 선택할 수 있어요."
                    SearchFailure.TIMEOUT -> "검색이 지연되고 있습니다. 다시 검색해 주세요."
                    SearchFailure.NETWORK -> "검색 연결을 확인하고 다시 시도해 주세요."
                }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (!state.loading && state.error == null && state.places.isEmpty()) Text("검색 결과가 없습니다. 도로명과 건물번호를 함께 입력해 주세요.", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.places, key = { it.destination.toString() }) { place ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                            Row(Modifier.fillMaxWidth().clickable { onSelect(place) }.heightIn(min = 88.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(RouteIcons.Pin, null)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(place.destination.name + if (place.example) " · 예시" else "", style = MaterialTheme.typography.titleMedium)
                                    Text(place.address, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(RouteIcons.Arrow, "${place.destination.name} 선택")
                            }
                        }
                    }
                }
            }
        }
    }
}
