package com.desk.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val steps = listOf("Welcome", "Look", "Weather", "Calendars", "Access", "Ready")

/** First-run setup: every step can be skipped and changed later in Settings. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    Box(Modifier.fillMaxSize().background(Dash.frame)) {
        Column(Modifier.fillMaxSize().imePadding().padding(Dash.gap * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            // Progress dots.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 10.dp else 7.dp).clip(CircleShape).background(if (i <= step) Dash.accentText else Dash.line))
                }
            }
            AnimatedContent(
                step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(200)) + slideInHorizontally { it / 6 * dir }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally { -it / 6 * dir })
                },
                label = "step", modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
                        when (s) {
                            0 -> Welcome()
                            1 -> { Heading("Make it yours", "Pick a theme and fonts. Everything here is in Settings › Appearance."); AppearancePage() }
                            2 -> { Heading("Weather", "Search for your city or neighbourhood. Forecasts come from Open-Meteo; no account needed."); WeatherSection() }
                            3 -> {
                                Heading("Calendars", "Show events from calendars on this device, an iCal link, an .ics file or Google.")
                                CalendarSection()
                                SubscriptionsSection()
                            }
                            4 -> {
                                Heading("Access", "Grant only what you'll use. Each item says which tile needs it.")
                                PermissionsSection()
                            }
                            else -> Ready()
                        }
                    }
                }
            }
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) OutlineButton("Back") { step-- } else MiniButton("Skip setup", Dash.muted) { onDone() }
                Spacer(Modifier.weight(1f))
                if (step in 1 until steps.lastIndex) {
                    MiniButton("Skip", Dash.muted) { step++ }
                    HSpace(10.dp)
                }
                PrimaryButton(when (step) { 0 -> "Get started"; steps.lastIndex -> "Open dashboard"; else -> "Next" }) {
                    if (step == steps.lastIndex) onDone() else step++
                }
            }
        }
        overlay?.invoke()
    }
}

@Composable
private fun Heading(title: String, text: String) {
    Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        Txt(title, Type.secondary, Dash.ink)
        Spacer(Modifier.height(6.dp))
        Txt(text, Type.body, Dash.muted, maxLines = 3)
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(Dash.shape).background(Dash.accent), contentAlignment = Alignment.Center) {
            DotGrid(Dash.onAccent)
        }
        Spacer(Modifier.height(28.dp))
        Txt("Deskdash", Type.secondary.copy(fontSize = 44.sp, lineHeight = 48.sp), Dash.ink)
        Spacer(Modifier.height(12.dp))
        Txt(
            "An always-on dashboard for a tablet or phone on your desk: clock, calendar, weather, music, alarm, timers and more, " +
                "laid out the way you like.",
            Type.body.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), Dash.muted, maxLines = 4,
        )
    }
}

@Composable
private fun Ready() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        Heading("You're set", "A few things worth knowing:")
        SettingsCard {
            SettingRow("Rearrange", "Long-press any tile, then drag it. Drag its corner to resize.", icon = DashIcons.grid)
            RowDivider()
            SettingRow("Add tiles", "In edit mode, tap Add tile: timers, checklists, world clocks, photos and more.", icon = DashIcons.plus)
            RowDivider()
            SettingRow("Tile options", "In edit mode, tap a tile for its style and settings.", icon = DashIcons.gear)
            RowDivider()
            SettingRow("Presets", "Save layouts and switch between them from the edit bar.", icon = DashIcons.app)
        }
    }
}
