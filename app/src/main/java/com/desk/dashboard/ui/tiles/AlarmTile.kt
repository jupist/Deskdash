package com.desk.dashboard.ui.tiles

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.alarm.AlarmConfig
import com.desk.dashboard.alarm.Alarms
import com.desk.dashboard.system.rememberToday
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.Switch
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_ORDER = DayOfWeek.entries // Monday first

@Composable
fun AlarmTile(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by remember { Alarms.config(context) }.collectAsStateWithLifecycle(null)
    val today by rememberToday()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val c = cfg ?: return Tile("Alarm", modifier) {}

    Tile("Alarm", modifier, meta = meta(c, today), metaColor = if (c.enabled) Dash.slateLight else Dash.muted) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // Time and days open Settings (where they're edited); the switch toggles.
            Column(
                Modifier.weight(1f).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenSettings,
                )
            ) {
                Txt(c.time.format(hhmm), Type.big, if (c.enabled) Dash.ink else Dash.muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { d ->
                        val on = d in c.days
                        Box(
                            Modifier.size(22.dp).border(1.dp, if (on) Dash.slate else Dash.line),
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt(
                                d.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                                Type.meta.copy(letterSpacing = 0.sp), if (on) Dash.ink else Dash.muted,
                            )
                        }
                    }
                }
            }
            Switch(checked = c.enabled, onToggle = {
                val on = !c.enabled
                if (on) ensureAlarmPermissions(context) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                scope.launch { Alarms.setEnabled(context, on) }
            })
        }
    }
}

private fun meta(c: AlarmConfig, today: java.time.LocalDate): String {
    c.snoozeAt?.let {
        val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        return "Snoozed · ${t.format(hhmm)}"
    }
    if (!c.enabled) return "Off"
    val next = c.next().toLocalDate()
    val day = when (next) {
        today -> "today"
        today.plusDays(1) -> "tmrw"
        else -> next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }
    return if (c.days.isEmpty()) "Once · $day" else "On · $day"
}

/**
 * Notifications (for the ringing notification) and full-screen intents (alarm over the lock
 * screen). Asks for the first; sends the user to the system page for the second.
 */
fun ensureAlarmPermissions(context: Context, requestNotifications: () -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestNotifications()
        return
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    if (!nm.canUseFullScreenIntent()) {
        Toast.makeText(context, "Allow full-screen alerts so the alarm can show over the lock screen", Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        )
    }
}
