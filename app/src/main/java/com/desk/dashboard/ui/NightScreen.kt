package com.desk.dashboard.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.alarm.AlarmConfig
import com.desk.dashboard.alarm.Alarms
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.system.rememberSecond
import com.desk.dashboard.system.is24h
import com.desk.dashboard.calendar.CalendarHub
import com.desk.dashboard.calendar.Event
import com.desk.dashboard.data.DisplayConfig
import com.desk.dashboard.modules.timeText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import java.time.LocalDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/** Dim colours from the theme: readable in a dark room at low brightness without lighting it up. */
private val NightInk get() = Look.nightPalette.ink.copy(alpha = 0.45f)
private val NightMuted get() = Look.nightPalette.accentText.copy(alpha = 0.4f)

/**
 * Night mode: a large muted clock, sized to the screen, with the next alarm and event under it.
 * Tap anywhere to peek at the dashboard.
 */
@Composable
fun NightScreen(cfg: DisplayConfig?, onTap: () -> Unit) {
    val context = LocalContext.current
    val seconds = cfg?.nightSeconds == true
    val now = if (seconds) rememberSecond().value else rememberMinute().value
    val alarm by remember { Alarms.config(context) }.collectAsStateWithLifecycle(Alarms.cached)
    var next by remember { mutableStateOf<Event?>(null) }
    val minute = now.withSecond(0)
    LaunchedEffect(minute, cfg?.nightEvent) {
        next = if (cfg?.nightEvent == true) CalendarHub.events(context, 2).firstOrNull { !it.allDay && it.start.isAfter(LocalDateTime.now()) } else null
    }
    val h24 = is24h(context)
    val (time, ampm) = timeText(now, h24, seconds)

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap,
        ).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FitText(
                time, Type.clock, NightInk, Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                sample = (if (h24) "00:00" else "10:00") + if (seconds) ":00" else "", maxSize = 260f,
                alignment = Alignment.BottomCenter,
            )
            if (ampm != null) Txt(ampm, Type.label.copy(fontSize = 16.sp), NightMuted)
            Spacer(Modifier.height(18.dp))
            if (cfg?.nightAlarm != false) Txt(alarmLine(alarm, now.toLocalDate()), Type.label.copy(fontSize = 16.sp, lineHeight = 22.sp), NightMuted)
            next?.let { e ->
                Spacer(Modifier.height(8.dp))
                val (t, ap) = timeText(e.start, h24)
                val day = if (e.start.toLocalDate() != now.toLocalDate()) "TMRW " else ""
                Row {
                    val style = Type.label.copy(fontSize = 14.sp, lineHeight = 20.sp)
                    Txt("$day$t${ap?.let { " $it" } ?: ""}".uppercase(), style, NightMuted)
                    Spacer(Modifier.width(14.dp))
                    Txt(e.title.uppercase(), style, NightMuted)
                }
            }
        }
    }
}

private fun alarmLine(c: AlarmConfig?, today: LocalDate): String {
    c ?: return ""
    val at = c.snoozeAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        ?: if (c.enabled) c.next() else null
    at ?: return "NO ALARM"
    val day = when (at.toLocalDate()) {
        today -> "TODAY"
        today.plusDays(1) -> "TMRW"
        else -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
    }
    val what = if (c.snoozeAt != null) "SNOOZED" else "ALARM"
    return "$what  $day ${at.format(hhmm)}"
}
