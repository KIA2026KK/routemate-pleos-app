package com.routemate.app.design

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

private fun glyph(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
}.build()
object RouteIcons {
    val Search = glyph("Search") { moveTo(20f,20f);lineTo(15.5f,15.5f);moveTo(10.5f,3f);curveTo(20.5f,3f,20.5f,18f,10.5f,18f);curveTo(.5f,18f,.5f,3f,10.5f,3f);close() }
    val Refresh = glyph("Refresh") { moveTo(20f,8f);curveTo(15f,-1f,2f,3f,3f,13f);curveTo(4f,23f,18f,23f,21f,14f);moveTo(20f,3f);lineTo(20f,8f);lineTo(15f,8f) }
    val Moon = glyph("Moon") { moveTo(20f,15f);curveTo(8f,21f,3f,10f,11f,3f);curveTo(0f,3f,0f,22f,13f,21f);curveTo(16f,21f,19f,18f,20f,15f);close() }
    val Sun = glyph("Sun") { moveTo(12f,8f);curveTo(18f,8f,18f,16f,12f,16f);curveTo(6f,16f,6f,8f,12f,8f);close();moveTo(12f,2f);lineTo(12f,4f);moveTo(12f,20f);lineTo(12f,22f);moveTo(2f,12f);lineTo(4f,12f);moveTo(20f,12f);lineTo(22f,12f);moveTo(5f,5f);lineTo(6f,6f);moveTo(18f,18f);lineTo(19f,19f);moveTo(5f,19f);lineTo(6f,18f);moveTo(18f,6f);lineTo(19f,5f) }
    val More = glyph("More") { moveTo(5f,11f);lineTo(5f,13f);moveTo(12f,11f);lineTo(12f,13f);moveTo(19f,11f);lineTo(19f,13f) }
    val Pin = glyph("Pin") { moveTo(12f,22f);curveTo(7f,16f,3f,12f,5f,6f);curveTo(7f,-1f,18f,-1f,20f,6f);curveTo(22f,12f,17f,17f,12f,22f);close();moveTo(12f,6f);curveTo(16f,6f,16f,12f,12f,12f);curveTo(8f,12f,8f,6f,12f,6f);close() }
    val Arrow = glyph("Arrow") { moveTo(5f,12f);lineTo(19f,12f);moveTo(13f,6f);lineTo(19f,12f);lineTo(13f,18f) }
    val Plus = glyph("Plus") { moveTo(12f,5f);lineTo(12f,19f);moveTo(5f,12f);lineTo(19f,12f) }
    val Close = glyph("Close") { moveTo(6f,6f);lineTo(18f,18f);moveTo(18f,6f);lineTo(6f,18f) }
    val Mic = glyph("Mic") { moveTo(8f,5f);curveTo(8f,0f,16f,0f,16f,5f);lineTo(16f,11f);curveTo(16f,16f,8f,16f,8f,11f);close();moveTo(5f,10f);curveTo(5f,21f,19f,21f,19f,10f);moveTo(12f,19f);lineTo(12f,23f);moveTo(8f,23f);lineTo(16f,23f) }
    val MicOff = glyph("MicOff") { moveTo(3f,3f);lineTo(21f,21f);moveTo(8f,8f);lineTo(8f,11f);curveTo(8f,15f,14f,16f,16f,13f);moveTo(9f,4f);curveTo(10f,0f,16f,1f,16f,5f);lineTo(16f,9f);moveTo(5f,10f);curveTo(5f,18f,12f,21f,17f,17f);moveTo(19f,10f);lineTo(19f,13f);moveTo(12f,19f);lineTo(12f,23f);moveTo(8f,23f);lineTo(16f,23f) }
    val People = glyph("People") { moveTo(9f,3f);curveTo(15f,3f,15f,11f,9f,11f);curveTo(3f,11f,3f,3f,9f,3f);close();moveTo(2f,21f);lineTo(2f,18f);curveTo(2f,12f,16f,12f,16f,18f);lineTo(16f,21f);moveTo(17f,4f);curveTo(22f,4f,22f,11f,18f,11f);moveTo(20f,15f);curveTo(23f,16f,22f,19f,22f,21f) }
    val Leave = glyph("Leave") { moveTo(10f,4f);lineTo(4f,4f);lineTo(4f,20f);lineTo(10f,20f);moveTo(10f,12f);lineTo(22f,12f);moveTo(17f,7f);lineTo(22f,12f);lineTo(17f,17f) }
}
@Composable fun RouteIconButton(description: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, Modifier.size(76.dp), enabled = enabled) { Icon(icon, description, Modifier.size(28.dp)) }
}
