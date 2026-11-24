package com.desk.dashboard.modules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.desk.dashboard.calendar.GoogleCalendar
import com.desk.dashboard.calendar.GoogleTasks
import com.desk.dashboard.ui.Chips
import com.desk.dashboard.ui.Segmented
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.alarm.TimerState
import com.desk.dashboard.alarm.Timers
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.ui.CircleButton
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.FitText
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.IconButton
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Overlay
import com.desk.dashboard.ui.PanelHeader
import com.desk.dashboard.ui.PrimaryButton
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.TextInput
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc

private fun clock(ms: Long, tenths: Boolean = false): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (tenths && h == 0L) base + ".%d".format(ms / 100 % 10) else base
}

object TimerModule : Module(
    "timer", "Timer", "Countdown timer, stopwatch or focus (pomodoro) sessions.",
    DashIcons.timer, Size(3, 2), Size(1, 1),
) {
    override fun label(t: TileScope) = t.strOrNull("title") ?: when (t.str("mode", "timer")) {
        "stopwatch" -> "Stopwatch"; "focus" -> "Focus"; else -> "Timer"
    }

    private fun workMs(t: TileScope) = t.int("work", 25) * 60_000L
    private fun breakMs(t: TileScope) = t.int("break", 5) * 60_000L

    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val mode = t.str("mode", "timer")
        val states by Timers.states.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { Timers.load(context) }
        val s = (states[t.id] ?: TimerState()).let { if (it.mode != mode) TimerState(mode = mode, remainingMs = if (mode == "focus") workMs(t) else it.remainingMs) else it }
        if (states[t.id]?.mode != mode) LaunchedEffect(mode) { Timers.put(context, t.id, s) }

        // Tick while running: 10×/s for the stopwatch, every second otherwise.
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(s.running, mode) {
            while (s.running) {
                now = System.currentTimeMillis()
                delay(if (mode == "stopwatch") 100 else 1000 - now % 1000 + 5)
            }
            now = System.currentTimeMillis()
        }
        val shown = when (mode) {
            "stopwatch" -> clock(s.elapsedNow(now), tenths = true)
            else -> clock(s.remainingNow(now))
        }
        val meta = when {
            mode == "focus" -> (if (s.phase == "work") "Focus" else "Break") +
                (if (s.sessions > 0 && s.sessionsDay == now / 86_400_000) " · ${s.sessions} done" else "")
            s.done -> "Done"
            else -> null
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxW = maxWidth; val boxH = maxHeight
            if (maxWidth < 150.dp || maxHeight < 100.dp) {
                // Compact: the time, tap to start or pause.
                Column(Modifier.fillMaxSize().pressable({ toggle(context, t, s) }).padding(10.dp)) {
                    Txt(label(t).uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing), tc.ink)
                    Spacer(Modifier.weight(1f))
                    FitText(shown, Type.display16, if (s.running || s.done) tc.ink else tc.muted, Modifier.fillMaxWidth().weight(1f), sample = "00:00", maxSize = 40f)
                }
                return@BoxWithConstraints
            }
            Tile(label(t), meta = meta, metaColor = if (s.done) tc.accent else tc.muted) {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
                    FitText(shown, Type.big, tc.ink, Modifier.weight(1f).fillMaxHeight(), sample = if (mode == "stopwatch") "00:00.0" else "00:00", maxSize = 96f)
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        CircleButton(DashIcons.refresh, tc.fill, tc.ink, 38.dp, 14.dp, outline = tc.line) {
                            Timers.reset(context, t.id, if (mode == "focus") workMs(t).also { Timers.set(context, t.id) { st -> st.copy(phase = "work") } } else lastLength(t))
                        }
                        CircleButton(if (s.running) DashIcons.pause else DashIcons.play, tc.ink, tc.fill, 46.dp, 15.dp) { toggle(context, t, s) }
                    }
                }
                // Quick lengths for the countdown timer, when there's room and it's idle.
                if (mode == "timer" && !s.running && boxH > 150.dp) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 10, 15, 25, 45).take(((boxW - 40.dp) / 46.dp).toInt().coerceIn(2, 6)).forEach { m ->
                            MiniButton("${m}m", tc.ink, tc.line) {
                                t.set("length", m)
                                Timers.set(context, t.id) { it.copy(remainingMs = m * 60_000L, done = false) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun lastLength(t: TileScope) = t.int("length", 5) * 60_000L

    private fun toggle(c: android.content.Context, t: TileScope, s: TimerState) {
        when {
            s.running -> Timers.pause(c, t.id)
            s.done -> { Timers.reset(c, t.id, lastLength(t)); Timers.start(c, t.id) }
            else -> Timers.start(c, t.id)
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val context = LocalContext.current
        SettingRow("Mode") {
            Segmented(listOf("timer", "stopwatch", "focus"), t.str("mode", "timer"), { it.replaceFirstChar(Char::uppercase) }) { m ->
                t.set("mode", m)
                Timers.put(context, t.id, TimerState(mode = m, remainingMs = if (m == "focus") workMs(t) else lastLength(t)))
            }
        }
        when (t.str("mode", "timer")) {
            "timer" -> SettingRow("Length", "Or tap a quick length on the tile") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(DashIcons.back, size = 30.dp, iconSize = 12.dp) { setLength(context, t, (t.int("length", 5) - 1).coerceAtLeast(1)) }
                    Txt("${t.int("length", 5)} min", Type.body, Dash.ink, Modifier.width(64.dp))
                    IconButton(DashIcons.chevronRight, size = 30.dp, iconSize = 12.dp) { setLength(context, t, (t.int("length", 5) + 1).coerceAtMost(600)) }
                }
            }
            "focus" -> {
                SettingRow("Focus length") {
                    Segmented(listOf(15, 25, 45, 50), t.int("work", 25), { "${it}m" }) { t.set("work", it) }
                }
                SettingRow("Break length") {
                    Segmented(listOf(5, 10, 15), t.int("break", 5), { "${it}m" }) { t.set("break", it) }
                }
            }
        }
    }

    private fun setLength(c: android.content.Context, t: TileScope, m: Int) {
        t.set("length", m)
        Timers.set(c, t.id) { if (it.running) it else it.copy(remainingMs = m * 60_000L, done = false) }
    }
}

/** Checklist items are stored as "0|text" / "1|text" (done) strings in the tile's settings. */
private data class Item(val done: Boolean, val text: String, val id: String? = null)

private fun TileScope.items() = strings("items").map { Item(it.startsWith("1|"), it.substringAfter('|')) }
private fun TileScope.saveItems(list: List<Item>) = set("items", list.map { (if (it.done) "1|" else "0|") + it.text })

/** The Google Tasks list this tile shows, or null for its own items. */
private fun TileScope.googleList() = strOrNull("gtasks")

object ChecklistModule : Module(
    "checklist", "Checklist", "A to-do list you tick off right on the tile, or one of your Google Tasks lists.",
    DashIcons.checklist, Size(3, 3), Size(2, 2),
) {
    override fun label(t: TileScope) = t.str("title", t.strOrNull("gtasksName") ?: "To do")

    @Composable
    override fun Content(t: TileScope) {
        val list = t.googleList()
        if (list == null) {
            val items = t.items()
            ListBody(
                t, items, status = null,
                onToggle = { i -> t.saveItems(items.toMutableList().also { it[i] = items[i].copy(done = !items[i].done) }) },
                onAdd = { text -> t.saveItems(items + Item(false, text)) },
            )
        } else if (list.isEmpty()) {
            ListBody(t, emptyList(), status = "Choose a Google Tasks list in this tile's settings", onToggle = {}, onAdd = {})
        } else {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val state = GoogleTasks.lists.collectAsStateWithLifecycle().value[list]
            // Picks up changes made in the Tasks app or elsewhere.
            LaunchedEffect(list) {
                while (true) { GoogleTasks.refresh(context, list); delay(5 * 60_000L) }
            }
            val items = state?.tasks.orEmpty().map { Item(it.done, it.title, it.id) }
            ListBody(
                t, items,
                status = state?.error ?: if (state?.tasks == null) "Loading Google Tasks…" else null,
                onToggle = { i -> items[i].id?.let { id -> scope.launch { GoogleTasks.setDone(context, list, id, !items[i].done) } } },
                onAdd = { text -> scope.launch { GoogleTasks.add(context, list, text) } },
            )
        }
    }

    @Composable
    private fun ListBody(t: TileScope, items: List<Item>, status: String?, onToggle: (Int) -> Unit, onAdd: (String) -> Unit) {
        val hideDone = t.bool("hideDone", false)
        val visible = items.withIndex().filter { !hideDone || !it.value.done }
        val left = items.count { !it.done }
        var adding by remember { mutableStateOf(false) }
        Tile(label(t), meta = if (items.isEmpty()) null else "$left left") {
            Spacer(Modifier.height(8.dp))
            if (status != null) Txt(status, Type.meta, tc.muted, maxLines = 3)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(visible, key = { _, it -> it.value.id ?: it.index }) { _, (i, item) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 36.dp)
                            .clickable { onToggle(i) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(18.dp).clip(Dash.smallShape)
                                .background(if (item.done) tc.accentFill else Color.Transparent)
                                .border(1.dp, if (item.done) tc.accentFill else tc.muted, Dash.smallShape),
                            contentAlignment = Alignment.Center,
                        ) { if (item.done) Icon(DashIcons.check, tc.fill, 12.dp) }
                        Spacer(Modifier.width(10.dp))
                        Txt(
                            item.text,
                            Type.body.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                            if (item.done) tc.muted else tc.ink, Modifier.weight(1f),
                        )
                    }
                }
                item {
                    if (adding) {
                        AddField(onDone = { text ->
                            if (text.isNotBlank()) onAdd(text.trim())
                            adding = false
                        })
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clickable { adding = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(DashIcons.plus, tc.muted, 14.dp)
                            Spacer(Modifier.width(12.dp))
                            Txt("Add item", Type.body, tc.muted)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AddField(onDone: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        BasicTextField(
            text, { text = it.take(80) },
            singleLine = true,
            textStyle = Type.body.copy(color = tc.ink),
            cursorBrush = SolidColor(tc.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Txt("New item", Type.body, tc.muted)
                    inner()
                }
            },
        )
    }

    @Composable
    override fun Settings(t: TileScope) {
        val list = t.googleList()
        TitleSetting(t, t.strOrNull("gtasksName") ?: "To do")
        SwitchRow("Hide finished items", checked = t.bool("hideDone", false), onToggle = { t.set("hideDone", !t.bool("hideDone", false)) })
        SettingRow("Items from") {
            Segmented(listOf(false, true), list != null, { if (it) "Google Tasks" else "This tile" }) { google ->
                if (!google) t.setAll("gtasks" to null, "gtasksName" to null)
                else if (list == null) t.set("gtasks", "") // shows the list picker below
            }
        }
        if (list == null) {
            val items = t.items()
            SettingRow("Finished items", "${items.count { it.done }} of ${items.size}") {
                MiniButton("Clear finished", Dash.ink) { t.saveItems(items.filter { !it.done }) }
            }
            items.forEachIndexed { i, item ->
                SettingRow(item.text) {
                    IconButton(DashIcons.trash, size = 30.dp, iconSize = 13.dp) { t.saveItems(items.filterIndexed { j, _ -> j != i }) }
                }
            }
        } else {
            GoogleTasksPicker(t, list)
        }
    }

    /** Connects Google Tasks (asking for access the first time) and chooses the list. */
    @Composable
    private fun GoogleTasksPicker(t: TileScope, list: String) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var lists by remember { mutableStateOf<List<GoogleTasks.TaskList>?>(null) }
        var status by remember { mutableStateOf<String?>(null) }

        suspend fun load(token: String) {
            runCatching { GoogleTasks.taskLists(token) }
                .onSuccess { lists = it; status = if (it.isEmpty()) "No task lists in this Google account" else null }
                .onFailure { status = it.message }
        }
        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            when (val a = GoogleCalendar.fromIntent(context, r.data)) {
                is GoogleCalendar.Auth.Token -> scope.launch { load(a.token) }
                is GoogleCalendar.Auth.Failed -> status = a.message
                is GoogleCalendar.Auth.NeedsConsent -> status = "Access wasn't approved"
            }
        }
        fun connect() {
            status = "Connecting…"
            scope.launch {
                when (val a = GoogleCalendar.authorize(context, GoogleTasks.SCOPE)) {
                    is GoogleCalendar.Auth.Token -> load(a.token)
                    is GoogleCalendar.Auth.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(a.intent.intentSender).build())
                    is GoogleCalendar.Auth.Failed -> status = a.message
                }
            }
        }
        // Already allowed: list the choices straight away, without a sign-in screen.
        LaunchedEffect(Unit) {
            (GoogleCalendar.authorize(context, GoogleTasks.SCOPE) as? GoogleCalendar.Auth.Token)?.let { load(it.token) }
        }

        val l = lists
        if (l == null) {
            SettingRow("Google Tasks", status ?: "Sign in to show one of your task lists") {
                MiniButton("Connect", Dash.ink, Dash.accent) { connect() }
            }
        } else {
            SettingRow("List", if (list.isEmpty()) "Choose which list this tile shows" else null) {}
            Chips(l, { it.id == list }, { it.title }) { t.setAll("gtasks" to it.id, "gtasksName" to it.title) }
            status?.let { Spacer(Modifier.height(8.dp)); Txt(it, Type.meta, Dash.danger, maxLines = 3) }
            if (list.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SettingRow("Finished tasks", "Hides them in Google Tasks too") {
                    MiniButton("Clear finished", Dash.ink) { scope.launch { GoogleTasks.clearDone(context, list) } }
                }
            }
        }
    }
}

/** Optional custom label for a tile. */
@Composable
fun TitleSetting(t: TileScope, default: String) {
    var text by remember(t.id) { mutableStateOf(t.strOrNull("title") ?: "") }
    SettingRow("Title") {
        TextInput(text, { text = it; t.set("title", it.ifBlank { null }) }, default, 24, Modifier.width(200.dp))
    }
}

object NoteModule : Module(
    "note", "Sticky note", "A note in big, readable type. Tap to edit.",
    DashIcons.note, Size(3, 2), Size(1, 1),
) {
    override fun label(t: TileScope) = t.str("title", "Note")

    @Composable
    override fun Content(t: TileScope) {
        val overlay = LocalOverlay.current
        val text = t.str("text", "")
        val edit = { overlay { NoteEditor(text, onSave = { t.set("text", it); overlay(null) }) { overlay(null) } } }
        Tile(label(t), Modifier.pressable(edit, 0.98f)) {
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                if (text.isBlank()) {
                    Txt("Tap to write a note", Type.body, tc.muted)
                } else {
                    // Short notes get big type; long ones wrap at body size.
                    val big = text.length < 40 && !text.contains('\n')
                    val style = if (big) Type.display20.copy(fontSize = Type.display20.fontSize * 1.4f, lineHeight = Type.display20.fontSize * 1.7f) else Type.body.copy(lineHeight = Type.body.fontSize * 1.5f)
                    val lines = (maxHeight / (if (big) 30.dp else 20.dp)).toInt().coerceAtLeast(1)
                    Txt(text, style, tc.ink, maxLines = lines)
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) = TitleSetting(t, "Note")
}

@Composable
private fun NoteEditor(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Overlay(onDismiss = onClose, width = 520.dp) {
        PanelHeader("Note", onClose)
        BasicTextField(
            text, { text = it.take(500) },
            textStyle = Type.body.copy(color = Dash.ink, lineHeight = Type.body.fontSize * 1.5f),
            cursorBrush = SolidColor(Dash.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().height(200.dp).border(1.dp, Dash.line, Dash.smallShape).padding(12.dp).focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${text.length}/500", Type.meta, Dash.muted, Modifier.weight(1f))
            MiniButton("Clear", Dash.muted) { text = "" }
            Spacer(Modifier.width(8.dp))
            PrimaryButton("Save") { onSave(text) }
        }
    }
}

