package com.desk.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.system.rememberBattery
import com.desk.dashboard.ui.tiles.AlarmTile
import com.desk.dashboard.ui.tiles.AmbientMini
import com.desk.dashboard.ui.tiles.CalendarTile
import com.desk.dashboard.ui.tiles.DatesTile
import com.desk.dashboard.ui.tiles.MusicTile
import com.desk.dashboard.ui.tiles.ReadingMini
import com.desk.dashboard.ui.tiles.TimeTile
import com.desk.dashboard.ui.tiles.WeatherTile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashActions(
    val openSettings: () -> Unit,
    val lock: () -> Unit,
    val openApps: () -> Unit,
)

/*
 * Layout (12 columns × 3 rows), see SPEC.md › Visual design:
 *
 *   row 1  | Time 1–5           | Today (calendar) 6–12              |
 *   row 2  | Now playing 1–4    | Upcoming dates 5–12                |
 *   row 3  | (Now playing)      | Alarm 5–8 | Weather 9–11 | Amb/Read 12 |
 *
 * Volume and brightness live in the top bar.
 */
@Composable
fun Dashboard(actions: DashActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dash.frame)
            .padding(Dash.gap),
        verticalArrangement = Arrangement.spacedBy(Dash.gap),
    ) {
        TopBar(actions)
        DashGrid(Modifier.fillMaxWidth().weight(1f)) {
            TimeTile(Modifier.layoutId(Cell(1, 5, 1)))
            CalendarTile(Modifier.layoutId(Cell(6, 12, 1)))
            MusicTile(Modifier.layoutId(Cell(1, 4, 2, 3)))
            DatesTile(Modifier.layoutId(Cell(5, 12, 2)), actions.openSettings)
            AlarmTile(Modifier.layoutId(Cell(5, 8, 3)), actions.openSettings)
            WeatherTile(Modifier.layoutId(Cell(9, 11, 3)))
            Column(Modifier.layoutId(Cell(12, 12, 3)), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
                AmbientMini(Modifier.fillMaxWidth().weight(1f))
                ReadingMini(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun TopBar(actions: DashActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dash.topBar)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dot icon: the list of installed apps (how to reach other apps in home-app mode).
        Box(Modifier.clickable(onClick = actions.openApps).padding(horizontal = 8.dp, vertical = 12.dp)) { DotGrid() }
        HSpace(16.dp)
        Box(Modifier.width(1.dp).height(26.dp).background(Dash.line))
        HSpace(28.dp)
        BatteryStat()
        HSpace(28.dp)
        UpdatedStat()
        Spacer(Modifier.weight(1f))
        VolumeSlider()
        HSpace(28.dp)
        BrightnessSlider()
        Spacer(Modifier.weight(1f))
        OutlineButton("Settings", DashIcons.gear, actions.openSettings)
        HSpace(12.dp)
        OutlineButton("Lock", DashIcons.lock, actions.lock)
    }
}

/** Own composable so battery updates recompose only this stat. */
@Composable
private fun BatteryStat() {
    val battery = rememberBattery().value
    Stat("Tablet", battery?.toString() ?: "—")
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/** Time of the last successful weather fetch. */
@Composable
private fun UpdatedStat() {
    val state by WeatherRepo.get(LocalContext.current).state.collectAsStateWithLifecycle()
    val at = state.fetchedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(hhmm) }
    Stat("Updated", at ?: "—")
}

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Txt(label.uppercase(), Type.small, Dash.muted)
        Txt(value, Type.bodySmall, Dash.ink)
    }
}
