package com.desk.dashboard.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.desk.dashboard.alarm.Alarms
import com.desk.dashboard.board.BoardStore
import com.desk.dashboard.board.Boards
import com.desk.dashboard.calendar.CalendarHub
import com.desk.dashboard.calendar.IcsFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class SavedDate(val title: String, val date: String, val time: String? = null, val category: String = "OTHER", val note: String? = null)

@Serializable
private data class BackupFile(
    val app: String = "Deskdash",
    val version: Int = 1,
    val created: String,
    val boards: Boards? = null,
    val settings: JsonObject = JsonObject(emptyMap()),
    val alarm: JsonObject = JsonObject(emptyMap()),
    val countdowns: List<SavedDate> = emptyList(),
    val icalLinks: List<IcsFeed> = emptyList(),
)

/**
 * Export and import of everything the user set up: presets and layouts, settings, the alarm,
 * countdowns and iCal links, as one JSON file. Not included: custom font files, imported .ics
 * files, Google sign-in (reconnect after restoring) and timer state.
 */
object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    /** Caches and per-device state that shouldn't move between devices. */
    internal val skip = setOf("weather_cache", "weather_cached_at", "weather_cache_for", "battery_prompted", "font_stamp")

    internal suspend fun dump(store: DataStore<Preferences>): JsonObject = encode(store.data.first().asMap())

    /** Settings values as JSON, each tagged with its type (caches and per-device keys left out). */
    internal fun encode(values: Map<Preferences.Key<*>, Any>): JsonObject = JsonObject(
        values.filterKeys { it.name !in skip }.mapNotNull { (k, v) ->
            val e: JsonElement = when (v) {
                is String -> JsonObject(mapOf("s" to JsonPrimitive(v)))
                is Boolean -> JsonObject(mapOf("b" to JsonPrimitive(v)))
                is Int -> JsonObject(mapOf("i" to JsonPrimitive(v)))
                is Long -> JsonObject(mapOf("l" to JsonPrimitive(v)))
                is Float -> JsonObject(mapOf("f" to JsonPrimitive(v)))
                is Double -> JsonObject(mapOf("d" to JsonPrimitive(v)))
                is Set<*> -> JsonObject(mapOf("set" to JsonArray(v.map { JsonPrimitive(it.toString()) })))
                else -> return@mapNotNull null
            }
            k.name to e
        }.toMap()
    )

    internal fun restoreInto(p: MutablePreferences, values: JsonObject) {
        for ((name, e) in values) {
            if (name in skip) continue
            val (type, v) = e.jsonObject.entries.firstOrNull() ?: continue
            runCatching {
                when (type) {
                    "s" -> p[stringPreferencesKey(name)] = v.jsonPrimitive.content
                    "b" -> p[booleanPreferencesKey(name)] = v.jsonPrimitive.boolean
                    "i" -> p[intPreferencesKey(name)] = v.jsonPrimitive.int
                    "l" -> p[longPreferencesKey(name)] = v.jsonPrimitive.long
                    "f" -> p[floatPreferencesKey(name)] = v.jsonPrimitive.float
                    "d" -> p[doublePreferencesKey(name)] = v.jsonPrimitive.double
                    "set" -> p[stringSetPreferencesKey(name)] = v.jsonArray.map { it.jsonPrimitive.content }.toSet()
                }
            }
        }
    }

    suspend fun export(c: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val dates = DashDb.get(c).dates().upcoming(LocalDate.of(1970, 1, 1)).first().map {
            SavedDate(it.title, it.date.toString(), it.time?.toString(), it.category.name, it.note)
        }
        CalendarHub.load(c)
        val file = BackupFile(
            created = java.time.LocalDateTime.now().withNano(0).toString(),
            boards = BoardStore.boards.value,
            settings = dump(c.prefs),
            alarm = dump(Alarms.store(c)),
            countdowns = dates,
            icalLinks = CalendarHub.sources.value.feeds.filter { it.url != null },
        )
        c.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToString(file).toByteArray()) }
    }

    /** Restores a backup. Returns null on success, or what went wrong. */
    suspend fun import(c: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val text = runCatching { c.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return@withContext "Couldn't read that file"
        val file = runCatching { json.decodeFromString<BackupFile>(text) }.getOrNull()
        if (file == null || file.app != "Deskdash") return@withContext "That isn't a Deskdash backup"
        c.prefs.edit { restoreInto(it, file.settings) }
        Alarms.store(c).edit { restoreInto(it, file.alarm) }
        Alarms.reschedule(c)
        file.boards?.let { withContext(Dispatchers.Main) { BoardStore.replaceAll(it) } }
        val dao = DashDb.get(c).dates()
        val have = dao.upcoming(LocalDate.of(1970, 1, 1)).first().map { it.title to it.date }.toSet()
        for (d in file.countdowns) {
            val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: continue
            if ((d.title to date) in have) continue
            dao.upsert(
                UpcomingDate(
                    title = d.title, date = date, time = d.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                    category = runCatching { Category.valueOf(d.category) }.getOrDefault(Category.OTHER), note = d.note,
                )
            )
        }
        CalendarHub.restoreFeeds(c, file.icalLinks)
        WeatherRepo.get(c).refreshNow()
        null
    }
}
