package com.desk.dashboard.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.desk.dashboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Plays the alarm on the alarm stream as a foreground service, holds a wake lock, and shows the
 * full-screen [AlarmActivity] (via a full-screen-intent notification, and directly when allowed).
 * Stops by itself after [MAX_RING_MS].
 */
class AlarmService : Service() {
    companion object {
        private const val ACTION_RING = "ring"
        private const val ACTION_STOP = "stop"
        private const val ACTION_SNOOZE = "snooze"
        private const val CHANNEL = "alarm"
        private const val NOTIF_ID = 7
        private const val MAX_RING_MS = 10 * 60_000L

        private const val EXTRA_LABEL = "label"
        private const val EXTRA_SNOOZABLE = "snoozable"
        private const val EXTRA_TIMEOUT = "timeout"

        private val _ringing = MutableStateFlow(false)
        val ringing: StateFlow<Boolean> = _ringing

        /** What's ringing: "Alarm", or a timer's name. Timers can't be snoozed. */
        val label = MutableStateFlow("Alarm")
        val snoozable = MutableStateFlow(true)

        fun ring(context: Context) = context.startForegroundService(intent(context, ACTION_RING))

        /** Rings for a finished timer: same screen and sound, no snooze, stops after 2 minutes. */
        fun ringTimer(context: Context, name: String) = context.startForegroundService(
            intent(context, ACTION_RING).putExtra(EXTRA_LABEL, name).putExtra(EXTRA_SNOOZABLE, false)
                .putExtra(EXTRA_TIMEOUT, 2 * 60_000L)
        )
        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
        fun snooze(context: Context) = context.startService(intent(context, ACTION_SNOOZE))

        private fun intent(context: Context, action: String) = Intent(context, AlarmService::class.java).setAction(action)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeout = Runnable { stopRinging() }
    private var maxRing = MAX_RING_MS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                if (!_ringing.value) {
                    label.value = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
                    snoozable.value = intent.getBooleanExtra(EXTRA_SNOOZABLE, true)
                    maxRing = intent.getLongExtra(EXTRA_TIMEOUT, MAX_RING_MS)
                }
                startRinging()
            }
            ACTION_SNOOZE -> {
                stopRinging()
                // Book the snooze before the process may die.
                runBlocking { Alarms.snooze(applicationContext) }
            }
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        // Always enter the foreground first: required within seconds of startForegroundService.
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, notification())
        if (_ringing.value) return
        _ringing.value = true

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "desk:alarm")
            .apply { acquire(maxRing + 5_000) }
        handler.postDelayed(timeout, maxRing)

        // If the dashboard is in front, the notification would only show as a heads-up; open the
        // alarm screen directly too. (When the screen is off, the full-screen intent handles it.)
        runCatching { startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        scope.launch {
            val cfg = Alarms.config(applicationContext).first()
            play(cfg.soundUri?.let(Uri::parse))
        }
    }

    private fun play(chosen: Uri?) {
        val audio = getSystemService(AudioManager::class.java)
        // An alarm at volume 0 is no alarm: lift it to half if it's muted.
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0)
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Chosen sound, then the system default (may be unreadable before first unlock), then a beep.
        val candidates = listOfNotNull(
            chosen,
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        for (uri in candidates) {
            val mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (mp != null) { player = mp; return }
        }
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        beep()
    }

    private fun beep() {
        val t = tone ?: return
        t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
        handler.postDelayed({ if (_ringing.value) beep() }, 2000)
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        tone?.release()
        tone = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _ringing.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_ringing.value) stopRinging()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the service plays the sound itself
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val full = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(label: String, a: String) = Notification.Action.Builder(
            null, label, PendingIntent.getService(this, a.hashCode(), intent(this, a), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label.value)
            .setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .apply { if (snoozable.value) addAction(action("Snooze", ACTION_SNOOZE)) }
            .addAction(action("Stop", ACTION_STOP))
            .build()
    }
}
