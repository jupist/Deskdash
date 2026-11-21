package com.desk.dashboard.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.dashboard.ui.CircleButton
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Switch
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type

// Milestone 1 placeholders: each tile shows its real frame plus an empty state.
// They are replaced one by one in later milestones.

@Composable
private fun Empty(text: String, sub: String? = null) {
    Spacer(Modifier.height(14.dp))
    Txt(text, Type.body, Dash.muted)
    if (sub != null) Txt(sub, Type.meta, Dash.muted)
}

@Composable
fun WeatherTile(modifier: Modifier = Modifier) {
    Tile("Weather", modifier, meta = "—") {
        Spacer(Modifier.weight(1f))
        Txt("--°", Type.big, Dash.muted)
        Txt("Set a city in settings", Type.meta, Dash.muted)
    }
}

@Composable
fun CalendarTile(modifier: Modifier = Modifier) {
    Tile("Today", modifier, meta = "Calendar") { Empty("Nothing else today") }
}

@Composable
fun MusicTile(modifier: Modifier = Modifier) {
    Tile("Now playing", modifier, meta = "Spotify") {
        Spacer(Modifier.weight(1f))
        Txt("Nothing playing", Type.display16, Dash.ink)
        Txt("Start music in Spotify", Type.bodySmall, Dash.muted)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Dash.line))
        Spacer(Modifier.height(12.dp + 6.dp + 13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircleButton(DashIcons.prev, Dash.tile, Dash.muted, 40.dp, 16.dp, outline = Dash.line) {}
            CircleButton(DashIcons.play, Dash.ink, Dash.tile, 52.dp, 16.dp) {}
            CircleButton(DashIcons.next, Dash.tile, Dash.muted, 40.dp, 16.dp, outline = Dash.line) {}
        }
    }
}

@Composable
fun AlarmTile(modifier: Modifier = Modifier) {
    Tile("Alarm", modifier, meta = "Off") {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Txt("07:00", Type.big, Dash.ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                        val on = i < 5
                        Box(
                            Modifier.size(22.dp).border(1.dp, if (on) Dash.slate else Dash.line),
                            contentAlignment = Alignment.Center,
                        ) { Txt(d, Type.meta.copy(letterSpacing = 0.sp), if (on) Dash.ink else Dash.muted) }
                    }
                }
            }
            Switch(checked = false, onToggle = {})
        }
    }
}

@Composable
fun AmbientTile(modifier: Modifier = Modifier) {
    Box(modifier) {
        Tile("Ambient", Modifier.fillMaxSize(), fill = Dash.sand, ink = Dash.onSand) {
            Spacer(Modifier.weight(1f))
            Txt("OFF", Type.secondary, Dash.onSand)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .border(1.dp, Dash.onSand, CircleShape)
                            .background(if (i == 0) Dash.onSand else Dash.sand)
                    )
                }
            }
        }
        CircleButton(DashIcons.arrow, Dash.onSand, Dash.sand, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {}
    }
}

@Composable
fun ControlsTile(modifier: Modifier = Modifier) {
    Tile("Controls", modifier) {
        Spacer(Modifier.weight(1f))
        FakeSlider("Volume")
        Spacer(Modifier.height(14.dp))
        FakeSlider("Brightness")
    }
}

@Composable
private fun FakeSlider(label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Txt(label.uppercase(), Type.meta.copy(letterSpacing = Type.label.letterSpacing), Dash.muted, Modifier.weight(1f))
        Txt("—", Type.display16, Dash.ink)
    }
    Box(Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 4.dp).height(3.dp).background(Dash.line))
}

@Composable
fun ReadingTile(modifier: Modifier = Modifier) {
    Box(modifier) {
        Tile("Reading", Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Txt("ReadEra", Type.secondary, Dash.ink)
            Spacer(Modifier.height(6.dp))
            Txt("Open your PDFs", Type.bodySmall, Dash.muted)
        }
        CircleButton(DashIcons.arrow, Dash.ink, Dash.tile, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {}
    }
}
