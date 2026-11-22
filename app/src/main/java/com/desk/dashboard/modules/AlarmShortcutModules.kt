package com.desk.dashboard.modules

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.alarm.AlarmConfig
import com.desk.dashboard.alarm.Alarms
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.system.Apps
import com.desk.dashboard.system.rememberToday
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.FitText
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Overlay
import com.desk.dashboard.ui.PanelHeader
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.Switch
import com.desk.dashboard.ui.TextInput
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.system.ensureAlarmPermissions
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object AlarmModule : Module(
    "alarm", "Alarm", "Your wake-up alarm: rings over the lock screen, with snooze.",
    DashIcons.bell, Size(4, 2), Size(2, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val actions = LocalDashActions.current
        val cfg by remember { Alarms.config(context) }.collectAsStateWithLifecycle(null)
        val today by rememberToday()
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val c = cfg ?: return Tile(label(t)) {}
        val h24 = t.uses24h()
        val (time, ampm) = timeText(c.time, h24)
        val ink = if (c.enabled) tc.ink else tc.muted
        val toggle = {
            val on = !c.enabled
            if (on) ensureAlarmPermissions(context) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            scope.launch { Alarms.setEnabled(context, on) }
            Unit
        }

        Tile(label(t), meta = alarmMeta(c, today), metaColor = if (c.enabled) tc.accent else tc.muted) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val showDays = maxHeight > 90.dp && maxWidth > 270.dp // seven chips plus the switch
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    // Time and days open the alarm settings; the switch toggles.
                    Column(Modifier.weight(1f).fillMaxSize().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { actions?.openSettings?.invoke("alarm") }, verticalArrangement = Arrangement.Bottom) {
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
                            FitText(time, com.desk.dashboard.ui.Type.big, ink, Modifier.weight(1f, fill = false).fillMaxSize(), sample = "00:00", maxSize = 64f)
                            if (ampm != null) Txt(ampm, Type.body, ink, Modifier.padding(start = 4.dp, bottom = 6.dp))
                        }
                        if (showDays) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                DayOfWeek.entries.forEach { d ->
                                    val on = d in c.days && !c.once
                                    Box(
                                        Modifier.size(22.dp).border(1.dp, if (on) tc.accent else tc.line, Dash.smallShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Txt(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Type.meta.copy(letterSpacing = 0.sp), if (on) tc.ink else tc.muted)
                                    }
                                }
                            }
                        }
                    }
                    Switch(c.enabled, onToggle = toggle, on = tc.accentFill)
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val actions = LocalDashActions.current
        HoursSetting(t)
        SettingRow("Time, repeat, sound and snooze", "One alarm, shared by every alarm tile") {
            MiniButton("Open", Dash.ink) { actions?.openSettings?.invoke("alarm") }
        }
    }
}

fun alarmMeta(c: AlarmConfig, today: LocalDate): String {
    c.snoozeAt?.let {
        val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        return "Snoozed · %02d:%02d".format(t.hour, t.minute)
    }
    if (!c.enabled) return "Off"
    val next = c.next().toLocalDate()
    val day = when (next) {
        today -> "today"
        today.plusDays(1) -> "tmrw"
        else -> next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    return if (c.ringsOnce) "Once · $day" else "On · $day"
}

object ShortcutModule : Module(
    "shortcut", "App shortcut", "Opens an app of your choice.",
    DashIcons.app, Size(1, 1), Size(1, 1),
) {
    override fun label(t: TileScope) = t.strOrNull("title") ?: t.strOrNull("appLabel") ?: "App"

    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val overlay = LocalOverlay.current
        val pkg = t.strOrNull("pkg")
        val apps by Apps.list.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { if (apps == null) Apps.refresh(context) }
        val app = apps?.firstOrNull { it.component.packageName == pkg }
        BoxWithConstraints(
            Modifier.fillMaxSize().pressable({
                if (pkg != null) openApp(context, pkg)
                else overlay { AppPicker({ a -> t.setAll("pkg" to a.component.packageName, "appLabel" to a.label); overlay(null) }) { overlay(null) } }
            }).padding(10.dp)
        ) {
            val iconSize = (minOf(maxWidth, maxHeight) * 0.42f).coerceIn(24.dp, 72.dp)
            Column(Modifier.fillMaxSize()) {
                Txt(label(t).uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing), tc.ink)
                Spacer(Modifier.weight(1f))
                if (app != null) Image(app.icon, null, Modifier.size(iconSize))
                else Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { Icon(if (pkg == null) DashIcons.plus else DashIcons.app, tc.muted, iconSize / 2) }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val overlay = LocalOverlay.current
        SettingRow("App", t.strOrNull("appLabel") ?: "None chosen") {
            MiniButton("Choose", Dash.ink, Dash.accent) {
                overlay { AppPicker({ a -> t.setAll("pkg" to a.component.packageName, "appLabel" to a.label); overlay(null) }) { overlay(null) } }
            }
        }
    }
}

@Composable
fun AppPicker(onPick: (com.desk.dashboard.system.LaunchableApp) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val apps by Apps.list.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Apps.refresh(context) }
    var q by remember { mutableStateOf("") }
    Overlay(onDismiss = onClose, width = 460.dp) {
        PanelHeader("Choose an app", onClose)
        TextInput(q, { q = it }, "Search apps", 40)
        Spacer(Modifier.height(8.dp))
        val list = apps.orEmpty().filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
            items(list, key = { it.component.flattenToString() }) { a ->
                Row(Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(a.icon, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Txt(a.label, Type.body, Dash.ink, Modifier.weight(1f))
                }
            }
        }
        if (apps == null) Txt("Loading…", Type.meta, Dash.muted, Modifier.fillMaxWidth().padding(8.dp))
    }
}

