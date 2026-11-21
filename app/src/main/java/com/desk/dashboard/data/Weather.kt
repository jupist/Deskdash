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

@Serializable
private data class GeoResponse(val results: List<Place> = emptyList())

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

    suspend fun searchCity(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "6")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            json.decodeFromString<GeoResponse>(r.body!!.string()).results
        }
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
