package com.desk.dashboard.modules

import com.desk.dashboard.remote.datesDao
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.calendar.CalendarHub
import com.desk.dashboard.calendar.Event
import com.desk.dashboard.calendar.OpenTarget
import com.desk.dashboard.data.CalendarSource
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.UpcomingDate
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.system.rememberToday
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Display
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Rule
import com.desk.dashboard.ui.Segmented
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

/** "now", "in 25 min", "in 3 h", "in 1 h 20 min". */
private fun relative(start: LocalDateTime, now: LocalDateTime): String {
    val mins = Duration.between(now, start).toMinutes()
    return when {
        mins <= 0 -> "now"
        mins < 60 -> "in $mins min"
        mins % 60 == 0L || mins >= 180 -> "in ${mins / 60} h"
        else -> "in ${mins / 60} h ${mins % 60} min"
    }
}

fun open(context: Context, t: OpenTarget) {
    try {
        when (t) {
            is OpenTarget.Device -> context.startActivity(CalendarSource.openIntent(t))
            is OpenTarget.Link -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            OpenTarget.None -> {}
        }
    } catch (_: ActivityNotFoundException) {}
}

object AgendaModule : Module(
    "agenda", "Agenda", "Upcoming events from your calendars: device, iCal links and Google.",
    DashIcons.calendar, Size(5, 2), Size(2, 1),
) {
    override fun label(t: TileScope) = t.strOrNull("title") ?: if (t.int("days", 2) <= 2) "Today" else "Agenda"

    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val actions = LocalDashActions.current
        val days = t.int("days", 2)
        val minute by rememberMinute()
        val version by CalendarHub.version.collectAsStateWithLifecycle()
        val sources by CalendarHub.sources.collectAsStateWithLifecycle()
        var deviceVersion by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { runCatching { CalendarSource.changes(context).collect { deviceVersion++ } } }
        // Remote sources refresh in the background while the tile is on screen.
        LaunchedEffect(Unit) {
            while (true) {
                CalendarHub.syncIfStale(context)
                delay(10 * 60_000L)
            }
        }
        var events by remember { mutableStateOf<List<Event>?>(null) }
        LaunchedEffect(minute, version, deviceVersion, days) { events = CalendarHub.events(context, days) }

        val hasAnySource = CalendarSource.hasPermission(context) || sources.feeds.isNotEmpty() || sources.google != null
        Tile(label(t)) {
            val list = events
            if (!hasAnySource) {
                Spacer(Modifier.weight(1f))
                Txt("No calendar connected", Type.body, tc.muted)
                Spacer(Modifier.height(10.dp))
                Box { MiniButton("Connect a calendar", tc.ink, tc.accentFill) { actions?.openSettings?.invoke("calendars") } }
                return@Tile
            }
            if (list == null) return@Tile
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Txt(if (days <= 1) "Nothing else today" else "Nothing in the next $days days", Type.body, tc.muted)
                return@Tile
            }
            val today = minute.toLocalDate()
            val grouped = days > 2 || list.any { it.start.toLocalDate() != list.first().start.toLocalDate() } && days > 1
            val nextTimed = list.firstOrNull { !it.allDay && it.start.isAfter(minute) }
            val dots = t.bool("dots", true)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                var lastDay: LocalDate? = null
                list.forEach { e ->
                    val d = if (e.allDay && e.start.toLocalDate().isBefore(today)) today else e.start.toLocalDate()
                    if (grouped && d != lastDay) {
                        lastDay = d
                        item(key = "h$d") {
                            Txt(
                                dayLabel(d, today).uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing),
                                if (d == today) tc.accent else tc.muted, Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = e.key) {
                        EventRow(e, minute, isNext = e == nextTimed, showDay = !grouped && d != today, dots = dots, location = t.bool("location", false)) {
                            open(context, e.open)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(e: Event, now: LocalDateTime, isNext: Boolean, showDay: Boolean, dots: Boolean, location: Boolean, onClick: () -> Unit) {
        val h24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Rule()
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dots) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(e.color?.let { Color(it) } ?: tc.muted))
                    Spacer(Modifier.width(10.dp))
                }
                Row(Modifier.width(if (h24) 66.dp else 84.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.allDay) Txt("ALL DAY", Type.meta, tc.ink)
                    else Txt(timeText(e.start, h24).let { (a, b) -> a + (b?.let { " ${it.lowercase()}" } ?: "") }, Type.display16, tc.ink)
                }
                Column(Modifier.weight(1f)) {
                    Txt(e.title, Type.body, tc.ink)
                    if (location && e.location != null) Txt(e.location, Type.meta, tc.muted)
                }
                val rel = when {
                    showDay -> "TMRW"
                    e.allDay -> null
                    else -> relative(e.start, now)
                }
                if (rel != null) {
                    Txt(rel, Type.meta.copy(textAlign = TextAlign.End), if (isNext) tc.accent else tc.muted, Modifier.padding(start = 10.dp))
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val actions = LocalDashActions.current
        SettingRow("Days ahead") {
            Segmented(listOf(1, 2, 3, 7, 14), t.int("days", 2), { if (it == 1) "Today" else "$it" }) { t.set("days", it) }
        }
        SwitchRow("Calendar colour dots", checked = t.bool("dots", true), onToggle = { t.set("dots", !t.bool("dots", true)) })
        SwitchRow("Show locations", checked = t.bool("location", false), onToggle = { t.set("location", !t.bool("location", false)) })
        TitleSetting(t, label(t))
        SettingRow("Calendars and accounts", "Device, iCal links, files and Google") {
            MiniButton("Open", Dash.ink) { actions?.openSettings?.invoke("calendars") }
        }
    }
}

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val mon = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

object CountdownModule : Module(
    "countdown", "Important dates", "Days left until exams, deadlines and trips you add.",
    DashIcons.flag, Size(6, 2), Size(2, 1),
) {
    override fun label(t: TileScope) = t.str("title", "Upcoming")

    @Composable
    override fun Content(t: TileScope) {
        val actions = LocalDashActions.current
        val dao = LocalContext.current.datesDao()
        val today by rememberToday()
        val entries by remember(today) { dao.upcoming(today) }.collectAsStateWithLifecycle(null)
        val list = entries ?: return Tile(label(t)) {}
        val open = { actions?.openSettings?.invoke("countdowns") ?: Unit }

        if (list.isEmpty()) {
            Tile(label(t), Modifier.pressable(open, 0.98f)) {
                Spacer(Modifier.weight(1f))
                Txt("No dates yet", Type.body, tc.muted)
                Txt("TAP TO ADD", Type.meta, tc.muted)
            }
            return
        }
        Tile(label(t), Modifier.pressable(open, 0.99f)) {
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val narrow = maxWidth < 330.dp
                // As many rows as fit at a comfortable height, stretched to fill the tile; the rest scroll.
                val rows = (maxHeight / 32.dp).toInt().coerceAtLeast(1)
                val rowH = maxHeight / rows
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { DateRow(it, today, Modifier.height(rowH), t, narrow) }
                }
            }
        }
    }

    @Composable
    private fun DateRow(e: UpcomingDate, today: LocalDate, modifier: Modifier, t: TileScope, narrow: Boolean) {
        val days = ChronoUnit.DAYS.between(today, e.date)
        val big = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 22.sp, lineHeightStyle = trim)
        Column(modifier.fillMaxWidth()) {
            Rule()
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(if (narrow) 70.dp else 92.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("%02d".format(e.date.dayOfMonth), big, tc.ink)
                    Spacer(Modifier.width(6.dp))
                    Txt(e.date.format(mon).uppercase(), Type.meta, tc.muted)
                }
                Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(e.title, Type.body, tc.ink, Modifier.weight(2f, fill = false))
                    val subs = listOfNotNull(
                        e.time?.takeIf { t.bool("time", true) }?.let { timeText(it, android.text.format.DateFormat.is24HourFormat(LocalContext.current)).first },
                        e.note?.takeIf { t.bool("notes", true) },
                    )
                    if (!narrow) subs.forEach { Txt(it, Type.meta, tc.muted, Modifier.padding(start = 12.dp).weight(1f, fill = false)) }
                }
                Txt(
                    when (days) { 0L -> "today"; 1L -> if (narrow) "tmrw" else "tomorrow"; else -> "${days}d" },
                    big.copy(fontSize = 20.sp, lineHeight = 20.sp), if (days <= 3) tc.accent else tc.ink,
                )
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val actions = LocalDashActions.current
        SwitchRow("Show times", checked = t.bool("time", true), onToggle = { t.set("time", !t.bool("time", true)) })
        SwitchRow("Show notes", checked = t.bool("notes", true), onToggle = { t.set("notes", !t.bool("notes", true)) })
        TitleSetting(t, "Upcoming")
        SettingRow("Add or edit dates") {
            MiniButton("Open", Dash.ink) { actions?.openSettings?.invoke("countdowns") }
        }
    }
}

