package com.desk.dashboard.modules

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.media.Ambient
import com.desk.dashboard.media.AmbientMode
import com.desk.dashboard.media.MediaRepo
import com.desk.dashboard.media.NowPlaying
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.system.Apps
import com.desk.dashboard.ui.Overlay
import com.desk.dashboard.ui.PanelHeader
import com.desk.dashboard.ui.SettingRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.desk.dashboard.system.Lock
import com.desk.dashboard.ui.BrightnessSlider
import com.desk.dashboard.ui.CircleButton
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.TileStyle
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.VolumeSlider
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MusicModule : Module(
    "music", "Now playing", "Album art and controls for Spotify or any player.",
    DashIcons.music, Size(4, 4), Size(2, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val repo = remember { MediaRepo.get(context) }
        var access by remember { mutableStateOf(repo.hasAccess()) }
        // Listen to sessions only while the dashboard is in front.
        LifecycleResumeEffect(repo) {
            access = repo.hasAccess()
            repo.start()
            onPauseOrDispose { repo.stop() }
        }
        val np by repo.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var resuming by remember { mutableStateOf(false) }
        var resumeFailed by remember { mutableStateOf(false) }
        val playing = np
        val showArt = t.bool("art", true)
        val overlay = LocalOverlay.current
        // "" means any app (no resume); null means not chosen yet.
        val app = t.strOrNull("app")
        LaunchedEffect(app) { repo.preferred = app?.ifEmpty { null } }
        val appName = app?.takeIf { it.isNotEmpty() }?.let { appLabel(context, it) }
        val choose = { overlay { MusicAppPicker(repo, onPick = { t.set("app", it); overlay(null) }) { overlay(null) } } }

        Tile(label(t), meta = playing?.let { appLabel(context, it.pkg) }) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                val tiny = maxHeight < 70.dp
                when {
                    !access -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Txt("Notification access is off", Type.display16, tc.ink)
                        if (!tiny) Txt("Needed to show what's playing", Type.bodySmall, tc.muted, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Box { MiniButton("Allow access", tc.ink, tc.accentFill) { context.startActivity(repo.accessIntent()) } }
                    }
                    app == null && playing == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Txt("Which music app?", Type.display16, tc.ink)
                        if (!tiny) Txt("Pick the app this tile resumes; it still shows whatever is playing", Type.bodySmall, tc.muted, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Box { MiniButton("Choose app", tc.ink, tc.accentFill, choose) }
                    }
                    playing == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Txt("Nothing playing", Type.display16, tc.ink)
                        if (!tiny) {
                            Txt(
                                when {
                                    resuming -> "Waking $appName…"
                                    resumeFailed -> "$appName didn't respond"
                                    appName != null -> "Resume plays your last $appName track"
                                    else -> "Play something in any music app"
                                },
                                Type.bodySmall, tc.muted, maxLines = 2,
                            )
                        }
                        if (app != null && app.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniButton("Resume", tc.ink, tc.accentFill) {
                                    if (resuming) return@MiniButton
                                    resuming = true
                                    resumeFailed = false
                                    // Plays in the background; never opens the app.
                                    scope.launch { resumeFailed = !repo.resume(app); resuming = false }
                                }
                                if (boxW > 200.dp) MiniButton("Open $appName", tc.muted, tc.line) { openApp(context, app) }
                            }
                        }
                    }
                    tiny || (maxWidth < 120.dp && maxHeight < 120.dp) -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(if (playing.playing) DashIcons.pause else DashIcons.play, tc.ink, tc.fill, 40.dp, 14.dp) { repo.togglePlay() }
                        if (boxW > 110.dp) {
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(playing.title, Type.display16, tc.ink)
                                Txt(playing.artist, Type.meta, tc.muted)
                            }
                        }
                    }
                    maxWidth > maxHeight * 1.4f || maxHeight < 200.dp -> WidePlayer(playing, repo, showArt, maxHeight)
                    else -> TallPlayer(playing, repo, showArt)
                }
            }
        }
    }

    @Composable
    private fun TallPlayer(np: NowPlaying, repo: MediaRepo, showArt: Boolean) {
        val context = LocalContext.current
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // As large as fits, in the art's own shape (square albums, wide video thumbnails).
                if (showArt) Art(np, maxWidth, maxHeight) { openApp(context, np.pkg) }
            }
            Spacer(Modifier.height(12.dp))
            Txt(np.title, Type.display16.copy(fontSize = Type.display20.fontSize), tc.ink)
            Txt(listOf(np.artist, np.album).filter { it.isNotBlank() }.joinToString(" — "), Type.bodySmall, tc.muted)
            Progress(np)
            Spacer(Modifier.height(10.dp))
            Controls(repo, np, 52.dp)
        }
    }

    @Composable
    private fun WidePlayer(np: NowPlaying, repo: MediaRepo, showArt: Boolean, height: Dp) {
        val context = LocalContext.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowW = maxWidth
            Row(Modifier.fillMaxSize().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                if (showArt) {
                    // Full height, in its own shape, but leaving at least half the width for the text.
                    Art(np, rowW * 0.5f, height - 10.dp) { openApp(context, np.pkg) }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Txt(np.title, Type.display16.copy(fontSize = Type.display20.fontSize), tc.ink)
                    Txt(listOf(np.artist, np.album).filter { it.isNotBlank() }.joinToString(" — "), Type.bodySmall, tc.muted)
                    if (height > 110.dp) Progress(np)
                    Spacer(Modifier.height(8.dp))
                    Controls(repo, np, if (height > 110.dp) 46.dp else 38.dp)
                }
            }
        }
    }

    /** The art at its own aspect ratio, as large as fits in [maxW] × [maxH]; a square placeholder without art. */
    @Composable
    private fun Art(np: NowPlaying, maxW: Dp, maxH: Dp, onClick: () -> Unit) {
        val art = np.art
        val aspect = if (art != null && art.height > 0) art.width.toFloat() / art.height else 1f
        val w = min(maxW, maxH * aspect)
        val h = w / aspect
        Box(Modifier.size(w, h).clip(com.desk.dashboard.ui.Dash.smallShape).background(tc.line).pressable(onClick, 0.97f)) {
            if (art != null) Image(art, null, Modifier.size(w, h), contentScale = ContentScale.Fit)
            else Box(Modifier.size(w, h), contentAlignment = Alignment.Center) { Icon(DashIcons.music, tc.muted, w / 4) }
        }
    }

    @Composable
    private fun Controls(repo: MediaRepo, np: NowPlaying, big: Dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleButton(DashIcons.prev, tc.fill, tc.ink, big * 0.77f, 15.dp, outline = tc.line) { repo.previous() }
            CircleButton(if (np.playing) DashIcons.pause else DashIcons.play, tc.ink, tc.fill, big, 16.dp) { repo.togglePlay() }
            CircleButton(DashIcons.next, tc.fill, tc.ink, big * 0.77f, 15.dp, outline = tc.line) { repo.next() }
        }
    }

    /** Only this part ticks (once per second, and only while playing). */
    @Composable
    private fun Progress(np: NowPlaying) {
        var pos by remember { mutableLongStateOf(np.positionNow()) }
        LaunchedEffect(np) {
            pos = np.positionNow()
            while (np.playing) {
                delay((1000 - pos % 1000).coerceAtLeast(50))
                pos = np.positionNow()
            }
        }
        val frac = if (np.durationMs > 0) (pos.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(tc.line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(tc.accentFill))
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth()) {
            Txt(mmss(pos), Type.meta, tc.muted, Modifier.weight(1f))
            if (np.durationMs > 0) Txt(mmss(np.durationMs), Type.meta, tc.muted)
        }
    }

    private fun mmss(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    @Composable
    override fun Settings(t: TileScope) {
        val context = LocalContext.current
        val overlay = LocalOverlay.current
        val repo = remember { MediaRepo.get(context) }
        val app = t.strOrNull("app")
        SettingRow("Music app", when { app == null -> "Not chosen"; app.isEmpty() -> "Any app"; else -> appLabel(context, app) }) {
            MiniButton("Choose", Dash.ink, Dash.accent) {
                overlay { MusicAppPicker(repo, onPick = { t.set("app", it); overlay(null) }) { overlay(null) } }
            }
        }
        SwitchRow("Album art", checked = t.bool("art", true), onToggle = { t.set("art", !t.bool("art", true)) })
    }
}

fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** Installed music apps to pick from, plus "Any app". */
@Composable
fun MusicAppPicker(repo: MediaRepo, onPick: (String) -> Unit, onClose: () -> Unit) {
    val apps = remember { repo.mediaApps() }
    val icons by Apps.list.collectAsStateWithLifecycle()
    Overlay(onDismiss = onClose, width = 460.dp) {
        PanelHeader("Your music app", onClose)
        Txt("Resume wakes this app and plays your last track. The tile still shows whatever is playing.", Type.meta, Dash.muted, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(apps, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = icons?.firstOrNull { it.component.packageName == a.pkg }?.icon
                    if (icon != null) Image(icon, null, Modifier.size(32.dp)) else Box(Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Txt(a.label, Type.body, Dash.ink, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(DashIcons.music, Dash.muted, 18.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Txt("Any app", Type.body, Dash.ink)
                        Txt("Show whatever plays; no Resume button", Type.meta, Dash.muted)
                    }
                }
            }
        }
        if (apps.isEmpty()) Txt("No music apps found", Type.meta, Dash.muted)
    }
}

/** Opens [pkg]; if it isn't installed, its store page. */
fun openApp(context: Context, pkg: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

object AmbientModule : Module(
    "ambient", "Ambient sound", "Rain or soft noise, looping with no gap. Tap to cycle.",
    DashIcons.waves, Size(1, 1), Size(1, 1), TileStyle.ALT,
) {
    @Composable
    override fun Content(t: TileScope) {
        val mode by Ambient.mode.collectAsStateWithLifecycle()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxW = maxWidth; val boxH = maxHeight
            if (maxWidth < 160.dp || maxHeight < 100.dp) {
                // Compact: tap anywhere cycles Off → Noise → Rain.
                Column(Modifier.fillMaxSize().pressable(Ambient::cycle).padding(10.dp)) {
                    Txt("AMBIENT", Type.small.copy(letterSpacing = Type.label.letterSpacing), tc.ink)
                    Spacer(Modifier.weight(1f))
                    Txt(mode.short, Type.display16, tc.ink)
                    Spacer(Modifier.height(5.dp))
                    Dots(mode)
                }
            } else {
                Tile(label(t)) {
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmbientMode.entries.forEach { m ->
                            val on = m == mode
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(Dash.smallShape)
                                    .background(if (on) tc.ink else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(1.dp, tc.ink.copy(alpha = 0.4f), Dash.smallShape)
                                    .pressable({ Ambient.set(m) }),
                                contentAlignment = Alignment.Center,
                            ) { Txt(m.short, Type.meta, if (on) tc.fill else tc.ink) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Dots(mode: AmbientMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AmbientMode.entries.forEach { m ->
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .border(1.dp, tc.ink, CircleShape)
                        .background(if (m == mode) tc.ink else tc.fill)
                )
            }
        }
    }
}

object ControlsModule : Module(
    "controls", "Controls", "Volume and brightness sliders, and a lock button.",
    DashIcons.sliders, Size(4, 1), Size(2, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val actions = LocalDashActions.current
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Dash.tilePadH, vertical = 8.dp)) {
            val boxW = maxWidth; val boxH = maxHeight
            val stacked = maxHeight > 90.dp
            if (stacked) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    VolumeSlider(Modifier.fillMaxWidth(), fill = true)
                    BrightnessSlider(Modifier.fillMaxWidth(), fill = true)
                    if (t.bool("lock", true) && boxH > 140.dp) {
                        Box { MiniButton("Lock screen", tc.ink, tc.line) { actions?.lock?.invoke() ?: Lock.lockOrAsk(context) } }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VolumeSlider(Modifier.weight(1f), fill = true)
                    if (boxW > 360.dp) BrightnessSlider(Modifier.weight(1f), fill = true)
                    if (t.bool("lock", true)) {
                        CircleButton(DashIcons.lock, tc.fill, tc.ink, 36.dp, 14.dp, outline = tc.line) {
                            actions?.lock?.invoke() ?: Lock.lockOrAsk(context)
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        SwitchRow("Lock button", "Needs device admin (asked on first use)", t.bool("lock", true)) { t.set("lock", !t.bool("lock", true)) }
    }
}
