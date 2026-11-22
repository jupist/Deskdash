package com.desk.dashboard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** Night mode and display settings (SPEC.md › Settings › Night mode / Display). */
data class DisplayConfig(
    val nightEnabled: Boolean = true,
    val nightStart: LocalTime = LocalTime.of(23, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
    val nightBrightness: Float = 0.02f,
    val keepAwakeCharging: Boolean = true,
    val batteryPrompted: Boolean = false,
) {
    /** True when [t] falls in the night window; the window may wrap past midnight. */
    fun isNight(t: LocalTime): Boolean {
        if (!nightEnabled || nightStart == nightEnd) return false
        return if (nightStart < nightEnd) t >= nightStart && t < nightEnd
        else t >= nightStart || t < nightEnd
    }
}

object DisplaySettings {
    private val nightEnabled = booleanPreferencesKey("night_enabled")
    private val nightStart = stringPreferencesKey("night_start")
    private val nightEnd = stringPreferencesKey("night_end")
    private val nightBrightness = floatPreferencesKey("night_brightness")
    private val keepAwake = booleanPreferencesKey("keep_awake_charging")
    private val batteryPrompted = booleanPreferencesKey("battery_prompted")

    fun config(context: Context): Flow<DisplayConfig> = context.prefs.data.map { p ->
        val d = DisplayConfig()
        DisplayConfig(
            nightEnabled = p[nightEnabled] ?: d.nightEnabled,
            nightStart = p[nightStart]?.let(::parseTime) ?: d.nightStart,
            nightEnd = p[nightEnd]?.let(::parseTime) ?: d.nightEnd,
            nightBrightness = p[nightBrightness] ?: d.nightBrightness,
            keepAwakeCharging = p[keepAwake] ?: d.keepAwakeCharging,
            batteryPrompted = p[batteryPrompted] ?: false,
        )
    }.distinctUntilChanged()

    private fun parseTime(s: String) = runCatching { LocalTime.parse(s) }.getOrNull()

    private suspend fun <T> put(context: Context, key: Preferences.Key<T>, value: T) {
        context.prefs.edit { it[key] = value }
    }

    suspend fun setNightEnabled(c: Context, v: Boolean) = put(c, nightEnabled, v)
    suspend fun setNightStart(c: Context, t: LocalTime) = put(c, nightStart, "%02d:%02d".format(t.hour, t.minute))
    suspend fun setNightEnd(c: Context, t: LocalTime) = put(c, nightEnd, "%02d:%02d".format(t.hour, t.minute))
    suspend fun setNightBrightness(c: Context, v: Float) = put(c, nightBrightness, v.coerceIn(0.01f, 1f))
    suspend fun setKeepAwake(c: Context, v: Boolean) = put(c, keepAwake, v)
    suspend fun setBatteryPrompted(c: Context) = put(c, batteryPrompted, true)
}
