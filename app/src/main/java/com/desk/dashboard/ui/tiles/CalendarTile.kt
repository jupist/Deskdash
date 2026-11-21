package com.desk.dashboard.ui.tiles

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.CalEvent
import com.desk.dashboard.data.CalendarSource
import com.desk.dashboard.system.rememberCalendarPermission
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val timeStyle = Type.display16.copy(fontSize = 17.sp)

@Composable
fun CalendarTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permission = rememberCalendarPermission()

    if (!permission.granted) {
        Tile("Today", modifier, meta = "Calendar") {
            Spacer(Modifier.height(14.dp))
            Txt("Calendar access is off", Type.body, Dash.muted)
            Spacer(Modifier.weight(1f))
            Box { MiniButton("Allow access", Dash.ink, Dash.slate, permission.request) }
        }
        return
    }

    // Reload on calendar changes (sync), selection changes and every minute (to drop past events
    // and update "in N min").
    val minute by rememberMinute()
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { CalendarSource.changes(context).collect { version++ } }
    val selected by remember { CalendarSource.selectedIds(context) }.collectAsStateWithLifecycle(null)
    var events by remember { mutableStateOf<List<CalEvent>?>(null) }
    LaunchedEffect(minute, version, selected) {
        val ids = selected ?: CalendarSource.effectiveSelection(context)
        events = CalendarSource.upcoming(context, ids)
    }

    Tile("Today", modifier, meta = "Calendar") {
        val list = events ?: return@Tile
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Txt("Nothing else today", Type.body, Dash.muted)
            return@Tile
        }
        val nextTimed = list.firstOrNull { !it.allDay }
        val now = minute
        list.forEachIndexed { i, e ->
            EventRow(e, now, isNext = e == nextTimed, last = i == list.lastIndex) {
                try { context.startActivity(CalendarSource.openIntent(e)) } catch (_: ActivityNotFoundException) {}
            }
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, now: LocalDateTime, isNext: Boolean, last: Boolean, onClick: () -> Unit) {
    val tomorrow = e.start.toLocalDate().isAfter(now.toLocalDate())
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(104.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (e.allDay) "ALL DAY" else e.start.format(hhmm), if (e.allDay) Type.meta else timeStyle, Dash.ink)
                if (tomorrow) {
                    Spacer(Modifier.width(6.dp))
                    Txt("TMRW", Type.small, Dash.muted)
                }
            }
            Txt(e.title, Type.body, Dash.ink, Modifier.weight(1f))
            if (!e.allDay && !tomorrow) {
                Txt(
                    relative(e, now), Type.meta.copy(textAlign = TextAlign.End),
                    if (isNext) Dash.slateLight else Dash.muted, Modifier.padding(start = 12.dp),
                )
            }
        }
        if (last) Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))
    }
}

/** "now", "in 25 min", "in 3 h", "in 1 h 20 min". */
private fun relative(e: CalEvent, now: LocalDateTime): String {
    val mins = java.time.Duration.between(now, e.start).toMinutes()
    return when {
        mins <= 0 -> "now"
        mins < 60 -> "in $mins min"
        mins % 60 == 0L || mins >= 180 -> "in ${mins / 60} h"
        else -> "in ${mins / 60} h ${mins % 60} min"
    }
}
