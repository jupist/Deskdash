package com.desk.dashboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.dashboard.data.AppearanceStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val a = Look.appearance
    fun set(block: (Appearance) -> Appearance) = scope.launch { AppearanceStore.update(context) { block(it) } }

    Section("Theme") {
        // As many cards per row as fit, sized to fill it, so no card is left alone on a row.
        val families = Palettes.all(context)
        androidx.compose.foundation.layout.BoxWithConstraints {
        val perRow = ((maxWidth + 10.dp) / (112.dp + 10.dp)).toInt().coerceIn(1, families.size)
        // Rounded down with 1 dp to spare: pixel rounding must never push the last card onto a new row.
        val cardW = (((maxWidth - 10.dp * (perRow - 1)) / perRow).value.toInt().dp - 1.dp).coerceAtMost(170.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            families.forEach { fam ->
                val p = if (Look.palette.dark) fam.dark else fam.light
                val on = a.family == fam.id
                Column(
                    Modifier.width(cardW).clip(Dash.shape)
                        .border(if (on) 2.dp else 1.dp, if (on) Dash.accentText else Dash.line, Dash.shape)
                        .pressable({ set { it.copy(family = fam.id) } }).padding(10.dp)
                ) {
                    // A tiny dashboard in that theme.
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(Dash.smallShape).background(p.frame).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1.2f).height(46.dp).clip(Dash.smallShape).background(p.accent))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(21.dp).clip(Dash.smallShape).background(p.tile))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f).height(21.dp).clip(Dash.smallShape).background(p.tile))
                                Box(Modifier.weight(1f).height(21.dp).clip(Dash.smallShape).background(p.alt))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Txt(fam.name, Type.bodySmall, if (on) Dash.accentText else Dash.ink)
                }
            }
        }
        }
        Spacer(Modifier.height(14.dp))
        SettingRow("Mode", "System follows the device's dark theme") {
            Segmented(ThemeMode.entries, a.mode, { it.label }) { m -> set { it.copy(mode = m) } }
        }
        if (Look.palette.dark || a.mode != ThemeMode.LIGHT) {
            SwitchRow("Black background", "Pure black in dark mode, for OLED screens", a.pureBlack) { set { it.copy(pureBlack = !it.pureBlack) } }
        }
    }

    Section("Tiles") {
        SettingRow("Corners") { Segmented(Corners.entries, a.corners, { it.label }) { c -> set { it.copy(corners = c) } } }
        SettingRow("Spacing") { Segmented(Spacing.entries, a.spacing, { it.label }) { s -> set { it.copy(spacing = s) } } }
    }

    Section("Fonts") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (Fonts.builtIn + Fonts.custom(context)).forEach { f ->
                val on = a.fonts == f.id
                Column(
                    Modifier.width(150.dp).height(118.dp).clip(Dash.shape)
                        .border(if (on) 2.dp else 1.dp, if (on) Dash.accentText else Dash.line, Dash.shape)
                        .pressable({ set { it.copy(fonts = f.id) } }).padding(12.dp)
                ) {
                    Txt("12:34", TextStyle(fontFamily = f.display, fontSize = 30.sp, lineHeight = 34.sp), Dash.ink)
                    Spacer(Modifier.weight(1f))
                    Txt("Agenda · 3 left", TextStyle(fontFamily = f.text, fontSize = 12.sp, lineHeight = 16.sp), Dash.muted)
                    Spacer(Modifier.height(6.dp))
                    Txt(f.name, Type.bodySmall, if (on) Dash.accentText else Dash.ink)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CustomFontRow("display", "Custom display font", "Clock, numbers and titles")
        CustomFontRow("text", "Custom text font", "Labels and body text")
        Spacer(Modifier.height(4.dp))
        SettingRow("Text size", "Labels and body text; clocks size themselves to their tile") {
            Segmented(listOf(0.9f, 1f, 1.15f, 1.3f), a.textScale, { when (it) { 0.9f -> "S"; 1f -> "M"; 1.15f -> "L"; else -> "XL" } }) { v -> set { it.copy(textScale = v) } }
        }
    }
}

@Composable
private fun CustomFontRow(which: String, title: String, hint: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val exists = remember(Look.appearance.fontStamp) { Fonts.customFile(context, which).exists() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            error = if (AppearanceStore.importFont(context, which, uri)) null else "That file isn't a TrueType or OpenType font"
        }
    }
    RowDivider()
    SettingRow(title, error ?: if (exists) "$hint · file loaded" else hint) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (exists) MiniButton("Remove", Dash.muted) { scope.launch { AppearanceStore.clearFont(context, which) } }
            MiniButton("Choose file", Dash.ink, Dash.accent) {
                picker.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
            }
        }
    }
}
