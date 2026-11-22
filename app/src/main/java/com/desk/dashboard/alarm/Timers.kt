package com.desk.dashboard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One timer tile's state. Times are wall-clock epoch ms, so a timer survives the app being
 * closed: the remaining time is always derived from [endAt].
 */
@Serializable
data class TimerState(
    val mode: String = "timer", // timer | stopwatch | focus
    val running: Boolean = false,
    /** Timer/focus: when the current countdown ends (running). */
    val endAt: Long = 0,
    /** Timer/focus: time left while paused; also the length last set. */
    val remainingMs: Long = 5 * 60_000,
    /** Stopwatch: when it was (re)started, and time banked before that. */
    val startedAt: Long = 0,
    val bankedMs: Long = 0,
    /** Focus: "work" or "break", and work sessions finished today. */
    val phase: String = "work",
    val sessions: Int = 0,
    val sessionsDay: Long = 0,
    /** Timer: it reached zero (until reset). */
    val done: Boolean = false,
) {
    fun remainingNow(now: Long = System.currentTimeMillis()) = if (running) (endAt - now).coerceAtLeast(0) else remainingMs
    fun elapsedNow(now: Long = System.currentTimeMillis()) = bankedMs + if (running) now - startedAt else 0
}

object Timers {
    private val json = Json { ignoreUnknownKeys = true }
    private val _states = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val states: StateFlow<Map<String, TimerState>> = _states
    private var loaded = false

    private fun file(c: Context) = File(c.filesDir, "timers.json")

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        _states.value = runCatching { json.decodeFromString<Map<String, TimerState>>(file(c).readText()) }.getOrDefault(emptyMap())
    }

    fun get(c: Context, id: String): TimerState { load(c); return _states.value[id] ?: TimerState() }

    @Synchronized
    fun put(c: Context, id: String, s: TimerState) {
        load(c)
        _states.value = _states.value + (id to s)
        runCatching { file(c).writeText(json.encodeToString(_states.value)) }
        schedule(c, id, s)
    }

    fun remove(c: Context, id: String) {
        load(c)
        cancel(c, id)
        _states.value = _states.value - id
        runCatching { file(c).writeText(json.encodeToString(_states.value)) }
    }

    // ---- actions ----

    fun start(c: Context, id: String) {
        val s = get(c, id)
        val now = System.currentTimeMillis()
        put(c, id, when (s.mode) {
            "stopwatch" -> s.copy(running = true, startedAt = now)
            else -> s.copy(running = true, done = false, endAt = now + s.remainingMs.coerceAtLeast(1000))
        })
    }

    fun pause(c: Context, id: String) {
        val s = get(c, id)
        val now = System.currentTimeMillis()
        put(c, id, when (s.mode) {
            "stopwatch" -> s.copy(running = false, bankedMs = s.elapsedNow(now))
            else -> s.copy(running = false, remainingMs = s.remainingNow(now))
        })
    }

    /** Back to the start: [lengthMs] for timer/focus (current phase), zero for the stopwatch. */
    fun reset(c: Context, id: String, lengthMs: Long) {
        val s = get(c, id)
        put(c, id, s.copy(running = false, done = false, bankedMs = 0, remainingMs = lengthMs, endAt = 0))
    }

    fun set(c: Context, id: String, block: (TimerState) -> TimerState) = put(c, id, block(get(c, id)))

    // ---- alarm scheduling ----

    private fun pending(c: Context, id: String) = PendingIntent.getBroadcast(
        c, ("timer:$id").hashCode(),
        Intent(c, TimerReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancel(c: Context, id: String) = c.getSystemService(AlarmManager::class.java).cancel(pending(c, id))

    private fun schedule(c: Context, id: String, s: TimerState) {
        cancel(c, id)
        if (s.running && s.mode != "stopwatch") {
            c.getSystemService(AlarmManager::class.java)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endAt, pending(c, id))
        }
    }

    /** Re-registers running timers (after a reboot or an app update). */
    fun rescheduleAll(c: Context) {
        load(c)
        _states.value.forEach { (id, s) -> schedule(c, id, s) }
    }

    /** Called when a countdown reaches zero. */
    fun onFinished(c: Context, id: String, name: String, focusWorkMs: Long, focusBreakMs: Long) {
        val s = get(c, id)
        if (!s.running) return
        val now = System.currentTimeMillis()
        if (s.mode == "focus") {
            chime(c)
            val today = now / 86_400_000
            val count = (if (s.sessionsDay == today) s.sessions else 0) + if (s.phase == "work") 1 else 0
            // Work → break starts at once; after a break, wait for the user to start the next session.
            put(
                c, id,
                if (s.phase == "work") s.copy(phase = "break", endAt = now + focusBreakMs, remainingMs = focusBreakMs, sessions = count, sessionsDay = today)
                else s.copy(phase = "work", running = false, remainingMs = focusWorkMs, sessions = count, sessionsDay = today),
            )
        } else {
            put(c, id, s.copy(running = false, done = true, remainingMs = 0))
            AlarmService.ringTimer(c, name)
        }
    }

    /** A short notification sound for focus phase changes. */
    private fun chime(c: Context) {
        runCatching {
            val r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play()
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val tile = com.desk.dashboard.board.BoardStore.findTile(context, id)
        fun cfgInt(k: String, d: Int) = (tile?.config?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: d
        val name = (tile?.config?.get("title") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Timer"
        Timers.onFinished(context, id, name, cfgInt("work", 25) * 60_000L, cfgInt("break", 5) * 60_000L)
    }
}
