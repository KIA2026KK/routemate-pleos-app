package com.routemate.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HyundaiBlue=Color(0xFF002C5F)
val RouteCyan=Color(0xFF00AAD2)
val RouteMint=Color(0xFFBCEDE7)
private val DayColors=lightColorScheme(primary=HyundaiBlue,onPrimary=Color.White,secondary=Color(0xFF007E9D),
    onSecondary=Color.White,tertiary=Color(0xFF006C61),background=Color(0xFFF3F6FA),surface=Color.White,
    onSurface=Color(0xFF102C48),onBackground=Color(0xFF102C48),surfaceVariant=Color(0xFFE8EFF5),
    onSurfaceVariant=Color(0xFF465C71),outline=Color(0xFF8295A7),error=Color(0xFFB3261E))
private val NightColors=darkColorScheme(primary=Color(0xFF81DCEF),onPrimary=HyundaiBlue,secondary=RouteMint,
    onSecondary=HyundaiBlue,background=Color(0xFF081725),surface=Color(0xFF102A41),onSurface=Color(0xFFF1F7FC),
    onBackground=Color(0xFFF1F7FC),surfaceVariant=Color(0xFF1C3B54),onSurfaceVariant=Color(0xFFC0D1E0),
    outline=Color(0xFF7D98AE),error=Color(0xFFFFB4AB))

@Composable fun RouteMateTheme(night:Boolean=false,content:@Composable ()->Unit){
    MaterialTheme(colorScheme=if(night)NightColors else DayColors,typography=Typography(
        headlineLarge=androidx.compose.ui.text.TextStyle(fontSize=34.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold),
        headlineMedium=androidx.compose.ui.text.TextStyle(fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
        headlineSmall=androidx.compose.ui.text.TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold),
        titleLarge=androidx.compose.ui.text.TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold),
        titleMedium=androidx.compose.ui.text.TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
        bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=18.sp,lineHeight=26.sp),
        bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=24.sp),
        labelLarge=androidx.compose.ui.text.TextStyle(fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    ),content=content)
}
@Composable fun VehicleCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(modifier,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.2f))){
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
/** Large touch targets also apply to secondary actions on a car display. */
@Composable fun DriveButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,secondary:Boolean=false){
    if(secondary)OutlinedButton(onClick,modifier.sizeIn(minWidth=76.dp,minHeight=76.dp),enabled=enabled,shape=RoundedCornerShape(18.dp),
        contentPadding=PaddingValues(horizontal=22.dp,vertical=16.dp)){Text(text)}
    else Button(onClick,modifier.sizeIn(minWidth=76.dp,minHeight=76.dp),enabled=enabled,shape=RoundedCornerShape(18.dp),
        contentPadding=PaddingValues(horizontal=22.dp,vertical=16.dp)){Text(text)}
}
@Composable fun StatusPill(text:String,accent:Boolean=false){
    Surface(color=if(accent)RouteMint else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(50)){
        Text(text,Modifier.padding(horizontal=16.dp,vertical=8.dp),color=if(accent)HyundaiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
    }
}
