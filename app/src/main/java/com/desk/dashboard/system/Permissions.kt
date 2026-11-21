package com.desk.dashboard.system

import android.Manifest
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
import com.desk.dashboard.data.CalendarSource

class PermissionState(val granted: Boolean, val request: () -> Unit)

/**
 * READ_CALENDAR state, re-checked on every resume. [PermissionState.request] shows the runtime
 * dialog; if Android won't show it any more ("don't ask again"), it opens the app's settings page.
 */
@Composable
fun rememberCalendarPermission(): PermissionState {
    val context = LocalContext.current
    val activity = context as Activity
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    LifecycleResumeEffect(Unit) {
        granted = CalendarSource.hasPermission(context)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
    }
    return PermissionState(granted) { launcher.launch(Manifest.permission.READ_CALENDAR) }
}
