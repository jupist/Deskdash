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
    /** Top bar: whole bar, and its optional parts. */
    val topBar: Boolean = true,
    val barBattery: Boolean = true,
    val barSliders: Boolean = true,
    val barLock: Boolean = true,
    /** "auto", "landscape" or "portrait". */
    val orientation: String = "auto",
    /** Night screen extras. */
    val nightAlarm: Boolean = true,
    val nightEvent: Boolean = true,
    val nightSeconds: Boolean = false,
    /** Burn-in protection (OLED): shift the picture a few pixels now and then. */
    val pixelShift: Boolean = false,
    val onboarded: Boolean = false,
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
    private val topBar = booleanPreferencesKey("top_bar")
    private val barBattery = booleanPreferencesKey("bar_battery")
    private val barSliders = booleanPreferencesKey("bar_sliders")
    private val barLock = booleanPreferencesKey("bar_lock")
    private val orientation = stringPreferencesKey("orientation")
    private val nightAlarm = booleanPreferencesKey("night_alarm")
    private val nightEvent = booleanPreferencesKey("night_event")
    private val nightSeconds = booleanPreferencesKey("night_seconds")
    private val pixelShift = booleanPreferencesKey("pixel_shift")
    private val onboarded = booleanPreferencesKey("onboarded")

    fun config(context: Context): Flow<DisplayConfig> = context.prefs.data.map { p ->
        val d = DisplayConfig()
        DisplayConfig(
            nightEnabled = p[nightEnabled] ?: d.nightEnabled,
            nightStart = p[nightStart]?.let(::parseTime) ?: d.nightStart,
            nightEnd = p[nightEnd]?.let(::parseTime) ?: d.nightEnd,
            nightBrightness = p[nightBrightness] ?: d.nightBrightness,
            keepAwakeCharging = p[keepAwake] ?: d.keepAwakeCharging,
            batteryPrompted = p[batteryPrompted] ?: false,
            topBar = p[topBar] ?: true,
            barBattery = p[barBattery] ?: true,
            barSliders = p[barSliders] ?: true,
            barLock = p[barLock] ?: true,
            orientation = p[orientation] ?: "auto",
            nightAlarm = p[nightAlarm] ?: true,
            nightEvent = p[nightEvent] ?: true,
            nightSeconds = p[nightSeconds] ?: false,
            pixelShift = p[pixelShift] ?: false,
            // Installs from before onboarding existed count as set up (they have a weather place).
            onboarded = p[onboarded] ?: (p[Keys.weatherCity] != null),
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
    suspend fun setTopBar(c: Context, v: Boolean) = put(c, topBar, v)
    suspend fun setBarBattery(c: Context, v: Boolean) = put(c, barBattery, v)
    suspend fun setBarSliders(c: Context, v: Boolean) = put(c, barSliders, v)
    suspend fun setBarLock(c: Context, v: Boolean) = put(c, barLock, v)
    suspend fun setOrientation(c: Context, v: String) = put(c, orientation, v)
    suspend fun setNightAlarm(c: Context, v: Boolean) = put(c, nightAlarm, v)
    suspend fun setNightEvent(c: Context, v: Boolean) = put(c, nightEvent, v)
    suspend fun setNightSeconds(c: Context, v: Boolean) = put(c, nightSeconds, v)
    suspend fun setPixelShift(c: Context, v: Boolean) = put(c, pixelShift, v)
    suspend fun setOnboarded(c: Context) = put(c, onboarded, true)
}
