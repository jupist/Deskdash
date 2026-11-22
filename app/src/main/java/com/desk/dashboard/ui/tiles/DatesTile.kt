package com.desk.dashboard.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.UpcomingDate
import com.desk.dashboard.system.rememberToday
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.Display
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val mon = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

private const val VisibleRows = 3

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val dayStyle = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 22.sp, lineHeightStyle = trim)
private val leftStyle = TextStyle(fontFamily = Display, fontSize = 20.sp, lineHeight = 20.sp, lineHeightStyle = trim)
private val tinyStyle = Type.small.copy(fontSize = 8.sp, letterSpacing = 0.1.em, lineHeight = 10.sp, lineHeightStyle = trim)
private val titleStyle = Type.body.copy(lineHeight = 16.sp, lineHeightStyle = trim)
private val noteStyle = Type.meta.copy(lineHeight = 13.sp, lineHeightStyle = trim)

@Composable
fun DatesTile(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    val dao = DashDb.get(LocalContext.current).dates()
    val today by rememberToday()
    val entries by remember(today) { dao.upcoming(today) }.collectAsStateWithLifecycle(null)
    val list = entries ?: return Tile("Upcoming", modifier) {}
    val click = modifier.clickable(onClick = onOpenSettings)

    if (list.isEmpty()) {
        Tile("Upcoming", click, meta = "0 dates") {
            Spacer(Modifier.height(14.dp))
            Txt("No dates yet", Type.body, Dash.muted)
            Spacer(Modifier.weight(1f))
            Txt("ADD IN SETTINGS", Type.meta, Dash.muted)
        }
        return
    }

    // The three closest dates fill the tile; the rest are a scroll away.
    val meta = if (list.size == 1) "1 date" else "${list.size} dates"
    Tile("Upcoming", click, meta = meta) {
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val rowH = maxHeight / VisibleRows
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { DateRow(it, today, Modifier.height(rowH)) }
            }
            // Bottom rule closes the list when it all fits.
            if (list.size < VisibleRows) {
                Box(Modifier.offset(y = rowH * list.size)) { Divider() }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))

/** One line: 23 NOV · title · time · note … days left, centred in its third of the tile. */
@Composable
private fun DateRow(e: UpcomingDate, today: LocalDate, modifier: Modifier) {
    val days = ChronoUnit.DAYS.between(today, e.date)
    Column(modifier.fillMaxWidth()) {
        Divider()
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(92.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("%02d".format(e.date.dayOfMonth), dayStyle, Dash.ink)
                Spacer(Modifier.width(6.dp))
                Txt(e.date.format(mon).uppercase(), Type.meta, Dash.muted)
            }
            Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Title takes what it needs (up to ~2/3); time and note get the rest and truncate first.
                Txt(e.title, titleStyle, Dash.ink, Modifier.weight(2f, fill = false))
                val sub = listOfNotNull(e.time?.format(hhmm), e.note).joinToString(" · ")
                if (sub.isNotEmpty()) Txt(sub, noteStyle, Dash.muted, Modifier.padding(start = 12.dp).weight(1f, fill = false))
            }
            Txt(daysLeft(days), leftStyle, leftColor(days))
        }
    }
}

private fun daysLeft(days: Long) = when (days) {
    0L -> "today"
    1L -> "tomorrow"
    else -> "${days}d"
}

private fun leftColor(days: Long) = if (days <= 3) Dash.slateLight else Dash.ink
