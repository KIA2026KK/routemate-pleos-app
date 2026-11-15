package com.routemate.app.destination.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.routemate.app.design.*
import com.routemate.app.destination.domain.DestinationFailure

@Composable fun DestinationScreen(state: DestinationState, usePleos: Boolean, active: Boolean, busy: Boolean,
    configured: Boolean, hasError: Boolean, onDestination: () -> Unit, onAccount: () -> Unit,
    onVoicePoc: (() -> Unit)?, prototype: Boolean, roomContent: @Composable () -> Unit, overlayContent: @Composable () -> Unit) {
    var night by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect { activity?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !night; isAppearanceLightNavigationBars = !night
    } } }
    RouteMateTheme(night) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("RouteMate", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    RouteIconButton(if (night) "주간 모드" else "야간 모드", if (night) RouteIcons.Sun else RouteIcons.Moon, { night = !night })
                    Box {
                        RouteIconButton("더보기", RouteIcons.More, { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("계정 관리") }, { menu = false; onAccount() }, Modifier.heightIn(min = 76.dp))
                            if (onVoicePoc != null) DropdownMenuItem({ Text("음성 연결 진단") }, { menu = false; onVoicePoc() }, Modifier.heightIn(min = 76.dp))
                        }
                    }
                }
                if (prototype) Text("체험 모드 · 실제 음성 없음", Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val destinationCard: @Composable () -> Unit = {
                        VehicleCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(RouteIcons.Pin, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Text(if (usePleos) "내비게이션 목적지" else "테스트 목적지", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RouteIconButton(if (hasError) "목적지 등록 재시도" else if (usePleos) "내비게이션 새로고침" else "목적지 변경",
                                    if (hasError || usePleos) RouteIcons.Refresh else RouteIcons.Search, onDestination, enabled = !busy)
                            }
                            Text(when (state) {
                                is DestinationState.Loaded -> state.destination.name
                                DestinationState.Loading -> "목적지 확인 중"
                                DestinationState.Empty -> "목적지를 선택하세요"
                                is DestinationState.Error -> when (state.reason) {
                                    DestinationFailure.CONNECTION -> "내비게이션 연결을 확인하세요"
                                    DestinationFailure.TIMEOUT -> "목적지 응답이 지연되고 있어요"
                                    DestinationFailure.PERMISSION -> "목적지 접근 권한을 확인하세요"
                                    DestinationFailure.INVALID_DATA -> "목적지 좌표를 확인하세요"
                                    DestinationFailure.UNKNOWN -> "목적지를 다시 확인하세요"
                                }
                            }, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                !configured -> "서버 연결이 설정되지 않았습니다."
                                busy && !active -> "목적지 등록 중"
                                hasError -> "연결 상태를 확인해 주세요"
                                active -> "목적지 등록 완료"
                                else -> "새 운행을 시작하세요"
                            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!active && !busy && configured && state is DestinationState.Loaded)
                                DriveButton("목적지 선택", onDestination, Modifier.fillMaxWidth())
                        }
                    }
                    if (maxWidth >= 720.dp) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(.8f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { destinationCard() }
                            Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { roomContent() }
                        }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) { destinationCard(); roomContent(); Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        overlayContent()
    }
}
