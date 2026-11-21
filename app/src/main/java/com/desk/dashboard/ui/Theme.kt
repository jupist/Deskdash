package com.desk.dashboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.desk.dashboard.R

/** Color tokens from SPEC.md. The app is dark only. */
object Dash {
    val frame = Color(0xFF111114)
    val tile = Color(0xFF1C1C1F)
    val ink = Color(0xFFEEECE5)
    val muted = Color(0xFF8B8B88)
    val line = Color(0xFF2E2E33)
    val slate = Color(0xFF4E6187)
    val slateLight = Color(0xFF8EA3CC)
    val onSlate = Color(0xFFF3F1EA)
    val onSlateMuted = Color(0xB3F3F1EA) // on-slate at 70%, as in the mockup
    val sand = Color(0xFFAEADA3)
    val onSand = Color(0xFF1C1C1F)

    val gap = 12.dp
    val topBar = 48.dp
    val tilePadV = 18.dp
    val tilePadH = 20.dp
}

val Display = FontFamily(Font(R.font.share_tech_mono))
val Mono = FontFamily(Font(R.font.space_mono))

object Type {
    /** Tile labels: 11 sp, uppercase (apply .uppercase() to the text), 0.1 em tracking. */
    val label = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.1.em, lineHeight = 14.sp)
    val meta = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.06.em, lineHeight = 13.sp)
    val small = TextStyle(fontFamily = Mono, fontSize = 9.sp, letterSpacing = 0.08.em, lineHeight = 12.sp)
    val body = TextStyle(fontFamily = Mono, fontSize = 13.sp, letterSpacing = 0.04.em, lineHeight = 17.sp)
    val bodySmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.04.em, lineHeight = 16.sp)

    val clock = TextStyle(fontFamily = Display, fontSize = 104.sp, lineHeight = 94.sp, letterSpacing = (-0.02).em)
    val big = TextStyle(fontFamily = Display, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = (-0.01).em)
    val secondary = TextStyle(fontFamily = Display, fontSize = 28.sp, lineHeight = 30.sp)
    val display16 = TextStyle(fontFamily = Display, fontSize = 16.sp, lineHeight = 20.sp)
}
