package com.desk.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import com.desk.dashboard.board.DashActions
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.remote.RemoteContext
import com.desk.dashboard.remote.RemoteMode
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.desk.dashboard.board.GridLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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

/** Settings › Remote control: let phones edit this dashboard, or edit another one from here. */
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
                        Txt("On the phone: Settings › Remote control, then tap this device. The code lasts 5 minutes.", Type.meta, Dash.muted, Modifier.weight(1f), maxLines = 3)
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
    val primary by RemoteMode.primary.collectAsStateWithLifecycle()
    Section("Control another device") {
        SwitchRow(
            "Use this device primarily as a remote for your tablet",
            "Opens straight onto your tablet instead of this device's dashboard. Turn it off from the remote screen.",
            primary,
        ) { RemoteMode.setPrimary(context, !primary) }
        RowDivider()
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
                    RemoteMode.start(context, t)
                }
            }
        }
    }
}

private sealed interface RemoteScreen {
    data object Home : RemoteScreen
    data class Settings(val section: String?) : RemoteScreen
}

/**
 * Remote control mode, in place of the app's own screens: finds the tablet, connects (pairing
 * the first time), then shows its dashboard and settings. Everything below runs with a
 * [RemoteContext], so the app's usual screens read and change the tablet.
 */
@Composable
fun RemoteShell(target: RemoteMode.Target, primary: Boolean) {
    val context = LocalContext.current
    val onExit = { RemoteMode.leave(context) }
    var tablet by remember(target) { mutableStateOf(RemoteMode.lastSeen?.takeIf { RemoteMode.matches(target, it) }) }
    val t = tablet
    if (t == null) {
        // Find it on the network first (its address can change between visits).
        val discovery = remember { Discovery(context) }
        DisposableEffect(Unit) {
            discovery.start()
            onDispose { discovery.stop() }
        }
        val found by discovery.found.collectAsStateWithLifecycle()
        LaunchedEffect(found) { found.firstOrNull { RemoteMode.matches(target, it) }?.let { RemoteMode.lastSeen = it; tablet = it } }
        // A dedicated remote goes back to its list of tablets (marked offline) rather than waiting here.
        if (primary) LaunchedEffect(Unit) { kotlinx.coroutines.delay(5_000); RemoteMode.leave(context) }
        Column(Modifier.fillMaxSize().background(Dash.frame).padding(Dash.gap)) {
            ShellBar(target.name, "Looking for it on this Wi-Fi…", onSettings = null, primary)
            Spacer(Modifier.height(Dash.gap))
            Message("Make sure ${target.name} is on, on the same Wi-Fi, and has Edit from another device turned on (Settings › Remote control).") {}
        }
        return
    }

    val session = remember(t) { RemoteSession(context, t) }
    DisposableEffect(session) {
        session.start()
        onDispose { session.close() }
    }
    val remote = remember(session) { RemoteContext(context, session) }
    val state by session.state.collectAsStateWithLifecycle()
    // Find the tablet afresh: after a restart or update it listens on a new port.
    val rediscover = { RemoteMode.lastSeen = null; tablet = null }
    LaunchedEffect(state) {
        if ((state as? RemoteSession.State.Failed)?.retryable == true) { kotlinx.coroutines.delay(2_000); rediscover() }
    }
    var screen by remember { mutableStateOf<RemoteScreen>(RemoteScreen.Home) }
    var sheet by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var editing by remember { mutableStateOf(false) }
    BackHandler(enabled = editing && sheet == null) { editing = false }
    BackHandler(enabled = sheet != null) { sheet = null }
    val actions = remember {
        DashActions(
            openSettings = { section -> sheet = null; editing = false; screen = RemoteScreen.Settings(section) },
            lock = {},
            openApps = {},
            editLayout = { sheet = null; screen = RemoteScreen.Home; editing = true },
        )
    }
    CompositionLocalProvider(
        LocalContext provides remote,
        LocalDashActions provides actions,
        LocalOverlay provides { sheet = it },
    ) {
        Box(Modifier.fillMaxSize().background(Dash.frame)) {
            val ready = state as? RemoteSession.State.Ready
            when (val sc = screen) {
                is RemoteScreen.Settings -> SettingsScreen(sc.section, onDone = { screen = RemoteScreen.Home })
                RemoteScreen.Home -> if (ready != null) RemoteDashboard(
                    session, ready.board, editing, { editing = it },
                    top = { ShellBar(t.name, null, onSettings = { screen = RemoteScreen.Settings(null) }, primary) },
                ) { sheet = it } else Column(Modifier.fillMaxSize().imePadding().padding(Dash.gap)) {
                    ShellBar(
                        t.name,
                        when (state) {
                            RemoteSession.State.Connecting -> "Connecting…"
                            is RemoteSession.State.NeedsCode -> "Not paired yet"
                            is RemoteSession.State.Ready -> null
                            is RemoteSession.State.Failed -> "Not connected"
                        },
                        onSettings = if (ready != null) ({ screen = RemoteScreen.Settings(null) }) else null,
                        primary = primary,
                    )
                    Spacer(Modifier.height(Dash.gap))
                    when (val s = state) {
                        RemoteSession.State.Connecting -> Unit
                        is RemoteSession.State.Failed -> Message(s.message) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniButton("Try again", Dash.ink, Dash.accent) { rediscover() }
                                if (t.id != null && Pairings.get(context, t.id) != null) {
                                    MiniButton("Forget tablet") { Pairings.forget(context, t.id); session.retry() }
                                }
                            }
                        }
                        is RemoteSession.State.NeedsCode -> CodeEntry(s.error) { session.pair(it) }
                        is RemoteSession.State.Ready -> Unit
                    }
                }
            }
            sheet?.invoke()
        }
    }
}

/** Top of remote control: which tablet, how it's going, its settings, and leaving remote control. */
@Composable
private fun ShellBar(name: String, status: String?, onSettings: (() -> Unit)?, primary: Boolean) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(name, Type.secondary, Dash.ink)
            if (status != null) Txt(status, Type.meta, Dash.muted)
        }
        if (onSettings != null) {
            OutlineButton("Settings", DashIcons.gear, onSettings)
            HSpace(10.dp)
        }
        if (!primary) OutlineButton("Exit remote", DashIcons.close) { RemoteMode.leave(context) }
        else {
            // A dedicated remote: switch tablets (when there's a choice), or stop being a remote.
            if (remember { Pairings.all(context).size } > 1) {
                OutlineButton("Tablets", DashIcons.grid) { RemoteMode.leave(context) }
                HSpace(10.dp)
            }
            OutlineButton("Turn off remote mode", DashIcons.close) { RemoteMode.setPrimary(context, false) }
        }
    }
}

/** A dedicated remote with no tablet open: the paired tablets to choose from, and others to pair. */
@Composable
fun RemotePicker() {
    val context = LocalContext.current
    val discovery = remember { Discovery(context) }
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val found by discovery.found.collectAsStateWithLifecycle()
    val paired = remember { Pairings.all(context) }
    // With just one tablet, open it as soon as it's online; until then this list shows it offline.
    LaunchedEffect(found) {
        paired.singleOrNull()?.let { p -> found.firstOrNull { it.id == p.id }?.let { RemoteMode.start(context, it) } }
    }
    val others = found.filter { f -> paired.none { it.id == f.id } && f.id != RemoteHost.fingerprint.let(Protocol::hostId).takeIf { RemoteHost.enabled.value } }
    Column(Modifier.fillMaxSize().background(Dash.frame).padding(Dash.gap).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        Row(Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Your tablets", Type.secondary, Dash.ink, Modifier.weight(1f))
            OutlineButton("Turn off remote mode", DashIcons.close) { RemoteMode.setPrimary(context, false) }
        }
        if (paired.isNotEmpty()) Section("Paired") {
            paired.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                val here = found.firstOrNull { it.id == p.id }
                NavRow(p.name, if (here != null) "Online" else "Offline", icon = DashIcons.phone) {
                    if (here != null) RemoteMode.start(context, here) else RemoteMode.open(context, p)
                }
            }
        }
        Section(if (paired.isEmpty()) "Tablets on this Wi-Fi" else "Pair another tablet") {
            if (others.isEmpty()) Txt(
                "Looking for tablets on this Wi-Fi… On the tablet, turn on Edit from another device in Settings › Remote control.",
                Type.meta, Dash.muted, maxLines = 3,
            )
            others.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                NavRow(t.name, "Pair", icon = DashIcons.phone) { RemoteMode.start(context, t) }
            }
        }
    }
}

/** Tiles whose content lives in the tile itself, so the phone can show and change it. */
private fun showsLive(t: TileSpec, b: RemoteBoard) = when (t.type) {
    "note", "clock", "worldclock" -> true
    // These read the tablet's settings through the remote context.
    "alarm" -> "prefs" in b.features
    "countdown" -> b.dates != null
    "checklist" -> t.config["gtasks"] == null && t.config["tasks"] == null
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
                live = { "tiles" in b.features && showsLive(it, b) },
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

/**
 * The tablet's dashboard, worked like the tablet's own: tap a tile to change it, long-press one to
 * rearrange (it's picked up in the same press). While editing, the edit bar replaces [top].
 */
@Composable
private fun RemoteDashboard(
    session: RemoteSession, b: RemoteBoard, editing: Boolean, onEditing: (Boolean) -> Unit,
    top: @Composable () -> Unit, showSheet: ((@Composable () -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val undo = remember { mutableStateListOf<GridLayout>() }
    fun change(new: GridLayout) {
        undo += b.layout
        if (undo.size > 30) undo.removeAt(0)
        session.setLayout(new)
    }
    Column(Modifier.fillMaxSize().imePadding().padding(Dash.gap), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        if (!editing) top() else EditBar(
            presetName = b.presets.firstOrNull { it.first == b.active }?.second ?: "Dashboard",
            hidden = emptyList(), onPutBack = {},
            canUndo = undo.isNotEmpty(),
            onUndo = { undo.removeLastOrNull()?.let { session.setLayout(it) } },
            onAdd = {
                showSheet {
                    ModulePicker(onClose = { showSheet(null) }, fits = { m -> withNewTile(b.layout, m) != null }) { m ->
                        showSheet(null)
                        withNewTile(b.layout, m)?.let(::change)
                    }
                }
            },
            onDone = { onEditing(false); undo.clear() },
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
        // One board for both modes, so a long press can enter edit mode and carry on dragging.
        ScaledBoard(session, b, editing, Modifier.fillMaxWidth().weight(1f), ::change, { onEditing(true) }, showSheet)
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

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
        Txt("On the tablet: Settings › Remote control › Pair.", Type.meta, Dash.muted, maxLines = 2)
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
