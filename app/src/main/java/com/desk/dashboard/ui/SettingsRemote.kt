package com.desk.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.TileSpec
import com.desk.dashboard.data.Category
import com.desk.dashboard.data.UpcomingDate
import com.desk.dashboard.remote.RemoteAlarm
import com.desk.dashboard.remote.RemoteBoard
import com.desk.dashboard.remote.RemoteDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.BuildConfig
import com.desk.dashboard.board.BoardView
import com.desk.dashboard.remote.Discovery
import com.desk.dashboard.remote.FoundTablet
import com.desk.dashboard.remote.Pairings
import com.desk.dashboard.remote.Protocol
import com.desk.dashboard.remote.RemoteHost
import com.desk.dashboard.remote.RemoteSession
import kotlinx.coroutines.delay

/** Settings › Phone control: let phones edit this dashboard, or edit another one from here. */
@Composable
internal fun RemotePage(showOverlay: ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val enabled by RemoteHost.enabled.collectAsStateWithLifecycle()
    val code by RemoteHost.code.collectAsStateWithLifecycle()
    val paired by RemoteHost.paired.collectAsStateWithLifecycle()
    val connected by RemoteHost.connected.collectAsStateWithLifecycle()

    Section("This device") {
        SwitchRow(
            "Edit from another device",
            "A phone with Deskdash on the same Wi-Fi can change this dashboard: its layout, tiles, countdowns and alarm. Only phones you pair can connect.",
            enabled,
        ) { RemoteHost.setEnabled(!enabled) }
        if (enabled) {
            RowDivider()
            if (code == null) {
                SettingRow("Pair a phone", "Shows a code to enter on the phone") {
                    MiniButton("Pair", Dash.ink, Dash.accent) { RemoteHost.openPairing() }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Txt("Enter this code on the phone", Type.body, Dash.ink)
                    Spacer(Modifier.height(8.dp))
                    Txt(code!!.chunked(3).joinToString(" "), Type.big, Dash.accentText)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("On the phone: Settings › Phone control, then tap this device. The code lasts 5 minutes.", Type.meta, Dash.muted, Modifier.weight(1f), maxLines = 3)
                        HSpace(12.dp)
                        MiniButton("Cancel") { RemoteHost.closePairing() }
                    }
                }
                // Closes itself when the code runs out, so a stale code isn't left on screen.
                LaunchedEffect(code) { delay(5 * 60_000L); RemoteHost.closePairing() }
            }
            paired.forEach { (hash, name) ->
                RowDivider()
                SettingRow(name, if (name in connected) "Connected now" else "Paired") {
                    MiniButton("Forget") { RemoteHost.forget(hash) }
                }
            }
        }
    }

    // Control another device (the phone side).
    val discovery = remember { Discovery(context) }
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val found by discovery.found.collectAsStateWithLifecycle()
    // This device's own announcement is only useful for testing on one device.
    val own = if (enabled) Protocol.hostId(RemoteHost.fingerprint) else null
    val others = found.filter { BuildConfig.DEBUG || it.id == null || it.id != own }
    Section("Control another device") {
        if (others.isEmpty()) {
            Txt(
                "Looking for tablets on this Wi-Fi… On the tablet, turn on Edit from another device. Both need to be on the same " +
                    "network; guest networks usually keep devices apart.",
                Type.meta, Dash.muted, maxLines = 4,
            )
        } else {
            others.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                val isPaired = t.id?.let { Pairings.get(context, it) } != null
                NavRow(t.name + if (t.id == own) " (this device)" else "", if (isPaired) "Paired" else "Pair", icon = DashIcons.phone) {
                    showOverlay { RemoteEditor(t, onClose = { showOverlay(null) }) }
                }
            }
        }
    }
}

/** Full screen: connects to [tablet], pairs if needed, then edits its dashboard. */
@Composable
private fun RemoteEditor(tablet: FoundTablet, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = remember(tablet) { RemoteSession(context, tablet) }
    DisposableEffect(session) {
        session.start()
        onDispose { session.close() }
    }
    val state by session.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Dash.frame).imePadding().padding(Dash.gap)) {
        Row(Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(DashIcons.back, outline = false, onClick = onClose)
            HSpace(8.dp)
            Column(Modifier.weight(1f)) {
                Txt(tablet.name, Type.secondary, Dash.ink)
                Txt(
                    when (state) {
                        RemoteSession.State.Connecting -> "Connecting…"
                        is RemoteSession.State.NeedsCode -> "Not paired yet"
                        is RemoteSession.State.Ready -> "Connected · changes show on the tablet straight away"
                        is RemoteSession.State.Failed -> "Not connected"
                    },
                    Type.meta, Dash.muted,
                )
            }
        }
        Spacer(Modifier.height(Dash.gap))
        when (val s = state) {
            RemoteSession.State.Connecting -> Unit
            is RemoteSession.State.Failed -> Message(s.message) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton("Try again", Dash.ink, Dash.accent) { session.retry() }
                    if (tablet.id != null && Pairings.get(context, tablet.id) != null) {
                        MiniButton("Forget tablet") { Pairings.forget(context, tablet.id); session.retry() }
                    }
                }
            }
            is RemoteSession.State.NeedsCode -> CodeEntry(s.error) { session.pair(it) }
            is RemoteSession.State.Ready -> RemoteTabs(session, s.board)
        }
    }
}

/** What a phone can change on the tablet, one tab each; tabs the tablet's version lacks are hidden. */
@Composable
private fun RemoteTabs(session: RemoteSession, b: RemoteBoard) {
    val tabs = buildList {
        add("Layout")
        if (b.dates != null) add("Countdowns")
        if (b.alarm != null) add("Alarm")
    }
    var tab by remember { mutableStateOf("Layout") }
    var sheet by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    BackHandler(enabled = sheet != null) { sheet = null }
    // Tiles' own pop-ups (the note editor) open over this screen.
    CompositionLocalProvider(LocalOverlay provides { sheet = it }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (tabs.size > 1) {
                    Segmented(tabs, tab, { it }) { tab = it }
                    Spacer(Modifier.height(Dash.gap))
                }
                when (tab) {
                    "Countdowns" -> RemoteCountdowns(session, b.dates.orEmpty()) { sheet = it }
                    "Alarm" -> b.alarm?.let { RemoteAlarmPanel(session, it) { o -> sheet = o } }
                    else -> RemoteLayout(session, b) { sheet = it }
                }
            }
            sheet?.invoke()
        }
    }
}

/** Tiles whose content lives in the tile itself, so the phone can show and change it. */
private fun showsLive(t: TileSpec) = when (t.type) {
    "note", "clock", "worldclock" -> true
    "checklist" -> t.config["gtasks"] == null
    else -> false
}

@Composable
private fun RemoteLayout(session: RemoteSession, b: RemoteBoard, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if ("presets" in b.features && b.presets.size > 1) {
            Box(Modifier.weight(1f)) { Chips(b.presets, { it.first == b.active }, { it.second }) { session.setActive(it.first) } }
        } else {
            Txt(
                if (editing) "Drag to move, drag the corner to resize, tap for settings." else "Tap a tile for its settings; hold one to rearrange.",
                Type.meta, Dash.muted, Modifier.weight(1f), maxLines = 2,
            )
        }
        HSpace(8.dp)
        if (editing && "tiles" in b.features) {
            MiniButton("Add tile", Dash.ink) {
                showSheet {
                    ModulePicker(onClose = { showSheet(null) }) { m ->
                        showSheet(null)
                        val placed = withNewTile(b.layout, m)
                        if (placed != null) session.setLayout(placed)
                        else android.widget.Toast.makeText(context, "No room for ${m.name}: shrink or remove a tile first", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            HSpace(8.dp)
        }
        MiniButton(if (editing) "Done" else "Edit layout", Dash.ink, if (editing) Dash.accent else Dash.line) { editing = !editing }
    }
    Spacer(Modifier.height(Dash.gap))
    // The tablet's board at its real size, scaled to fit: the same layout, just smaller.
    BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / b.widthDp, maxHeight.value / b.heightDp)
        Box(Modifier.requiredSize(b.widthDp.dp, b.heightDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            BoardView(
                layout = b.layout,
                editing = editing,
                onStartEditing = { editing = true },
                onChange = { session.setLayout(it) },
                onTileSettings = { spec ->
                    if ("tiles" !in b.features) return@BoardView
                    showSheet {
                        // Follows the session, so changes made here show in the open panel.
                        val state by session.state.collectAsStateWithLifecycle()
                        val current = (state as? RemoteSession.State.Ready)?.board?.layout?.tiles?.firstOrNull { it.id == spec.id }
                        if (current == null) showSheet(null)
                        else TileSettingsPanel(current, onClose = { showSheet(null) }, update = { block -> session.updateTile(spec.id, block) }) {
                            (session.state.value as? RemoteSession.State.Ready)?.board?.layout?.let { l ->
                                session.setLayout(l.copy(tiles = l.tiles.filter { it.id != spec.id }))
                            }
                            showSheet(null)
                        }
                    }
                },
                onTileConfig = { spec, cfg -> if ("tiles" in b.features) session.updateTile(spec.id) { it.copy(config = cfg) } },
                modifier = Modifier.fillMaxSize(),
                live = { "tiles" in b.features && showsLive(it) },
            )
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
private fun RemoteCountdowns(session: RemoteSession, dates: List<RemoteDate>, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var category by remember { mutableStateOf(Category.OTHER) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        Section("Add a date") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(title, { title = it }, "What's happening", UpcomingDate.TITLE_MAX, Modifier.weight(1f))
                HSpace(8.dp)
                PickerField(date?.format(dateFmt), "Date", Modifier.width(150.dp)) {
                    showSheet { DatePickerOverlay(date, LocalDate.now(), onPick = { date = it; showSheet(null) }) { showSheet(null) } }
                }
            }
            Spacer(Modifier.height(10.dp))
            Chips(Category.entries, { it == category }, { it.label }) { category = it }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add") {
                val d = date
                if (title.isNotBlank() && d != null) {
                    session.saveDate(null, title.trim(), d, null, category.name)
                    title = ""; date = null; category = Category.OTHER
                }
            }
        }
        Section("On the tablet") {
            if (dates.isEmpty()) Txt("No upcoming dates.", Type.meta, Dash.muted)
            dates.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d.date)
                SettingRow(d.title, d.date.format(dateFmt) + " · " + when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }) {
                    IconButton(DashIcons.trash, size = 30.dp, iconSize = 13.dp) { session.deleteDate(d.id) }
                }
            }
        }
    }
}

private val ringFmt = DateTimeFormatter.ofPattern("EEE dd MMM 'at' HH:mm", Locale.ENGLISH)

@Composable
private fun RemoteAlarmPanel(session: RemoteSession, a: RemoteAlarm, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    Section(null) {
        SettingRow("Alarm", if (a.enabled) a.next?.let { "Rings ${it.format(ringFmt)}" } ?: "On" else "Off") {
            Switch(a.enabled, { session.setAlarm(enabled = !a.enabled) })
        }
        RowDivider()
        NavRow("Time", "%02d:%02d".format(a.time.hour, a.time.minute)) {
            showSheet { TimePickerOverlay(a.time, onPick = { session.setAlarm(time = it); showSheet(null) }, onClear = null) { showSheet(null) } }
        }
        RowDivider()
        SettingRow("Repeat", if (a.days.isEmpty()) "Once" else null) {}
        Chips(DayOfWeek.entries, { it in a.days }, { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }) { d ->
            session.setAlarm(days = if (d in a.days) a.days - d else a.days + d)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Message(text: String, actions: @Composable () -> Unit) {
    Section(null) {
        Txt(text, Type.body, Dash.ink, maxLines = 4)
        Spacer(Modifier.height(14.dp))
        actions()
    }
}

@Composable
private fun CodeEntry(error: String?, onPair: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Section(null) {
        Txt("Enter the code shown on the tablet", Type.body, Dash.ink)
        Spacer(Modifier.height(4.dp))
        Txt("On the tablet: Settings › Phone control › Pair.", Type.meta, Dash.muted, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(code, { code = it }, "6-digit code", 6, Modifier.width(180.dp), numeric = true)
            HSpace(12.dp)
            PrimaryButton("Pair") { if (code.length == 6) onPair(code) }
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Txt(it, Type.meta, Dash.danger, maxLines = 3)
        }
    }
}
