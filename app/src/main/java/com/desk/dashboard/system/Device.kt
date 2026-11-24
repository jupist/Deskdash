package com.desk.dashboard.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Home-app mode and battery-sleep exemptions. */
object Device {
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** True when this app is the default home app (so it starts on boot and Home returns here). */
    fun isHomeApp(context: Context): Boolean =
        context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName

    /** System page for picking the default home app. */
    fun homeSettingsIntent() = Intent(Settings.ACTION_HOME_SETTINGS)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** One-tap system dialog: "Let app always run in background?" (battery: Unrestricted). */
    @SuppressLint("BatteryLife") // sideloaded desk app; staying alive is the point
    fun batteryExemptionIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /**
     * Samsung's battery pages, most specific first: Background usage limits (where Never sleeping
     * apps lives), then Device care › Battery. Some need a system-only permission, so only pages
     * this app may open are returned; null when none is available (not a Samsung device).
     */
    fun samsungSleepIntent(context: Context): Intent? = listOf(
        "com.samsung.android.sm.ACTION_START_APP_POWER_MANAGEMENT_SETTING",
        "com.samsung.android.sm.ACTION_BATTERY",
    ).map(::Intent).firstOrNull { canStart(context, it) }

    private fun canStart(context: Context, intent: Intent): Boolean {
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return false
        val perm = info.permission ?: return info.exported
        return info.exported && context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens a system page; if this device doesn't have it, opens the app's own system page instead. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) { // ActivityNotFoundException, SecurityException
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
}
