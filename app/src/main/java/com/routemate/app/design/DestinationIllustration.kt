package com.routemate.app.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Illustrative matching diagram: no invented current position, route or map data. */
@Composable fun DestinationIllustration(radiusKm:Int,modifier:Modifier=Modifier){
    val night=MaterialTheme.colorScheme.background.luminance()<.5f
    val background=if(night)Color(0xFF15354B) else Color(0xFFEAF2F5)
    val roads=if(night)Color(0xFF284D64) else Color.White
    val riverColor=if(night)Color(0xFF1B5366) else Color(0xFFCEE6EB)
    val label=if(night)Color(0xFFD4EAF4) else HyundaiBlue
    val caption=if(night)Color(0xFFB2C9D8) else Color(0xFF344F65)
    Box(modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(20.dp)).background(background)){
        Canvas(Modifier.fillMaxSize()){
            val w=size.width;val h=size.height
            val river=Path().apply{moveTo(-w*.1f,h*.18f);cubicTo(w*.35f,h*.75f,w*.62f,-h*.1f,w*1.1f,h*.6f)}
            drawPath(river,riverColor,style=Stroke(h*.22f,cap=StrokeCap.Round))
            for(i in -2..7){
                drawLine(roads,Offset(i*w*.18f,-30f),Offset(i*w*.18f+w*.25f,h+30f),9.dp.toPx(),StrokeCap.Round)
                drawLine(roads,Offset(-20f,i*h*.3f),Offset(w+20f,i*h*.3f-h*.15f),7.dp.toPx(),StrokeCap.Round)
            }
            val center=Offset(w*.52f,h*.53f);val r=h*.36f
            drawCircle(RouteCyan.copy(alpha=.1f),r,center)
            drawCircle(RouteCyan.copy(alpha=.75f),r,center,style=Stroke(2.dp.toPx()))
            listOf(Offset(w*.3f,h*.45f),Offset(w*.72f,h*.68f),Offset(w*.68f,h*.25f)).forEach{
                drawLine(RouteCyan.copy(alpha=.35f),center,it,2.dp.toPx(),StrokeCap.Round)
                drawCircle(Color.White,10.dp.toPx(),it);drawCircle(HyundaiBlue,5.dp.toPx(),it)
            }
            drawCircle(Color.White,23.dp.toPx(),center);drawCircle(HyundaiBlue,17.dp.toPx(),center);drawCircle(RouteMint,6.dp.toPx(),center)
        }
        Text("목적지 기준 ${radiusKm}km",Modifier.align(Alignment.TopStart).padding(16.dp),color=label,style=MaterialTheme.typography.titleMedium)
        Text("연결도 예시 · 실제 지도와 길안내가 아닙니다",Modifier.align(Alignment.BottomStart).padding(16.dp),color=caption,style=MaterialTheme.typography.labelMedium)
    }
}
