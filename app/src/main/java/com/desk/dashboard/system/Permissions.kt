package com.desk.dashboard.system

import android.Manifest
import android.widget.Toast
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
@Composable
fun rememberRuntimePermission(permission: String): PermissionState {
    val context = LocalContext.current
    val activity = context as Activity
    fun check() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
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
 * Notifications (for the ringing notification) and full-screen intents (alarm over the lock
 * screen). Asks for the first; sends the user to the system page for the second.
 */
fun ensureAlarmPermissions(context: Context, requestNotifications: () -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestNotifications()
        return
    }
    if (!canFullScreen(context)) {
        Toast.makeText(context, "Allow full-screen alerts so the alarm can show over the lock screen", Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        )
    }
}

/** Full-screen alerts (alarm over the lock screen) need a grant only from Android 14. */
fun canFullScreen(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
