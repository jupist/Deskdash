package com.desk.dashboard.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

const val SPOTIFY = "com.spotify.music"

data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: ImageBitmap?,
    val durationMs: Long,
    val playing: Boolean,
    /** Position at [positionAt] (elapsedRealtime), advancing at [speed] while playing. */
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
) {
    fun positionNow(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

/**
 * Tracks the active media session (Spotify first, else the most recent one) while [start]ed.
 * Needs notification access for [MediaListener].
 */
class MediaRepo private constructor(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private var controller: MediaController? = null
    private var users = 0
    private var artKey: Any? = null
    private var artCache: ImageBitmap? = null

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system page where the user turns on notification access for this app. */
    fun accessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            // Another app may have started playing; re-evaluate which session to show.
            if (state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_STOPPED) {
                runCatching { pick(sessions.getActiveSessions(component)) }
            }
        }
        override fun onSessionDestroyed() {
            runCatching { pick(sessions.getActiveSessions(component)) }.onFailure { setController(null) }
        }
    }

    /** The tile calls this on resume (and [stop] on pause), so access is re-checked each time. */
    fun start() {
        if (users++ > 0) return
        if (!hasAccess()) return
        runCatching {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            pick(sessions.getActiveSessions(component))
        }
    }

    fun stop() {
        if (--users > 0) return
        users = 0
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
        setController(null)
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.packageName == SPOTIFY && it.metadata != null }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) setController(best) else publish()
    }

    private fun setController(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) { _state.value = null; return }
        val pb = c.playbackState
        _state.value = NowPlaying(
            pkg = c.packageName,
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = art(md),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            playing = pb?.state == PlaybackState.STATE_PLAYING || pb?.state == PlaybackState.STATE_BUFFERING,
            positionMs = pb?.position ?: 0,
            positionAt = pb?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    /** Album art, downscaled to ≤ 320 px and cached per bitmap to keep memory and work low. */
    private fun art(md: MediaMetadata): ImageBitmap? {
        val bmp: Bitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (bmp === artKey) return artCache
        val max = 320
        val scaled = if (bmp.width > max || bmp.height > max) {
            val f = max.toFloat() / maxOf(bmp.width, bmp.height)
            bmp.scale((bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1))
        } else bmp
        artKey = bmp
        artCache = scaled.asImageBitmap()
        return artCache
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    /**
     * Resumes Spotify's last track in the background when it has no session (not running).
     * 1. Sends a PLAY key straight to Spotify's media button receiver (what a headset does).
     * 2. Falls back to connecting to Spotify's MediaBrowserService and calling play().
     * Returns false if neither produced a Spotify session; the caller then opens the app.
     */
    suspend fun resumeSpotify(): Boolean {
        sendPlayKeyToSpotify()
        if (awaitSpotifyPlaying()) return true
        val viaBrowser = runCatching { playViaBrowser() }.getOrDefault(false)
        return viaBrowser && awaitSpotifyPlaying()
    }

    private fun sendPlayKeyToSpotify() {
        val probe = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY)
        val receiver = context.packageManager.queryBroadcastReceivers(probe, 0).firstOrNull()?.activityInfo ?: return
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(receiver.packageName, receiver.name))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
            )
        }
    }

    /** Waits for a Spotify session to show up; presses play on it if it arrives paused. */
    private suspend fun awaitSpotifyPlaying(timeoutMs: Long = 6000): Boolean {
        val np = withTimeoutOrNull(timeoutMs) { state.first { it?.pkg == SPOTIFY } } ?: return false
        if (!np.playing) {
            controller?.transportControls?.play()
            withTimeoutOrNull(3000) { state.first { it?.pkg == SPOTIFY && it.playing } }
        }
        return true
    }

    /** Connects to Spotify's MediaBrowserService (Android Auto's route) and plays. */
    private suspend fun playViaBrowser(): Boolean {
        val service = context.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(SPOTIFY), 0)
            .firstOrNull()?.serviceInfo ?: return false
        return withTimeoutOrNull(6000) {
            suspendCancellableCoroutine { cont ->
                var browser: MediaBrowser? = null
                browser = MediaBrowser(
                    context, ComponentName(service.packageName, service.name),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            val b = browser!!
                            MediaController(context, b.sessionToken).transportControls.play()
                            // Give play a moment to reach Spotify before dropping the connection.
                            main.postDelayed({ b.disconnect() }, 3000)
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onConnectionFailed() { if (cont.isActive) cont.resume(false) }
                        override fun onConnectionSuspended() { if (cont.isActive) cont.resume(false) }
                    },
                    null,
                )
                main.post { browser.connect() }
                cont.invokeOnCancellation { main.post { browser.disconnect() } }
            }
        } ?: false
    }

    companion object {
        @Volatile private var instance: MediaRepo? = null
        fun get(context: Context): MediaRepo = instance ?: synchronized(this) {
            instance ?: MediaRepo(context.applicationContext).also { instance = it }
        }
    }
}
