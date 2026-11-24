package com.desk.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.desk.dashboard.board.GridLayout
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
            "A phone with Deskdash on the same Wi-Fi can change this dashboard: its layout, tiles, important dates and alarm. Only phones you pair can connect.",
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
    var sheet by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var editingLayout by remember { mutableStateOf(false) }
    BackHandler(enabled = editingLayout && sheet == null) { editingLayout = false }
    BackHandler(enabled = sheet != null) { sheet = null }
    // Tiles' own pop-ups (the note editor) open over this screen.
    CompositionLocalProvider(LocalOverlay provides { sheet = it }) {
        Box(Modifier.fillMaxSize().background(Dash.frame)) {
            val ready = state as? RemoteSession.State.Ready
            if (ready != null && editingLayout) {
                RemoteLayoutEditor(session, ready.board, onDone = { editingLayout = false }) { sheet = it }
            } else {
                RemoteHome(tablet, session, state, onClose, onEditLayout = { editingLayout = true }) { sheet = it }
            }
            sheet?.invoke()
        }
    }
}

@Composable
private fun RemoteHome(
    tablet: FoundTablet, session: RemoteSession, state: RemoteSession.State, onClose: () -> Unit,
    onEditLayout: () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().imePadding().padding(Dash.gap)) {
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
            is RemoteSession.State.Ready -> RemoteTabs(session, s.board, onEditLayout, showSheet)
        }
    }
}

/** What a phone can change on the tablet, one tab each; tabs the tablet's version lacks are hidden. */
@Composable
private fun RemoteTabs(session: RemoteSession, b: RemoteBoard, onEditLayout: () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    val tabs = buildList {
        add("Layout")
        if (b.dates != null) add("Important dates")
        if (b.alarm != null) add("Alarm")
    }
    var tab by remember { mutableStateOf("Layout") }
    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            Segmented(tabs, tab, { it }) { tab = it }
            Spacer(Modifier.height(Dash.gap))
        }
        when (tab) {
            "Important dates" -> RemoteCountdowns(session, b.dates.orEmpty(), showSheet)
            "Alarm" -> b.alarm?.let { RemoteAlarmPanel(session, it, showSheet) }
            else -> RemoteLayout(session, b, onEditLayout, showSheet)
        }
    }
}

/** Tiles whose content lives in the tile itself, so the phone can show and change it. */
private fun showsLive(t: TileSpec) = when (t.type) {
    "note", "clock", "worldclock" -> true
    "checklist" -> t.config["gtasks"] == null
    else -> false
}

/** The tablet's board at its real size, scaled to fit: the same layout, just smaller. */
@Composable
private fun ScaledBoard(
    session: RemoteSession, b: RemoteBoard, editing: Boolean, modifier: Modifier,
    onChange: (GridLayout) -> Unit, onStartEditing: () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / b.widthDp, maxHeight.value / b.heightDp)
        Box(Modifier.requiredSize(b.widthDp.dp, b.heightDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            BoardView(
                layout = b.layout,
                editing = editing,
                onStartEditing = onStartEditing,
                onChange = onChange,
                onTileSettings = { spec -> if ("tiles" in b.features) showSheet { RemoteTileSettings(session, spec.id, showSheet) } },
                onTileConfig = { spec, cfg -> if ("tiles" in b.features) session.updateTile(spec.id) { it.copy(config = cfg) } },
                modifier = Modifier.fillMaxSize(),
                live = { "tiles" in b.features && showsLive(it) },
            )
        }
    }
}

@Composable
private fun RemoteTileSettings(session: RemoteSession, id: String, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    // Follows the session, so changes made here show in the open panel.
    val state by session.state.collectAsStateWithLifecycle()
    val current = (state as? RemoteSession.State.Ready)?.board?.layout?.tiles?.firstOrNull { it.id == id }
    if (current == null) { showSheet(null); return }
    TileSettingsPanel(current, onClose = { showSheet(null) }, update = { block -> session.updateTile(id, block) }) {
        (session.state.value as? RemoteSession.State.Ready)?.board?.layout?.let { l ->
            session.setLayout(l.copy(tiles = l.tiles.filter { it.id != id }))
        }
        showSheet(null)
    }
}

@Composable
private fun RemoteLayout(session: RemoteSession, b: RemoteBoard, onEditLayout: () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if ("presets" in b.features && b.presets.size > 1) {
            Box(Modifier.weight(1f)) { Chips(b.presets, { it.first == b.active }, { it.second }) { session.setActive(it.first) } }
        } else {
            Txt("Tap a tile for its settings.", Type.meta, Dash.muted, Modifier.weight(1f))
        }
        HSpace(8.dp)
        PrimaryButton("Edit layout", onClick = onEditLayout)
    }
    Spacer(Modifier.height(Dash.gap))
    ScaledBoard(session, b, editing = false, Modifier.fillMaxWidth().fillMaxHeight(), { session.setLayout(it) }, onEditLayout, showSheet)
}

/** Full screen, like editing the layout on the tablet itself: the edit bar above the tablet's board. */
@Composable
private fun RemoteLayoutEditor(session: RemoteSession, b: RemoteBoard, onDone: () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val undo = remember { mutableStateListOf<GridLayout>() }
    fun change(new: GridLayout) {
        undo += b.layout
        if (undo.size > 30) undo.removeAt(0)
        session.setLayout(new)
    }
    Column(Modifier.fillMaxSize().padding(Dash.gap), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        EditBar(
            presetName = b.presets.firstOrNull { it.first == b.active }?.second ?: "Dashboard",
            hidden = emptyList(), onPutBack = {},
            canUndo = undo.isNotEmpty(),
            onUndo = { undo.removeLastOrNull()?.let { session.setLayout(it) } },
            onAdd = {
                showSheet {
                    ModulePicker(onClose = { showSheet(null) }) { m ->
                        showSheet(null)
                        val placed = withNewTile(b.layout, m)
                        if (placed != null) change(placed)
                        else android.widget.Toast.makeText(context, "No room for ${m.name}: shrink or remove a tile first", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDone = onDone,
            presetMenu = if ("presets" in b.features) { dismiss ->
                Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
                    Column(Modifier.width(220.dp).clip(Dash.smallShape).background(Dash.tile).border(1.dp, Dash.line, Dash.smallShape)) {
                        b.presets.forEach { (id, name) ->
                            Txt(
                                name, Type.body, if (id == b.active) Dash.accentText else Dash.ink,
                                Modifier.fillMaxWidth().clickable { session.setActive(id); undo.clear(); dismiss() }.padding(12.dp),
                            )
                        }
                    }
                }
            } else null,
        )
        ScaledBoard(session, b, editing = true, Modifier.fillMaxWidth().weight(1f), ::change, {}, showSheet)
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
