package com.desk.dashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.localPrefs: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** All non-Room settings: this device's, or the tablet's while the phone is a remote control. */
val Context.prefs: DataStore<Preferences>
    get() = (this as? com.desk.dashboard.remote.RemoteContext)?.session?.settings ?: localPrefs

object Keys {
    /** Calendar ids (as strings) shown on the Today tile; absent = default (all Google calendars). */
    val calendarIds = stringSetPreferencesKey("calendar_ids")

    val weatherCity = stringPreferencesKey("weather_city")
    val weatherLat = doublePreferencesKey("weather_lat")
    val weatherLon = doublePreferencesKey("weather_lon")
    val tempUnit = stringPreferencesKey("temp_unit") // "celsius" | "fahrenheit"
    val weatherCache = stringPreferencesKey("weather_cache")
    val weatherCachedAt = longPreferencesKey("weather_cached_at")
    /** "lat,lon,unit" the cache was fetched for, so a city or unit change invalidates it. */
    val weatherCacheFor = stringPreferencesKey("weather_cache_for")
}
