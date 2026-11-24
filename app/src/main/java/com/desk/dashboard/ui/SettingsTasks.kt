package com.desk.dashboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.desk.dashboard.calendar.GoogleCalendar
import com.desk.dashboard.remote.findActivity
import com.desk.dashboard.tasks.GoogleTasks
import com.desk.dashboard.tasks.Tasks
import com.desk.dashboard.tasks.Todoist
import kotlinx.coroutines.launch

/** Settings › Tasks: connect the to-do services that Checklist tiles can show a list from. */
@Composable
internal fun TasksPage() {
    Txt(
        "Connect a service here, then choose one of its lists in a Checklist tile's settings (Items from). " +
            "Ticking and adding items there saves to the service, and changes made elsewhere show up within five minutes.",
        Type.meta, Dash.muted, Modifier.padding(horizontal = 4.dp), maxLines = 4,
    )
    GoogleTasksSection()
    TodoistSection()
}

@Composable
private fun GoogleTasksSection() {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(GoogleTasks.connected(context)) }
    var lists by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun finish(token: String) {
        runCatching { GoogleTasks.listsWith(token) }
            .onSuccess { GoogleTasks.setConnected(context, true); connected = true; lists = it.size; status = null }
            .onFailure { status = Tasks.message(it) }
        busy = false
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        when (val a = GoogleCalendar.fromIntent(activity, r.data)) {
            is GoogleCalendar.Auth.Token -> scope.launch { finish(a.token) }
            is GoogleCalendar.Auth.Failed -> { status = a.message; busy = false }
            is GoogleCalendar.Auth.NeedsConsent -> { status = "Access wasn't approved"; busy = false }
        }
    }
    LaunchedEffect(connected) {
        if (connected) runCatching { GoogleTasks.lists(context) }.onSuccess { lists = it.size }.onFailure { status = Tasks.message(it) }
    }

    Section("Google Tasks") {
        if (!connected) {
            Txt("Show a Google Tasks list on a Checklist tile. Uses your Google sign-in, asking for access to your tasks.", Type.meta, Dash.muted, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniButton(if (busy) "Connecting…" else "Connect Google Tasks", Dash.ink, Dash.accent) {
                    if (busy) return@MiniButton
                    busy = true; status = null
                    scope.launch {
                        when (val a = GoogleCalendar.authorize(activity, GoogleTasks.SCOPE)) {
                            is GoogleCalendar.Auth.Token -> finish(a.token)
                            is GoogleCalendar.Auth.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(a.intent.intentSender).build())
                            is GoogleCalendar.Auth.Failed -> { status = a.message; busy = false }
                        }
                    }
                }
            }
        } else {
            SettingRow("Connected", lists?.let { "$it ${if (it == 1) "list" else "lists"}" } ?: "Checking…") {
                MiniButton("Disconnect") { GoogleTasks.setConnected(context, false); connected = false; lists = null }
            }
        }
        status?.let { Spacer(Modifier.height(8.dp)); Txt(it, Type.meta, Dash.danger, maxLines = 3) }
    }
}

@Composable
private fun TodoistSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(Todoist.connected(context)) }
    var projects by remember { mutableStateOf<Int?>(null) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(connected) {
        if (connected) runCatching { Todoist.lists(context) }.onSuccess { projects = it.size }.onFailure { status = Tasks.message(it) }
    }

    Section("Todoist") {
        if (!connected) {
            Txt(
                "Show a Todoist project on a Checklist tile. Paste your API token from Todoist: Settings › Integrations › Developer. " +
                    "It stays on this device.",
                Type.meta, Dash.muted, maxLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(token, { token = it }, "API token", 80, Modifier.weight(1f), secret = true)
                MiniButton(if (busy) "Checking…" else "Connect", Dash.ink, Dash.accent) {
                    if (busy) return@MiniButton
                    busy = true; status = null
                    scope.launch {
                        runCatching { Todoist.connect(context, token) }
                            .onSuccess { connected = true; projects = it.size; token = "" }
                            .onFailure { status = Tasks.message(it) }
                        busy = false
                    }
                }
            }
        } else {
            SettingRow("Connected", projects?.let { "$it ${if (it == 1) "project" else "projects"}" } ?: "Checking…") {
                MiniButton("Disconnect") { Todoist.disconnect(context); connected = false; projects = null }
            }
        }
        status?.let { Spacer(Modifier.height(8.dp)); Txt(it, Type.meta, Dash.danger, maxLines = 3) }
    }
}
