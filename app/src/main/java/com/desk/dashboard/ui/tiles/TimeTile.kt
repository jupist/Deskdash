package com.desk.dashboard.ui.tiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

@Composable
fun TimeTile(modifier: Modifier = Modifier) {
    Tile("Now", modifier, fill = Dash.slate, ink = Dash.onSlate) {
        Spacer(Modifier.weight(1f))
        // Only this subtree reads the minute state.
        val now = rememberMinute().value
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Txt(now.format(hhmm), Type.clock, Dash.onSlate, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Txt(now.format(weekday).uppercase(), Type.body, Dash.onSlate)
                Txt(now.format(dayMonth).uppercase(), Type.body, Dash.onSlate)
            }
        }
    }
}
