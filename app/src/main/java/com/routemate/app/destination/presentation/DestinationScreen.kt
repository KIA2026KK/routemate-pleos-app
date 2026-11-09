package com.routemate.app.destination.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import com.routemate.app.R
import com.routemate.app.destination.domain.DestinationFailure
import com.routemate.app.design.*

@Composable
fun DestinationScreen(state:DestinationState,onRefresh:()->Unit,usePleos:Boolean=false,
    onVoicePoc:(()->Unit)?=null,onAccount:(()->Unit)?=null,prototype:Boolean=false,radiusKm:Int=5,
    settingsAllowed:Boolean=false,onOpenSettings:()->Unit={},
    tripContent:@Composable ()->Unit={},roomContent:@Composable ()->Unit={},overlayContent:@Composable ()->Unit={}){
    var night by rememberSaveable{mutableStateOf(false)}
    var showTools by rememberSaveable{mutableStateOf(false)}
    val activity=LocalActivity.current
    val view=LocalView.current
    SideEffect{
        activity?.window?.let{window->WindowCompat.getInsetsController(window,view).apply{
            isAppearanceLightStatusBars=!night;isAppearanceLightNavigationBars=!night
        }}
    }
    RouteMateTheme(night){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.safeDrawingPadding()){
                DashboardHeader(night,{night=!night},onOpenSettings,onVoicePoc!=null,{showTools=!showTools})
                if(prototype)Text("프로토타입 체험 · 예시 차량·통화방이며 실제 음성은 연결되지 않습니다",
                    Modifier.padding(horizontal=24.dp,vertical=6.dp),color=MaterialTheme.colorScheme.primary,
                    style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                if(settingsAllowed){
                    Row(Modifier.padding(horizontal=24.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("정차 후 설정 · 주행 상태 자동 감지 미연동",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(onAccount!=null)DriveButton("계정 관리",onAccount,secondary=true)
                    }
                }
                if(showTools&&onVoicePoc!=null){
                    Row(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("개발자 도구",Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.bodyMedium)
                        DriveButton("음성 통화 테스트",onVoicePoc,secondary=true,enabled=settingsAllowed)
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
                    if(maxWidth>=900.dp){
                        Row(Modifier.fillMaxSize().padding(start=24.dp,end=24.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)){
                            Column(Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)){
                                DestinationCard(state,onRefresh,usePleos,radiusKm,tripContent)
                            }
                            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)){roomContent()}
                        }
                    }else{
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                            DestinationCard(state,onRefresh,usePleos,radiusKm,tripContent);roomContent()
                        }
                    }
                }
            }
        }
        overlayContent()
    }
}

@Composable private fun DashboardHeader(night:Boolean,onNight:()->Unit,onSettings:()->Unit,hasTools:Boolean,onTools:()->Unit){
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp)){
        if(maxWidth<700.dp){
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    HeaderBrand(Modifier.weight(1f));DriveButton(if(night)"주간" else "야간",onNight,secondary=true)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    DriveButton("정차 중 설정",onSettings,Modifier.weight(1f),secondary=true)
                    if(hasTools)DriveButton("더보기",onTools,secondary=true)
                }
            }
        }else{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                HeaderBrand(Modifier.weight(1f));DriveButton(if(night)"주간" else "야간",onNight,secondary=true)
                DriveButton("정차 중 설정",onSettings,secondary=true)
                if(hasTools)DriveButton("더보기",onTools,secondary=true)
            }
        }
    }
}
@Composable private fun HeaderBrand(modifier:Modifier){
    Column(modifier){
        Text("RouteMate",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
        Text("목적지가 가까운 우리",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun DestinationCard(state:DestinationState,onRefresh:()->Unit,usePleos:Boolean,radiusKm:Int,tripContent:@Composable ()->Unit){
    VehicleCard(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("최종 목적지",style=MaterialTheme.typography.titleMedium)
            StatusPill(if(usePleos)"Pleos 모드" else "샘플 목적지",accent=true)
        }
        when(state){
            DestinationState.Empty->Text(stringResource(R.string.empty_destination),style=MaterialTheme.typography.headlineSmall)
            DestinationState.Loading->Text(stringResource(R.string.loading_destination),style=MaterialTheme.typography.headlineSmall)
            is DestinationState.Error->Text(stringResource(when(state.reason){
                DestinationFailure.CONNECTION->R.string.destination_connection_error
                DestinationFailure.TIMEOUT->R.string.destination_timeout
                DestinationFailure.PERMISSION->R.string.destination_permission_error
                DestinationFailure.INVALID_DATA->R.string.destination_invalid_data
                DestinationFailure.UNKNOWN->R.string.destination_error
            }),color=MaterialTheme.colorScheme.error)
            is DestinationState.Loaded->Text(state.destination.name,style=MaterialTheme.typography.headlineLarge)
        }
        tripContent()
        Text("현재 위치 대신, 내비게이션의 최종 목적지로 연결합니다.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        DestinationIllustration(radiusKm)
        Text(stringResource(if(usePleos)R.string.pleos_notice else R.string.mock_notice),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        DriveButton(stringResource(if(usePleos)R.string.load_pleos_destination else R.string.load_destination),onRefresh,
            Modifier.fillMaxWidth(),enabled=state!=DestinationState.Loading,secondary=true)
    }
}
