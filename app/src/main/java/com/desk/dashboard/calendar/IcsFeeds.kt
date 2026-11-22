package com.desk.dashboard.calendar

import android.content.Context
import android.net.Uri
import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** An ICS subscription (secret iCal link) or an imported .ics file ([url] null). */
@Serializable
data class IcsFeed(
    val id: String,
    val name: String,
    val url: String? = null,
    val color: Int = 0xFF4E6187.toInt(),
    val enabled: Boolean = true,
    val lastSync: Long = 0,
    val error: String? = null,
    val eventCount: Int = 0,
)

object IcsFeeds {
    const val SYNC_EVERY_MS = 30 * 60_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .build()

    private fun file(c: Context, id: String) = File(c.filesDir, "ics/$id.ics").also { it.parentFile?.mkdirs() }

    /** Parsed calendars, cached until their file changes. */
    private val cache = HashMap<String, Pair<Long, List<ICalendar>>>()

    private fun parsed(c: Context, id: String): List<ICalendar> {
        val f = file(c, id)
        if (!f.exists()) return emptyList()
        val stamp = f.lastModified()
        cache[id]?.takeIf { it.first == stamp }?.let { return it.second }
        val cals = runCatching { Biweekly.parse(f).all() }.getOrDefault(emptyList())
        cache[id] = stamp to cals
        return cals
    }

    /** Normalises webcal:// links (what calendar apps hand out) to https://. */
    fun normalise(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("webcal://", true) -> "https://" + u.substring(9)
            u.startsWith("webcals://", true) -> "https://" + u.substring(10)
            !u.contains("://") -> "https://$u"
            else -> u
        }
    }

    /** Downloads a subscription. Returns the updated feed (with an error message on failure). */
    suspend fun sync(c: Context, feed: IcsFeed): IcsFeed = withContext(Dispatchers.IO) {
        val url = feed.url ?: return@withContext feed
        val result = runCatching {
            http.newCall(Request.Builder().url(normalise(url)).header("User-Agent", "Deskdash/1.0").build()).execute().use { r ->
                if (!r.isSuccessful) error(if (r.code == 404) "Not found (404): check the link" else "Server error ${r.code}")
                val text = r.body!!.string()
                if (!text.contains("BEGIN:VCALENDAR")) error("That link isn't an iCal (.ics) calendar")
                text
            }
        }
        result.fold(
            onSuccess = { text ->
                val count = runCatching { Biweekly.parse(text).all().sumOf { it.events.size } }.getOrDefault(0)
                file(c, feed.id).writeText(text)
                feed.copy(lastSync = System.currentTimeMillis(), error = null, eventCount = count)
            },
            onFailure = { feed.copy(error = it.message ?: "Couldn't download") },
        )
    }

    /** Copies an .ics file picked by the user. Returns the event count, or null if it isn't one. */
    suspend fun import(c: Context, id: String, uri: Uri): Int? = withContext(Dispatchers.IO) {
        val text = runCatching { c.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return@withContext null
        if (!text.contains("BEGIN:VCALENDAR")) return@withContext null
        val count = runCatching { Biweekly.parse(text).all().sumOf { it.events.size } }.getOrNull() ?: return@withContext null
        file(c, id).writeText(text)
        count
    }

    fun delete(c: Context, id: String) {
        file(c, id).delete()
        cache.remove(id)
    }

    /** Occurrences from [feed] that haven't ended, through the end of [lastDay]. */
    suspend fun events(c: Context, feed: IcsFeed, today: LocalDate, lastDay: LocalDate): List<Event> = withContext(Dispatchers.Default) {
        occurrences(parsed(c, feed.id), feed, today, lastDay, LocalDateTime.now(), ZoneId.systemDefault())
    }

    /** Pure expansion of parsed calendars (unit tested). */
    fun occurrences(
        cals: List<ICalendar>, feed: IcsFeed, today: LocalDate, lastDay: LocalDate, now: LocalDateTime, zone: ZoneId,
    ): List<Event> {
        val windowStart = today.minusDays(1).atStartOfDay(zone).toInstant()
        val windowEnd = lastDay.plusDays(1).atStartOfDay(zone).toInstant()
        val out = mutableListOf<Event>()
        for (cal in cals) {
            for (ev in cal.events) {
                runCatching { expand(cal, ev, feed, zone, windowStart, windowEnd, now, today, lastDay, out) }
            }
        }
        return out
    }

    private fun expand(
        cal: ICalendar, ev: VEvent, feed: IcsFeed, zone: ZoneId,
        windowStart: java.time.Instant, windowEnd: java.time.Instant,
        now: LocalDateTime, today: LocalDate, lastDay: LocalDate, out: MutableList<Event>,
    ) {
        val dtStart = ev.dateStart?.value ?: return
        val allDay = !dtStart.hasTime()
        // The event's own time zone (TZID), so recurrences land on the right local times.
        val tz: TimeZone = cal.timezoneInfo.getTimezone(ev.dateStart)?.timeZone
            ?: if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getTimeZone(zone)
        val length: Duration = when {
            ev.dateEnd?.value != null -> Duration.ofMillis(ev.dateEnd.value.time - dtStart.time)
            ev.duration?.value != null -> Duration.ofMillis(ev.duration.value.toMillis())
            allDay -> Duration.ofDays(1)
            else -> Duration.ZERO
        }
        val title = ev.summary?.value?.ifBlank { null } ?: "(No title)"
        val location = ev.location?.value?.ifBlank { null }
        val link = ev.url?.value
        val it = ev.getDateIterator(tz)
        // Skip ahead to the window, then collect occurrences inside it.
        it.advanceTo(java.util.Date.from(windowStart.minus(length).minusSeconds(1)))
        var guard = 0
        while (it.hasNext() && guard++ < 500) {
            val d = it.next()
            if (d.toInstant().isAfter(windowEnd)) break
            val start: LocalDateTime
            val end: LocalDateTime
            if (allDay) {
                val day = d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                start = day.atStartOfDay()
                end = start.plus(length.coerceAtLeast(Duration.ofDays(1)))
                if (end.toLocalDate().minusDays(1).isBefore(today) || start.toLocalDate().isAfter(lastDay)) continue
            } else {
                start = d.toInstant().atZone(zone).toLocalDateTime()
                end = start.plus(length)
                if (!end.isAfter(now) || start.toLocalDate().isAfter(lastDay)) continue
            }
            out += Event(
                key = "ics:${feed.id}:${ev.uid?.value ?: title}:${d.time}",
                title = title, start = start, end = end, allDay = allDay,
                color = feed.color, calendar = feed.name, location = location,
                open = link?.let { OpenTarget.Link(it) } ?: OpenTarget.None,
            )
        }
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}
