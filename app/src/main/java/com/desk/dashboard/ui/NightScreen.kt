package com.desk.dashboard.ui

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/** Dim colours: readable in a dark room at 2 % brightness without lighting it up. */
private val NightInk = Color(0xFF6E6E6A)
private val NightMuted = Color(0xFF45454A)

/** Night mode: a large muted clock and the next alarm. Tap anywhere to peek at the dashboard. */
@Composable
fun NightScreen(onTap: () -> Unit) {
    val context = LocalContext.current
    val now by rememberMinute()
    val alarm by remember { Alarms.config(context) }.collectAsStateWithLifecycle(null)

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Txt(now.format(hhmm), Type.clock.copy(fontSize = 220.sp, lineHeight = 200.sp), NightInk)
            Spacer(Modifier.height(18.dp))
            Txt(alarmLine(alarm, now.toLocalDate()), Type.label.copy(fontSize = 16.sp, lineHeight = 20.sp), NightMuted)
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
    return "$what  ${at.format(hhmm)} · $day"
}
