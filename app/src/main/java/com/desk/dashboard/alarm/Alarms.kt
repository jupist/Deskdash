package com.desk.dashboard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.desk.dashboard.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class AlarmConfig(
    val enabled: Boolean,
    val time: LocalTime,
    /** Empty = rings once. */
    val days: Set<DayOfWeek>,
    /** Null = system default alarm sound. */
    val soundUri: String?,
    val snoozeMinutes: Int,
    /** Epoch ms of a pending snooze ring, or null. */
    val snoozeAt: Long?,
    /** One-time: rings at the next [time] and turns itself off; [days] are kept for later. */
    val once: Boolean = false,
) {
    /** True when the alarm won't repeat (one-time switch on, or no days picked). */
    val ringsOnce get() = once || days.isEmpty()

    fun next(from: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        Alarms.nextOccurrence(time, if (once) emptySet() else days, from)
}

/**
 * The app's own alarm (SPEC.md › Alarm). Settings live in a DataStore file in device-protected
 * storage so the boot receiver and ringing service can read them before the first unlock.
 */
object Alarms {
    private val ENABLED = booleanPreferencesKey("alarm_enabled")
    private val TIME = stringPreferencesKey("alarm_time")
    private val DAYS = stringSetPreferencesKey("alarm_days")
    private val SOUND = stringPreferencesKey("alarm_sound_uri")
    private val SNOOZE_MIN = intPreferencesKey("snooze_minutes")
    private val SNOOZE_AT = longPreferencesKey("alarm_snooze_at")
    private val ONCE = booleanPreferencesKey("alarm_once")

    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val SNOOZE_CHOICES = listOf(5, 9, 10, 15)

    private const val RC_RING = 1
    private const val RC_SNOOZE = 2
    const val EXTRA_SNOOZE = "snooze"

    @Volatile private var store: DataStore<Preferences>? = null

    internal fun store(context: Context): DataStore<Preferences> = store ?: synchronized(this) {
        store ?: PreferenceDataStoreFactory.create(
            produceFile = {
                context.applicationContext.createDeviceProtectedStorageContext().preferencesDataStoreFile("alarm")
            }
        ).also { store = it }
    }

    /** Last known alarm settings, so tiles can draw before the first storage read. */
    @Volatile var cached: AlarmConfig? = null

    fun config(context: Context): Flow<AlarmConfig> = store(context).data.map { p ->
        AlarmConfig(
            enabled = p[ENABLED] ?: false,
            time = p[TIME]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(7, 0),
            days = p[DAYS]?.mapNotNull { it.toIntOrNull()?.let(DayOfWeek::of) }?.toSet() ?: WEEKDAYS,
            soundUri = p[SOUND],
            snoozeMinutes = p[SNOOZE_MIN] ?: 9,
            snoozeAt = p[SNOOZE_AT]?.takeIf { it > 0 },
            once = p[ONCE] ?: false,
        ).also { cached = it }
    }

    suspend fun setEnabled(context: Context, on: Boolean) = edit(context) {
        it[ENABLED] = on
        if (!on) it.remove(SNOOZE_AT) // turning the alarm off also cancels a pending snooze
    }
    suspend fun setTime(context: Context, t: LocalTime) = edit(context) { it[TIME] = "%02d:%02d".format(t.hour, t.minute) }
    suspend fun setDays(context: Context, days: Set<DayOfWeek>) = edit(context) { it[DAYS] = days.map { d -> d.value.toString() }.toSet() }
    suspend fun setSound(context: Context, uri: String?) = edit(context) { if (uri == null) it.remove(SOUND) else it[SOUND] = uri }
    suspend fun setOnce(context: Context, once: Boolean) = edit(context) { it[ONCE] = once }
    suspend fun setSnoozeMinutes(context: Context, m: Int) = edit(context) { it[SNOOZE_MIN] = m }

    suspend fun snooze(context: Context) {
        val minutes = config(context).first().snoozeMinutes
        edit(context) { it[SNOOZE_AT] = System.currentTimeMillis() + minutes * 60_000L }
    }

    /** Called when an alarm fires: a one-shot alarm turns itself off; a snooze clears itself. */
    suspend fun onFired(context: Context, wasSnooze: Boolean) = edit(context) {
        if (wasSnooze) it.remove(SNOOZE_AT)
        else if (it[ONCE] == true || it[DAYS]?.isEmpty() == true) it[ENABLED] = false
    }

    private suspend fun edit(context: Context, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store(context).edit(block)
        reschedule(context)
    }

    /** Clears and re-registers both the main alarm and any pending snooze with AlarmManager. */
    suspend fun reschedule(context: Context) {
        val cfg = config(context).first()
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(firePending(context, RC_RING))
        am.cancel(firePending(context, RC_SNOOZE))
        val now = System.currentTimeMillis()
        cfg.snoozeAt?.let { at ->
            if (at > now) setClock(am, at, context, RC_SNOOZE)
        }
        if (cfg.enabled) {
            val at = cfg.next().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setClock(am, at, context, RC_RING)
        }
    }

    /**
     * An alarm-clock alarm (exact, shown in the status bar). If exact alarms aren't allowed (Android
     * 12 with the permission revoked), falls back to an inexact wake-up rather than crashing.
     */
    private fun setClock(am: AlarmManager, at: Long, context: Context, rc: Int) {
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPending(context)), firePending(context, rc))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, firePending(context, rc))
        }
    }

    /** First occurrence strictly after [from] on one of [days] (or any day when [days] is empty). */
    fun nextOccurrence(time: LocalTime, days: Set<DayOfWeek>, from: LocalDateTime): LocalDateTime {
        for (i in 0..7) {
            val candidate = from.toLocalDate().plusDays(i.toLong()).atTime(time)
            if (candidate.isAfter(from) && (days.isEmpty() || candidate.dayOfWeek in days)) return candidate
        }
        return from.toLocalDate().plusDays(1).atTime(time) // unreachable
    }

    private fun firePending(context: Context, rc: Int): PendingIntent = PendingIntent.getBroadcast(
        context, rc,
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_SNOOZE, rc == RC_SNOOZE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What the system opens when the user taps the alarm icon in the status bar / lock screen. */
    private fun showPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
