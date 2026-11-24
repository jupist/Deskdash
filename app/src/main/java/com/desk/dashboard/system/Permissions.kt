package com.desk.dashboard.system

import android.Manifest
import android.content.Context
import android.app.NotificationManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberCalendarPermission(): PermissionState = rememberRuntimePermission(Manifest.permission.READ_CALENDAR)

/**
 * Runtime permission state, re-checked on every resume. [PermissionState.request] shows the
 * runtime dialog; if Android won't show it any more ("don't ask again"), it opens the app's
 * settings page.
 */
/** Granted, counting notifications as allowed before Android 13 (no runtime permission there). */
fun hasPermission(context: Context, permission: String) =
    (permission == Manifest.permission.POST_NOTIFICATIONS && android.os.Build.VERSION.SDK_INT < 33) ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberRuntimePermission(permission: String): PermissionState {
    val context = LocalContext.current
    val activity = context as Activity
    fun check() = hasPermission(context, permission)
    var granted by remember { mutableStateOf(check()) }
    LifecycleResumeEffect(Unit) {
        granted = check()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok && !activity.shouldShowRequestPermissionRationale(permission)) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
    }
    return PermissionState(granted) { launcher.launch(permission) }
}

/**
 * Notifications, for the ringing notification: asks if needed. Full-screen alerts (the alarm over
 * the lock screen) are offered in the alarm's settings instead, rather than jumping to a system page.
 */
fun ensureAlarmPermissions(context: Context, requestNotifications: () -> Unit) {
    if (!hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) requestNotifications()
}

/** The system page where full-screen alerts are allowed. */
fun fullScreenSettings(context: Context) = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))

/** Full-screen alerts (alarm over the lock screen) need a grant only from Android 14. */
fun canFullScreen(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
