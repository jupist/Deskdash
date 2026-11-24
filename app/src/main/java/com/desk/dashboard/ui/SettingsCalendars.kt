package com.desk.dashboard.ui

import com.desk.dashboard.remote.findActivity
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.calendar.CalendarHub
import com.desk.dashboard.calendar.GoogleCalendar
import com.desk.dashboard.calendar.IcsFeed
import kotlinx.coroutines.launch

private fun ago(ms: Long): String {
    if (ms <= 0) return "never"
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 1440 -> "${m / 60} h ago"
        else -> "${m / 1440} d ago"
    }
}

@Composable
private fun ColorDot(c: Int) = Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c)))

/** iCal links and imported .ics files. */
@Composable
internal fun SubscriptionsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalOverlay.current
    val sources by CalendarHub.sources.collectAsStateWithLifecycle()
    val syncing by CalendarHub.syncing.collectAsStateWithLifecycle()
    var importName by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = importName.ifBlank { uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported" }
            importError = if (CalendarHub.importFile(context, name, uri)) null else "That file isn't an iCalendar (.ics) file"
        }
    }

    Section("iCal links and files") {
        Txt(
            "Paste a calendar's secret iCal address (Google: Settings › your calendar › “Secret address in iCal format”; " +
                "Outlook: Publish calendar › ICS) or import an .ics file such as an exam timetable.",
            Type.meta, Dash.muted, maxLines = 4,
        )
        Spacer(Modifier.height(10.dp))
        sources.feeds.forEach { f -> FeedRow(f) }
        if (sources.feeds.isNotEmpty()) Spacer(Modifier.height(6.dp))
        importError?.let { Txt(it, Type.meta, Dash.danger); Spacer(Modifier.height(6.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniButton("Add iCal link", Dash.ink, Dash.accent) { overlay { AddLinkDialog { overlay(null) } } }
            MiniButton("Import .ics file", Dash.ink) { importName = ""; importer.launch(arrayOf("text/calendar", "text/*", "application/octet-stream")) }
            if (sources.feeds.any { it.url != null }) {
                Spacer(Modifier.weight(1f))
                MiniButton(if (syncing) "Syncing…" else "Sync now", Dash.muted) { scope.launch { CalendarHub.sync(context, force = true) } }
            }
        }
    }
}

@Composable
private fun FeedRow(f: IcsFeed) {
    val context = LocalContext.current
    Column {
        RowDivider()
        SettingRow(
            f.name,
            when {
                f.error != null -> f.error
                f.url == null -> "${f.eventCount} events from a file"
                f.lastSync <= 0 -> "Not synced yet"
                else -> "${f.eventCount} events, synced ${ago(f.lastSync)}"
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorDot(f.color)
                Switch(f.enabled, onToggle = { CalendarHub.setFeed(context, f.copy(enabled = !f.enabled)) })
                IconButton(DashIcons.trash, size = 32.dp, iconSize = 13.dp) { CalendarHub.removeFeed(context, f.id) }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Overlay(onDismiss = onClose, width = 520.dp) {
        PanelHeader("Add an iCal link", onClose)
        Field("Name") { TextInput(name, { name = it }, "e.g. University timetable", 40) }
        Spacer(Modifier.height(12.dp))
        Field("iCal address (https:// or webcal://)") { TextInput(url, { url = it.trim() }, "https://…/basic.ics", 600) }
        error?.let { Spacer(Modifier.height(8.dp)); Txt(it, Type.meta, Dash.danger, maxLines = 3) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(if (busy) "Checking the link…" else "The link is downloaded every 30 minutes.", Type.meta, Dash.muted, Modifier.weight(1f))
            PrimaryButton("Add") {
                if (busy || url.isBlank()) return@PrimaryButton
                busy = true
                scope.launch {
                    val feed = CalendarHub.addSubscription(context, name.ifBlank { "Calendar" }, url)
                    busy = false
                    if (feed.error == null) onClose()
                    else {
                        error = feed.error
                        CalendarHub.removeFeed(context, feed.id)
                    }
                }
            }
        }
    }
}

/** Google Calendar via sign-in (read only). */
@Composable
internal fun GoogleSection() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val sources by CalendarHub.sources.collectAsStateWithLifecycle()
    val g = sources.google
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun finish(token: String) {
        status = CalendarHub.connectGoogle(context, token)
        busy = false
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        when (val a = GoogleCalendar.fromIntent(activity, r.data)) {
            is GoogleCalendar.Auth.Token -> scope.launch { finish(a.token) }
            is GoogleCalendar.Auth.Failed -> { status = a.message; busy = false }
            is GoogleCalendar.Auth.NeedsConsent -> { status = "Access wasn't approved"; busy = false }
        }
    }

    fun connect() {
        busy = true
        status = null
        scope.launch {
            when (val a = GoogleCalendar.authorize(activity)) {
                is GoogleCalendar.Auth.Token -> finish(a.token)
                is GoogleCalendar.Auth.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(a.intent.intentSender).build())
                is GoogleCalendar.Auth.Failed -> { status = a.message; busy = false }
            }
        }
    }

    Section("Google Calendar") {
        if (g == null) {
            Txt(
                "Sign in to read your Google calendars directly. If your Google account is already on this device, " +
                    "its calendars also appear under On this device.",
                Type.meta, Dash.muted, maxLines = 4,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniButton(if (busy) "Connecting…" else "Connect Google account", Dash.ink, Dash.accent) { if (!busy) connect() }
            }
        } else {
            SettingRow(g.email, g.error ?: "Synced ${ago(g.lastSync)}") {
                MiniButton("Disconnect", Dash.muted) { CalendarHub.disconnectGoogle(context) }
            }
            g.calendars.forEach { cal ->
                RowDivider()
                SettingRow(cal.name, if (cal.primary) "Primary" else null, onClick = { CalendarHub.setGoogleCalendar(context, cal.id, !cal.selected) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorDot(cal.color)
                        Switch(cal.selected, onToggle = { CalendarHub.setGoogleCalendar(context, cal.id, !cal.selected) })
                    }
                }
            }
            if (g.error != null) {
                Spacer(Modifier.height(6.dp))
                MiniButton("Reconnect", Dash.ink, Dash.accent) { if (!busy) connect() }
            }
        }
        status?.let { Spacer(Modifier.height(8.dp)); Txt(it, Type.meta, Dash.danger, maxLines = 4) }
    }
}

