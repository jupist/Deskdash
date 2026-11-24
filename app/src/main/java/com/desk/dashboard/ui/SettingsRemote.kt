package com.desk.dashboard.ui

import androidx.compose.foundation.background
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
            "A phone with Deskdash on the same Wi-Fi can change this dashboard's layout and preset. Only phones you pair can connect.",
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
            is RemoteSession.State.Ready -> {
                val b = s.board
                if ("presets" in b.features && b.presets.size > 1) {
                    Chips(b.presets, { it.first == b.active }, { it.second }) { session.setActive(it.first) }
                    Spacer(Modifier.height(Dash.gap))
                }
                Txt("Drag a tile to move it, drag its corner to resize, × to remove.", Type.meta, Dash.muted)
                Spacer(Modifier.height(Dash.gap))
                // The tablet's board at its real size, scaled to fit: the same layout, just smaller.
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val scale = minOf(maxWidth.value / b.widthDp, maxHeight.value / b.heightDp)
                    Box(Modifier.requiredSize(b.widthDp.dp, b.heightDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                        BoardView(
                            layout = b.layout,
                            editing = true,
                            onStartEditing = {},
                            onChange = { session.setLayout(it) },
                            onTileSettings = {},
                            onTileConfig = { _, _ -> },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
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
