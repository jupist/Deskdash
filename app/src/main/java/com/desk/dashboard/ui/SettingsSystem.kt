package com.desk.dashboard.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.DisplayConfig
import com.desk.dashboard.data.DisplaySettings
import com.desk.dashboard.media.MediaRepo
import com.desk.dashboard.system.Device
import com.desk.dashboard.system.Lock
import com.desk.dashboard.system.rememberCalendarPermission
import com.desk.dashboard.system.rememberRuntimePermission
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/** One row: title and optional hint on the left, a control on the right. */
@Composable
private fun SettingRow(title: String, hint: String? = null, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.bodySmall, Dash.ink)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Txt(hint, Type.meta, Dash.muted, maxLines = 2)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
fun NightSection(showOverlay: ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val cfg by remember { DisplaySettings.config(context) }.collectAsStateWithLifecycle(null)
    val c = cfg ?: return
    var dragging by remember { mutableFloatStateOf(-1f) } // live value while the slider is held

    fun pickTime(initial: LocalTime, save: suspend (LocalTime) -> Unit) = showOverlay {
        TimePickerOverlay(
            initial,
            onPick = { t -> scope.launch { save(t) }; showOverlay(null) },
            onClear = null,
            onDismiss = { showOverlay(null) },
        )
    }

    Section("Night mode") {
        SettingRow("Night mode", "Dim clock between these times; tap it to peek at the dashboard") {
            Switch(c.nightEnabled, onToggle = { scope.launch { DisplaySettings.setNightEnabled(context, !c.nightEnabled) } })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Starts", Modifier.weight(1f)) {
                PickerField(c.nightStart.fmt(), "") { pickTime(c.nightStart) { DisplaySettings.setNightStart(context, it) } }
            }
            Field("Ends", Modifier.weight(1f)) {
                PickerField(c.nightEnd.fmt(), "") { pickTime(c.nightEnd) { DisplaySettings.setNightEnd(context, it) } }
            }
        }
        Spacer(Modifier.height(14.dp))
        Field("Night brightness") {
            val shown = if (dragging >= 0f) dragging else c.nightBrightness
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Previews on the screen while held, then goes back to the normal level.
                FlatSlider(
                    shown,
                    onChange = { v ->
                        dragging = v.coerceAtLeast(0.01f)
                        setWindowBrightness(activity, dragging)
                    },
                    modifier = Modifier.weight(1f),
                    onRelease = {
                        val v = dragging
                        dragging = -1f
                        if (v >= 0f) scope.launch { DisplaySettings.setNightBrightness(context, v) }
                        setWindowBrightness(activity, if (c.isNight(LocalTime.now())) v else null)
                    },
                )
                Spacer(Modifier.width(10.dp))
                Txt("${(shown * 100).roundToInt()}%", Type.display16.copy(textAlign = TextAlign.End), Dash.ink, Modifier.width(44.dp))
            }
        }
    }
}

private fun LocalTime.fmt() = "%02d:%02d".format(hour, minute)

/** Window brightness override (0–1), or null to follow the system brightness. */
fun setWindowBrightness(activity: Activity, level: Float?) {
    val lp = activity.window.attributes
    lp.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    activity.window.attributes = lp
}

/** Bumps on every resume, so reads of system state keyed on it refresh after a settings trip. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    return tick
}

@Composable
fun DisplaySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by remember { DisplaySettings.config(context) }.collectAsStateWithLifecycle(null)
    val c = cfg ?: return
    val tick = rememberResumeTick()
    val home = remember(tick) { Device.isHomeApp(context) }

    Section("Display") {
        SettingRow("Keep screen on while charging", "Off the charger, the screen times out as usual") {
            Switch(c.keepAwakeCharging, onToggle = { scope.launch { DisplaySettings.setKeepAwake(context, !c.keepAwakeCharging) } })
        }
        SettingRow(
            "Use as home app",
            if (home) "Desk is the home app: it opens on boot and Home; the dot icon lists apps"
            else "Opens on boot and on Home. Choose Desk on the next screen",
        ) {
            MiniButton(if (home) "Change" else "Set", if (home) Dash.muted else Dash.ink) {
                Device.open(context, Device.homeSettingsIntent())
            }
        }
    }
}

@Composable
fun PermissionsSection() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val calendar = rememberCalendarPermission()
    val notifications = rememberRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
    val s = remember(tick) { SpecialAccess.read(context) }

    Section("Permissions") {
        PermRow("Calendar", "Today tile", calendar.granted, calendar.request)
        PermRow("Notification access", "Now playing (Spotify)", s.listener) {
            Device.open(context, MediaRepo.get(context).accessIntent())
        }
        PermRow("Modify system settings", "Brightness slider", s.writeSettings) {
            Device.open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg(context)))
        }
        PermRow("Notifications", "Alarm notification", notifications.granted, notifications.request)
        PermRow("Full-screen alerts", "Alarm over the lock screen", s.fullScreen) {
            Device.open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context)))
        }
        PermRow("Device admin", "Lock button (turn off before uninstalling)", s.admin) {
            Device.open(context, Lock.askIntent(context))
        }
        PermRow("Unrestricted battery", "Keeps Desk running in the background", s.battery) {
            Device.open(context, Device.batteryExemptionIntent(context))
        }
        Device.samsungSleepIntent(context)?.let { intent ->
            SettingRow("Never sleeping apps", "Samsung: Background usage limits › Never sleeping apps › add Desk") {
                MiniButton("Open", Dash.ink) { Device.open(context, intent) }
            }
        }
        SettingRow("Exact alarms, boot, internet", "Granted at install") { Granted() }
    }
}

@Composable
private fun PermRow(title: String, why: String, granted: Boolean, grant: () -> Unit) =
    SettingRow(title, why) { if (granted) Granted() else MiniButton("Grant", Dash.ink, Dash.slate, grant) }

@Composable
private fun Granted() = Txt("GRANTED", Type.meta.copy(letterSpacing = Type.small.letterSpacing), Dash.slateLight)

private fun pkg(context: Context) = Uri.parse("package:${context.packageName}")

/** Permissions granted on system pages rather than by a runtime dialog. */
private class SpecialAccess(
    val listener: Boolean, val writeSettings: Boolean, val fullScreen: Boolean, val admin: Boolean, val battery: Boolean,
) {
    companion object {
        fun read(c: Context) = SpecialAccess(
            listener = MediaRepo.get(c).hasAccess(),
            writeSettings = Settings.System.canWrite(c),
            fullScreen = c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            admin = Lock.isEnabled(c),
            battery = Device.isIgnoringBatteryOptimizations(c),
        )
    }
}

/** One-time prompt (SPEC: Samsung battery) so One UI doesn't put Desk to sleep. */
@Composable
fun BatteryPrompt(cfg: DisplayConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = rememberResumeTick()
    val exempt = remember(tick) { Device.isIgnoringBatteryOptimizations(context) }
    val exemptAtStart = remember { exempt }
    // Shown once; stays up after "Allow" so the Samsung list is still one tap away.
    if (cfg.batteryPrompted || exemptAtStart) return
    val done: () -> Unit = { scope.launch { DisplaySettings.setBatteryPrompted(context) } }
    val samsung = remember { Device.samsungSleepIntent(context) }

    Overlay(onDismiss = done, width = 480.dp) {
        Txt("KEEP DESK AWAKE", Type.label, Dash.ink)
        Spacer(Modifier.height(14.dp))
        Txt(
            "Android may put Desk to sleep in the background, which can stop the clock, music tile and " +
                "alarm from updating. Allow it to run unrestricted" +
                (if (samsung != null) ", then open Battery › Background usage limits › Never sleeping apps and add Desk." else "."),
            Type.bodySmall.copy(lineHeight = Type.bodySmall.fontSize * 1.6f), Dash.ink, maxLines = 6,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (exempt) Granted() else PrimaryButton("Allow") { Device.open(context, Device.batteryExemptionIntent(context)) }
            if (samsung != null) MiniButton("Samsung battery", Dash.ink) { Device.open(context, samsung) }
            Spacer(Modifier.weight(1f))
            MiniButton("Done", Dash.muted, onClick = done)
        }
    }
}
