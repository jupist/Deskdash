package com.desk.dashboard.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.desk.dashboard.calendar.Event
import com.desk.dashboard.calendar.OpenTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    val syncing: Boolean,
) {
    val isGoogle get() = accountType == "com.google"
}

object CalendarSource {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(context: Context): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.SYNC_EVENTS,
        )
        val out = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    color = c.getInt(4),
                    visible = c.getInt(5) == 1,
                    syncing = c.getInt(6) == 1,
                )
            }
        }
        out.sortedWith(compareBy({ !it.isGoogle }, { it.account }, { it.name }))
    }

    /** Spec default: all Google calendars (visible and syncing). Falls back to all visible ones. */
    fun defaultSelection(all: List<DeviceCalendar>): Set<Long> {
        val google = all.filter { it.isGoogle && it.visible && it.syncing }
        return (google.ifEmpty { all.filter { it.visible } }).map { it.id }.toSet()
    }

    /** Selected calendar ids, or null when the user never changed the selection (use the default). */
    fun selectedIds(context: Context): Flow<Set<Long>?> =
        context.prefs.data.map { p -> p[Keys.calendarIds]?.mapNotNull(String::toLongOrNull)?.toSet() }

    suspend fun setSelected(context: Context, ids: Set<Long>) {
        context.prefs.edit { it[Keys.calendarIds] = ids.map(Long::toString).toSet() }
    }

    suspend fun effectiveSelection(context: Context): Set<Long> =
        selectedIds(context).first() ?: defaultSelection(calendars(context))

    /**
     * Instances from the selected calendars that haven't ended yet, from now until the end of
     * the day [days] - 1 days from today (1 = today only).
     */
    suspend fun events(context: Context, ids: Set<Long>, days: Int): List<Event> = withContext(Dispatchers.IO) {
        if (!hasPermission(context) || ids.isEmpty()) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val lastDay = today.plusDays((days - 1).toLong().coerceAtLeast(0))
        val endMs = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Query from yesterday so all-day events (stored at UTC midnight) and ongoing events are included.
        val from = now - 36 * 3600_000L
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, endMs + 24 * 3600_000L)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
            Instances.DISPLAY_COLOR, Instances.CALENDAR_DISPLAY_NAME, Instances.EVENT_LOCATION,
        )
        val selection = "${Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        val out = mutableListOf<Event>()
        context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val start: LocalDateTime
                val finish: LocalDateTime
                if (allDay) {
                    start = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay()
                    finish = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay()
                } else {
                    start = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
                    finish = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
                }
                val keep = if (allDay) {
                    !finish.toLocalDate().minusDays(1).isBefore(today) && !start.toLocalDate().isAfter(lastDay)
                } else {
                    end > now && begin < endMs
                }
                if (keep) out += Event(
                    key = "device:${c.getLong(0)}:$begin",
                    title = c.getString(1)?.ifBlank { null } ?: "(No title)",
                    start = start, end = finish, allDay = allDay,
                    color = c.getInt(5), calendar = c.getString(6) ?: "",
                    location = c.getString(7)?.ifBlank { null },
                    open = OpenTarget.Device(c.getLong(0), begin, end),
                )
            }
        }
        out
    }

    /** Emits whenever the calendar provider changes (e.g. after a Google sync). */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    fun openIntent(t: OpenTarget.Device): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, t.eventId))
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, t.begin)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, t.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
