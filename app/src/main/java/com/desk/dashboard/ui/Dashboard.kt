package com.desk.dashboard.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import com.desk.dashboard.system.rememberBattery
import com.desk.dashboard.ui.tiles.AlarmTile
import com.desk.dashboard.ui.tiles.AmbientTile
import com.desk.dashboard.ui.tiles.CalendarTile
import com.desk.dashboard.ui.tiles.ControlsTile
import com.desk.dashboard.ui.tiles.DatesTile
import com.desk.dashboard.ui.tiles.MusicTile
import com.desk.dashboard.ui.tiles.ReadingTile
import com.desk.dashboard.ui.tiles.TimeTile
import com.desk.dashboard.ui.tiles.WeatherTile

class DashActions(
    val openSettings: () -> Unit,
    val lock: () -> Unit,
    val openApps: () -> Unit,
)

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
            WeatherTile(Modifier.layoutId(Cell(6, 8, 1)))
            CalendarTile(Modifier.layoutId(Cell(9, 12, 1)))
            MusicTile(Modifier.layoutId(Cell(1, 4, 2, 3)))
            AlarmTile(Modifier.layoutId(Cell(5, 8, 2)))
            DatesTile(Modifier.layoutId(Cell(9, 12, 2)), actions.openSettings)
            AmbientTile(Modifier.layoutId(Cell(5, 6, 3)))
            ControlsTile(Modifier.layoutId(Cell(7, 9, 3)))
            ReadingTile(Modifier.layoutId(Cell(10, 12, 3)))
        }
    }
}

@Composable
private fun TopBar(actions: DashActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dash.topBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(4.dp)) { DotGrid() }
        HSpace(24.dp)
        Box(Modifier.width(1.dp).height(26.dp).background(Dash.line))
        HSpace(28.dp)
        BatteryStat()
        HSpace(28.dp)
        // Filled in by milestone 3 (time of the last successful weather fetch).
        Stat("Updated", "—")
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

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Txt(label.uppercase(), Type.small, Dash.muted)
        Txt(value, Type.bodySmall, Dash.ink)
    }
}
