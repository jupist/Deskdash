package com.desk.dashboard.calendar

import android.content.Context
import android.net.Uri
import com.desk.dashboard.data.CalendarSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** A fetched Google event as stored on disk (so the agenda has it after a restart or offline). */
@Serializable
private data class StoredEvent(
    val key: String, val title: String, val start: String, val end: String, val allDay: Boolean,
    val color: Int? = null, val calendar: String, val location: String? = null, val link: String? = null,
) {
    fun toEvent() = Event(
        key, title, java.time.LocalDateTime.parse(start), java.time.LocalDateTime.parse(end), allDay, color, calendar, location,
        link?.let { OpenTarget.Link(it) } ?: OpenTarget.None,
    )

    companion object {
        fun of(e: Event) = StoredEvent(
            e.key, e.title, e.start.toString(), e.end.toString(), e.allDay, e.color, e.calendar, e.location,
            (e.open as? OpenTarget.Link)?.url,
        )
    }
}

@Serializable
data class CalendarSources(val feeds: List<IcsFeed> = emptyList(), val google: GoogleAccount? = null)

/**
 * Every calendar source in one place: calendars synced on the device (Google, Samsung, Outlook,
 * DAVx5…), ICS subscriptions and imported files, and Google Calendar via sign-in.
 */
object CalendarHub {
    /** How far ahead remote sources are fetched; tiles show up to this many days. */
    const val MAX_DAYS = 14
    private const val GOOGLE_EVERY_MS = 15 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _sources = MutableStateFlow(CalendarSources())
    val sources: StateFlow<CalendarSources> = _sources
    /** Bumped whenever remote data changes, so tiles reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private var googleEvents: List<Event> = emptyList()
    private var loaded = false
    private val lock = Mutex()

    private fun file(c: Context) = File(c.filesDir, "calendars.json")
    private fun googleFile(c: Context) = File(c.filesDir, "google_events.json")

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        _sources.value = runCatching { json.decodeFromString<CalendarSources>(file(c).readText()) }.getOrDefault(CalendarSources())
        if (_sources.value.google != null) {
            googleEvents = runCatching {
                json.decodeFromString<List<StoredEvent>>(googleFile(c).readText()).map { it.toEvent() }
            }.getOrDefault(emptyList())
        }
    }

    private fun setGoogleEvents(c: Context, events: List<Event>) {
        googleEvents = events
        runCatching { googleFile(c).writeText(json.encodeToString(events.map(StoredEvent::of))) }
    }

    private fun save(c: Context, s: CalendarSources) {
        _sources.value = s
        runCatching { file(c).writeText(json.encodeToString(CalendarSources.serializer(), s)) }
        _version.value++
    }

    private fun update(c: Context, block: (CalendarSources) -> CalendarSources) = save(c, block(_sources.value))

    /** Events from every enabled source that haven't ended, today through [days] days. */
    suspend fun events(c: Context, days: Int): List<Event> {
        load(c)
        val today = LocalDate.now()
        val lastDay = today.plusDays((days - 1).toLong().coerceIn(0, MAX_DAYS - 1L))
        val s = _sources.value
        val device = runCatching {
            val ids = CalendarSource.selectedIds(c).first() ?: CalendarSource.effectiveSelection(c)
            CalendarSource.events(c, ids, days)
        }.getOrDefault(emptyList())
        val ics = s.feeds.filter { it.enabled }.flatMap { f -> runCatching { IcsFeeds.events(c, f, today, lastDay) }.getOrDefault(emptyList()) }
        val google = googleEvents.filter { !it.start.toLocalDate().isAfter(lastDay) && (it.allDay || it.end.isAfter(java.time.LocalDateTime.now())) }
        return (device + ics + google)
            .distinctBy { Triple(it.title.lowercase(), it.start, it.allDay) } // the same event from two sources
            .sortedWith(compareBy({ it.start.toLocalDate() }, { !it.allDay }, { it.start }))
    }

    /** Refreshes subscriptions and Google if they're older than their interval. */
    suspend fun syncIfStale(c: Context) = sync(c, force = false)

    suspend fun sync(c: Context, force: Boolean) = lock.withLock {
        load(c)
        _syncing.value = true
        try {
            val now = System.currentTimeMillis()
            val feeds = _sources.value.feeds.map { f ->
                if (f.url != null && f.enabled && (force || now - f.lastSync > IcsFeeds.SYNC_EVERY_MS)) IcsFeeds.sync(c, f) else f
            }
            if (feeds != _sources.value.feeds) update(c) { it.copy(feeds = feeds) }
            val g = _sources.value.google
            if (g != null && (force || now - g.lastSync > GOOGLE_EVERY_MS)) syncGoogle(c, g)
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun syncGoogle(c: Context, g: GoogleAccount) {
        when (val auth = GoogleCalendar.authorize(c)) {
            is GoogleCalendar.Auth.Token -> runCatching {
                val acc = GoogleCalendar.account(auth.token, g)
                setGoogleEvents(c, GoogleCalendar.events(auth.token, acc, LocalDate.now(), LocalDate.now().plusDays(MAX_DAYS - 1L)))
                update(c) { it.copy(google = acc) }
            }.onFailure { e -> update(c) { it.copy(google = g.copy(error = e.message, lastSync = System.currentTimeMillis())) } }
            is GoogleCalendar.Auth.NeedsConsent -> update(c) { it.copy(google = g.copy(error = "Access needs approving again: reconnect")) }
            is GoogleCalendar.Auth.Failed -> update(c) { it.copy(google = g.copy(error = auth.message, lastSync = System.currentTimeMillis())) }
        }
    }

    // ---- ICS ----

    suspend fun addSubscription(c: Context, name: String, url: String): IcsFeed {
        load(c)
        val feed = IcsFeeds.sync(c, IcsFeed(newId(), name.ifBlank { "Calendar" }, url.trim(), nextColor()))
        update(c) { it.copy(feeds = it.feeds + feed) }
        return feed
    }

    suspend fun importFile(c: Context, name: String, uri: Uri): Boolean {
        load(c)
        val id = newId()
        val count = IcsFeeds.import(c, id, uri) ?: return false
        update(c) { it.copy(feeds = it.feeds + IcsFeed(id, name.ifBlank { "Imported" }, null, nextColor(), lastSync = System.currentTimeMillis(), eventCount = count)) }
        return true
    }

    fun setFeed(c: Context, feed: IcsFeed) = update(c) { s -> s.copy(feeds = s.feeds.map { if (it.id == feed.id) feed else it }) }

    fun removeFeed(c: Context, id: String) {
        IcsFeeds.delete(c, id)
        update(c) { s -> s.copy(feeds = s.feeds.filter { it.id != id }) }
    }

    // ---- Google ----

    /** After the user approved access: fetch the account and its calendars. */
    suspend fun connectGoogle(c: Context, token: String): String? = runCatching {
        load(c)
        val acc = GoogleCalendar.account(token, _sources.value.google)
        setGoogleEvents(c, GoogleCalendar.events(token, acc, LocalDate.now(), LocalDate.now().plusDays(MAX_DAYS - 1L)))
        update(c) { it.copy(google = acc) }
        null
    }.getOrElse { it.message ?: "Couldn't reach Google Calendar" }

    fun setGoogleCalendar(c: Context, id: String, selected: Boolean) = update(c) { s ->
        s.copy(google = s.google?.let { g -> g.copy(calendars = g.calendars.map { if (it.id == id) it.copy(selected = selected) else it }, lastSync = 0) })
    }

    fun disconnectGoogle(c: Context) {
        setGoogleEvents(c, emptyList())
        update(c) { it.copy(google = null) }
    }

    /** Adds iCal subscriptions from a backup (skipping links already present) and syncs them. */
    suspend fun restoreFeeds(c: Context, feeds: List<IcsFeed>) {
        load(c)
        val have = _sources.value.feeds.mapNotNull { it.url }.toSet()
        for (f in feeds) if (f.url != null && f.url !in have) addSubscription(c, f.name, f.url)
    }

    /** Device calendar selection changed (stored elsewhere): reload tiles. */
    fun touch() { _version.value++ }

    private fun newId() = java.util.UUID.randomUUID().toString().take(8)

    private val palette = listOf(0xFF4E6187, 0xFF3E6B55, 0xFF9A4E36, 0xFF7A5C99, 0xFF2F7F86, 0xFFA07A2C).map { it.toInt() }
    private fun nextColor() = palette[_sources.value.feeds.size % palette.size]
}
