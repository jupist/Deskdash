package com.desk.dashboard.ui.tiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.media.MediaRepo
import com.desk.dashboard.media.NowPlaying
import com.desk.dashboard.media.SPOTIFY
import com.desk.dashboard.ui.CircleButton
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val titleStyle = Type.display16.copy(fontSize = 18.sp, lineHeight = 22.sp)

@Composable
fun MusicTile(modifier: Modifier = Modifier) {
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
    val playing = np

    Tile("Now playing", modifier, meta = playing?.let { sourceName(context, it.pkg) } ?: "Spotify") {
        when {
            !access -> {
                Spacer(Modifier.weight(1f))
                Txt("Notification access is off", Type.display16, Dash.ink)
                Txt("Needed to show and control Spotify", Type.bodySmall, Dash.muted)
                Spacer(Modifier.height(14.dp))
                Box { MiniButton("Allow access", Dash.ink, Dash.slate) { context.startActivity(repo.accessIntent()) } }
            }
            playing == null -> {
                Spacer(Modifier.weight(1f))
                Txt("Nothing playing", Type.display16, Dash.ink)
                Txt(if (resuming) "Waking Spotify…" else "Resume plays your last Spotify track", Type.bodySmall, Dash.muted)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniButton("Resume Spotify", Dash.ink, Dash.slate) {
                        if (resuming) return@MiniButton
                        resuming = true
                        scope.launch {
                            // Plays in the background; only opens the app if Spotify can't be woken.
                            if (!repo.resumeSpotify()) openSpotify(context)
                            resuming = false
                        }
                    }
                    MiniButton("Open Spotify") { openSpotify(context) }
                }
            }
            else -> Playing(playing, repo)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Playing(np: NowPlaying, repo: MediaRepo) {
    Spacer(Modifier.height(14.dp))
    // Art takes whatever height is left, as a square no wider than the tile.
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val side = min(maxWidth, maxHeight)
        // Tapping the art opens Spotify.
        val context = LocalContext.current
        Box(Modifier.size(side).background(Dash.line).clickable { openSpotify(context) }) {
            np.art?.let { Image(it, null, Modifier.size(side), contentScale = ContentScale.Crop) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Txt(np.title, titleStyle, Dash.ink)
    Txt(listOf(np.artist, np.album).filter { it.isNotBlank() }.joinToString(" — "), Type.bodySmall, Dash.muted)
    Progress(np)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CircleButton(DashIcons.prev, Dash.tile, Dash.ink, 40.dp, 16.dp, outline = Dash.line) { repo.previous() }
        CircleButton(if (np.playing) DashIcons.pause else DashIcons.play, Dash.ink, Dash.tile, 52.dp, 16.dp) { repo.togglePlay() }
        CircleButton(DashIcons.next, Dash.tile, Dash.ink, 40.dp, 16.dp, outline = Dash.line) { repo.next() }
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
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(3.dp).background(Dash.line)) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Dash.slate))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Txt(mmss(pos), Type.meta, Dash.muted, Modifier.weight(1f))
        if (np.durationMs > 0) Txt(mmss(np.durationMs), Type.meta, Dash.muted)
    }
}

private fun mmss(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private fun sourceName(context: Context, pkg: String): String =
    if (pkg == SPOTIFY) "Spotify"
    else runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

private fun openSpotify(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(SPOTIFY)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SPOTIFY"))
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
