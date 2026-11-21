package com.desk.dashboard.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
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
import com.desk.dashboard.ui.TileHeader
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val mon = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

private const val MaxRows = 3

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val dayStyle = TextStyle(fontFamily = Display, fontSize = 20.sp, lineHeight = 20.sp, lineHeightStyle = trim)
private val leftStyle = TextStyle(fontFamily = Display, fontSize = 18.sp, lineHeight = 18.sp, lineHeightStyle = trim)
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

    // Rows have natural heights; show as many whole rows as fit (max three) and put the rest in "+N more".
    SubcomposeLayout(click.background(Dash.tile).padding(horizontal = Dash.tilePadH, vertical = Dash.tilePadV)) { c ->
        val loose = Constraints(maxWidth = c.maxWidth)
        val gap = 6.dp.roundToPx()
        val headerH = subcompose("probe") { Txt("X", Type.label, Dash.ink) }.first().measure(loose).height
        val bottom = subcompose("bottom") { Divider() }.first().measure(loose)
        val avail = c.maxHeight - headerH - gap - bottom.height
        val want = minOf(list.size, MaxRows)

        fun fit(rows: List<androidx.compose.ui.layout.Placeable>): Int {
            var room = avail
            var n = 0
            while (n < rows.size && rows[n].height <= room) { room -= rows[n].height; n++ }
            return n
        }

        // Prefer the mockup's two-line rows; fall back to one-line rows when that shows more entries.
        val full = subcompose("rows") { list.take(MaxRows).forEach { DateRow(it, today) } }.map { it.measure(loose) }
        var rows = full
        var count = fit(full)
        if (count < want) {
            val compact = subcompose("compact") { list.take(MaxRows).forEach { CompactRow(it, today) } }.map { it.measure(loose) }
            val n = fit(compact)
            if (n > count) { rows = compact; count = n }
        }
        count = count.coerceAtLeast(1)
        val hidden = list.size - count
        val meta = if (hidden > 0) "+$hidden more" else if (list.size == 1) "1 date" else "${list.size} dates"
        val header = subcompose("header") { TileHeader("Upcoming", meta) }.first().measure(loose)

        layout(c.maxWidth, c.maxHeight) {
            header.place(0, 0)
            var y = headerH + gap
            for (i in 0 until count) { rows[i].place(0, y); y += rows[i].height }
            bottom.place(0, y)
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))

@Composable
private fun DateRow(e: UpcomingDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, e.date)
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(40.dp)) {
                Txt("%02d".format(e.date.dayOfMonth), dayStyle, Dash.ink)
                Spacer(Modifier.height(2.dp))
                Txt(e.date.format(mon).uppercase(), tinyStyle, Dash.muted)
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        e.category.name, tinyStyle, Dash.muted,
                        Modifier.border(1.dp, Dash.line).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Txt(e.title, titleStyle, Dash.ink)
                }
                val sub = listOfNotNull(e.time?.format(hhmm), e.note).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Txt(sub, noteStyle, Dash.muted)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Txt(daysLeft(days), leftStyle, leftColor(days))
                if (days > 1) {
                    Spacer(Modifier.height(2.dp))
                    Txt("LEFT", tinyStyle, Dash.muted)
                }
            }
        }
    }
}

private fun daysLeft(days: Long) = when (days) {
    0L -> "today"
    1L -> "tomorrow"
    else -> "${days}d"
}

private fun leftColor(days: Long) = if (days <= 3) Dash.slateLight else Dash.ink

private val compactDate = TextStyle(fontFamily = Display, fontSize = 16.sp, lineHeight = 16.sp, lineHeightStyle = trim)

/** One-line row: 24 NOV · TAG · title · time · note … days left. */
@Composable
private fun CompactRow(e: UpcomingDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, e.date)
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("%02d ".format(e.date.dayOfMonth) + e.date.format(mon).uppercase(), compactDate, Dash.ink, Modifier.width(70.dp))
            Txt(
                e.category.name, tinyStyle, Dash.muted,
                Modifier.border(1.dp, Dash.line).padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(6.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // Title takes what it needs (up to ~2/3); time and note get the rest and truncate first.
                Txt(e.title, titleStyle, Dash.ink, Modifier.weight(2f, fill = false))
                val sub = listOfNotNull(e.time?.format(hhmm), e.note).joinToString(" · ")
                if (sub.isNotEmpty()) Txt(sub, noteStyle, Dash.muted, Modifier.padding(start = 12.dp).weight(1f, fill = false))
            }
            Spacer(Modifier.width(8.dp))
            Txt(daysLeft(days), leftStyle.copy(fontSize = 16.sp, lineHeight = 16.sp), leftColor(days))
        }
    }
}
