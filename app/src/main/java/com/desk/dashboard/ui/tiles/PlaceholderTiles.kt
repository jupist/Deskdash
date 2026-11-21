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

