package com.desk.dashboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// ---- Open-Meteo responses (only the fields we use) ----

@Serializable
data class Forecast(val current: Current, val daily: Daily) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temp: Double,
        @SerialName("weather_code") val code: Int,
    )

    @Serializable
    data class Daily(
        val time: List<String>,
        @SerialName("temperature_2m_max") val max: List<Double>,
        @SerialName("temperature_2m_min") val min: List<Double>,
        @SerialName("weather_code") val code: List<Int>,
    ) {
        val dates: List<LocalDate> get() = time.map(LocalDate::parse)
    }
}

@Serializable
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
) {
    val detail: String get() = listOfNotNull(admin1, country).distinct().joinToString(", ")
}

/** WMO weather interpretation codes → one short word. */
fun conditionWord(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Hail storm"
    else -> "—"
}

data class WeatherState(
    val loaded: Boolean = false,
    val city: String? = null,
    val forecast: Forecast? = null,
    /** Epoch ms of the last successful fetch. */
    val fetchedAt: Long? = null,
    /** The last attempt failed; [forecast] (if any) is from the cache. */
    val failed: Boolean = false,
    val fetching: Boolean = false,
)

/**
 * Weather source of truth. Holds a [StateFlow] for the UI, caches the raw forecast JSON in
 * DataStore, and refreshes at most every [REFRESH_MS] unless forced.
 */
class WeatherRepo private constructor(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state

    /** Fills the state from DataStore (cache + city) without touching the network. */
    suspend fun loadCache() = withContext(Dispatchers.IO) {
        val p = context.prefs.data.first()
        val city = p[Keys.weatherCity]
        val cacheValid = p[Keys.weatherCacheFor] == cacheKey(p[Keys.weatherLat], p[Keys.weatherLon], p[Keys.tempUnit])
        val forecast = if (cacheValid) p[Keys.weatherCache]?.let { runCatching { json.decodeFromString<Forecast>(it) }.getOrNull() } else null
        _state.value = _state.value.copy(
            loaded = true, city = city, forecast = forecast,
            fetchedAt = if (forecast != null) p[Keys.weatherCachedAt] else null,
        )
    }

    /** Fetches if there's a city and the cache is older than [maxAgeMs]. Safe to call often. */
    suspend fun refresh(maxAgeMs: Long = REFRESH_MS) = lock.withLock {
        withContext(Dispatchers.IO) {
            val p = context.prefs.data.first()
            val lat = p[Keys.weatherLat]
            val lon = p[Keys.weatherLon]
            val unit = p[Keys.tempUnit] ?: "celsius"
            if (lat == null || lon == null) {
                _state.value = _state.value.copy(loaded = true, city = null, forecast = null, fetchedAt = null, failed = false)
                return@withContext
            }
            val key = cacheKey(lat, lon, unit)
            val age = System.currentTimeMillis() - (p[Keys.weatherCachedAt] ?: 0)
            if (p[Keys.weatherCacheFor] == key && age < maxAgeMs && _state.value.forecast != null) return@withContext

            _state.value = _state.value.copy(fetching = true)
            val result = runCatching { fetch(lat, lon, unit) }
            val body = result.getOrNull()
            if (body != null) {
                val forecast = json.decodeFromString<Forecast>(body)
                val now = System.currentTimeMillis()
                context.prefs.edit {
                    it[Keys.weatherCache] = body
                    it[Keys.weatherCachedAt] = now
                    it[Keys.weatherCacheFor] = key
                }
                _state.value = WeatherState(loaded = true, city = p[Keys.weatherCity], forecast = forecast, fetchedAt = now)
            } else {
                _state.value = _state.value.copy(failed = true, fetching = false, city = p[Keys.weatherCity])
            }
        }
    }

    /** Fire-and-forget forced refresh, e.g. after the city or unit changes. */
    fun refreshNow() {
        scope.launch { loadCache(); refresh(maxAgeMs = 0) }
    }

    private fun fetch(lat: Double, lon: Double, unit: String): String? {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,weather_code")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "5")
            .addQueryParameter("temperature_unit", unit)
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            return if (r.isSuccessful) r.body!!.string() else null
        }
    }

    /**
     * Place search via Photon (OpenStreetMap, no key). Unlike Open-Meteo's geocoder, which only
     * knows towns and cities, it finds suburbs and neighbourhoods (e.g. Gachibowli), so the
     * forecast can be for the exact area. Keeps places and administrative areas; falls back to
     * any match (a landmark, say) when there are none.
     */
    suspend fun searchCity(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "12")
            .addQueryParameter("lang", "en")
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Deskdash/0.1 (tablet dashboard)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val all = json.parseToJsonElement(r.body!!.string()).jsonObject["features"]?.jsonArray.orEmpty()
                .mapNotNull { photonPlace(it.jsonObject) }
            val places = all.filter { it.second }.map { it.first }
            (places.ifEmpty { all.map { it.first } })
                .distinctBy { "${it.name}|${it.detail}" }
                .take(6)
        }
    }

    /** A Photon feature as a [Place], paired with whether it's a place/area (vs a road, shop…). */
    private fun photonPlace(f: JsonObject): Pair<Place, Boolean>? {
        val p = f["properties"]?.jsonObject ?: return null
        fun str(k: String) = p[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val coords = f["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return null
        val name = str("name") ?: return null
        val key = str("osm_key")
        val kind = str("osm_value")
        val isArea = key == "place" || (key == "boundary" && kind == "administrative")
        // "Suburb · Hyderabad, Telangana" or "City · Telangana"
        val where = listOfNotNull(str("city")?.takeIf { it != name }, str("state")).joinToString(", ")
        val label = kind?.takeIf { key == "place" }?.replaceFirstChar { it.uppercase() }
        return Place(
            name = name,
            latitude = coords[1].jsonPrimitive.double,
            longitude = coords[0].jsonPrimitive.double,
            country = str("country"),
            admin1 = listOfNotNull(label, where.ifEmpty { null }).joinToString(" · ").ifEmpty { null },
        ) to isArea
    }

    suspend fun setCity(place: Place) {
        context.prefs.edit {
            it[Keys.weatherCity] = place.name
            it[Keys.weatherLat] = place.latitude
            it[Keys.weatherLon] = place.longitude
        }
        _state.value = _state.value.copy(city = place.name, forecast = null, fetchedAt = null, failed = false)
        refreshNow()
    }

    suspend fun setUnit(unit: String) {
        context.prefs.edit { it[Keys.tempUnit] = unit }
        refreshNow()
    }

    private fun cacheKey(lat: Double?, lon: Double?, unit: String?) = "$lat,$lon,${unit ?: "celsius"}"

    companion object {
        const val REFRESH_MS = 30 * 60 * 1000L

        @Volatile private var instance: WeatherRepo? = null
        fun get(context: Context): WeatherRepo = instance ?: synchronized(this) {
            instance ?: WeatherRepo(context.applicationContext).also { instance = it }
        }
    }
}
