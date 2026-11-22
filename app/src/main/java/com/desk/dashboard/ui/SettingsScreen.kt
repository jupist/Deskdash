package com.desk.dashboard.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.BuildConfig
import com.desk.dashboard.board.BoardStore
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.data.DisplaySettings
import kotlinx.coroutines.launch

private class Page(val id: String, val title: String, val icon: ImageVector, val summary: String)

private val pages = listOf(
    Page("appearance", "Appearance", DashIcons.palette, "Theme, fonts, corners"),
    Page("dashboard", "Dashboard", DashIcons.grid, "Layout, presets, top bar"),
    Page("calendars", "Calendars", DashIcons.calendar, "Device, iCal links, Google"),
    Page("countdowns", "Countdowns", DashIcons.flag, "Dates you're counting down to"),
    Page("weather", "Weather", DashIcons.cloud, "Place and units"),
    Page("alarm", "Alarm", DashIcons.bell, "Time, repeat, sound, snooze"),
    Page("night", "Night mode", DashIcons.moon, "Schedule and dim clock"),
    Page("display", "Display & power", DashIcons.phone, "Screen on, home app"),
    Page("permissions", "Permissions", DashIcons.shield, "What Deskdash can access"),
    Page("about", "About", DashIcons.info, "Version and credits"),
)

/**
 * Settings: a list of pages beside the selected page on wide screens; on narrow screens the list
 * and the page take turns. [initial] opens straight at a page (from a tile's shortcut).
 */
@Composable
fun SettingsScreen(initial: String?, onDone: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(initial) }
    var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Dash.frame)) {
        val wide = maxWidth >= 720.dp
        val page = pages.firstOrNull { it.id == current } ?: if (wide) pages[0] else null
        BackHandler {
            when {
                overlay != null -> overlay = null
                !wide && page != null -> current = null
                else -> onDone()
            }
        }
        Column(Modifier.fillMaxSize().imePadding().padding(Dash.gap)) {
            Row(Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!wide && page != null) {
                    IconButton(DashIcons.back, outline = false) { current = null }
                    HSpace(8.dp)
                }
                Txt(if (!wide && page != null) page.title else "Settings", Type.secondary, Dash.ink, Modifier.weight(1f))
                OutlineButton("Done", DashIcons.check, onDone)
            }
            Spacer(Modifier.height(Dash.gap))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Dash.gap)) {
                if (wide || page == null) {
                    Column(
                        (if (wide) Modifier.width(260.dp) else Modifier.fillMaxWidth()).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        pages.forEach { p -> NavItem(p, selected = wide && p == page) { current = p.id } }
                    }
                }
                if (page != null) {
                    AnimatedContent(page, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) }, label = "page", modifier = Modifier.weight(1f)) { p ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dash.gap),
                        ) {
                            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
                                if (wide) Txt(p.title, Type.secondary.copy(fontSize = Type.display20.fontSize), Dash.ink, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                                PageContent(p.id) { overlay = it }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
        overlay?.invoke()
    }
}

@Composable
private fun NavItem(p: Page, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Dash.smallShape)
            .background(if (selected) Dash.tile else androidx.compose.ui.graphics.Color.Transparent)
            .pressable(onClick, 0.98f).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.icon, if (selected) Dash.accentText else Dash.muted, 18.dp)
        HSpace(14.dp)
        Column(Modifier.weight(1f)) {
            Txt(p.title, Type.body, if (selected) Dash.ink else Dash.ink)
            Txt(p.summary, Type.meta, Dash.muted)
        }
    }
}

@Composable
private fun PageContent(id: String, showOverlay: ((@Composable () -> Unit)?) -> Unit) {
    when (id) {
        "appearance" -> AppearancePage()
        "dashboard" -> DashboardPage()
        "calendars" -> {
            CalendarSection()
            SubscriptionsSection()
            GoogleSection()
        }
        "countdowns" -> DatesSection(showOverlay)
        "weather" -> WeatherSection()
        "alarm" -> AlarmSection(showOverlay)
        "night" -> {
            NightSection(showOverlay)
            NightExtras()
        }
        "display" -> DisplaySection()
        "permissions" -> PermissionsSection()
        "about" -> AboutPage()
    }
}

@Composable
private fun DashboardPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = LocalDashActions.current
    val cfg by remember { DisplaySettings.config(context) }.collectAsStateWithLifecycle(null)
    val boards by BoardStore.boards.collectAsStateWithLifecycle()
    val c = cfg ?: return
    Section("Layout") {
        Txt(
            "Long-press any tile on the dashboard to rearrange: drag to move, drag the corner to resize, × to remove. " +
                "Each screen size keeps its own arrangement.",
            Type.meta, Dash.muted, maxLines = 4,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniButton("Edit layout", Dash.ink, Dash.accent) { actions?.editLayout?.invoke() }
            MiniButton("Reset to default", Dash.muted) { BoardStore.resetActive() }
        }
    }
    Section("Presets") {
        Txt("Saved dashboards you can switch between. The one you pick is the dashboard.", Type.meta, Dash.muted, maxLines = 2)
        val b = boards ?: return@Section
        b.presets.forEach { p ->
            RowDivider()
            SettingRow(p.name, if (p.id == b.activeId) "In use" else null, onClick = { BoardStore.setActive(p.id) }) {
                if (p.id == b.activeId) Icon(DashIcons.check, Dash.accentText, 16.dp)
                else if (b.presets.size > 1) IconButton(DashIcons.trash, size = 30.dp, iconSize = 13.dp) { BoardStore.delete(p.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniButton("Duplicate current", Dash.ink) { BoardStore.create("${b.active.name} copy", copyCurrent = true) }
            MiniButton("New from default", Dash.ink) { BoardStore.create("Preset ${b.presets.size + 1}", copyCurrent = false) }
        }
    }
    Section("Top bar") {
        SwitchRow("Show the top bar", checked = c.topBar) { scope.launch { DisplaySettings.setTopBar(context, !c.topBar) } }
        if (c.topBar) {
            SwitchRow("Battery", checked = c.barBattery) { scope.launch { DisplaySettings.setBarBattery(context, !c.barBattery) } }
            SwitchRow("Volume and brightness sliders", "Shown on wide screens", c.barSliders) { scope.launch { DisplaySettings.setBarSliders(context, !c.barSliders) } }
            SwitchRow("Lock button", checked = c.barLock) { scope.launch { DisplaySettings.setBarLock(context, !c.barLock) } }
        }
        if (!c.topBar) Txt("Without the bar, open settings and edit mode by long-pressing a tile.", Type.meta, Dash.muted, maxLines = 2)
    }
    Section("Screen") {
        SettingRow("Orientation") {
            Segmented(listOf("auto", "landscape", "portrait"), c.orientation, { it.replaceFirstChar(Char::uppercase) }) { o ->
                scope.launch { DisplaySettings.setOrientation(context, o) }
            }
        }
    }
}

@Composable
private fun NightExtras() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by remember { DisplaySettings.config(context) }.collectAsStateWithLifecycle(null)
    val c = cfg ?: return
    Section("Night screen") {
        SwitchRow("Next alarm", checked = c.nightAlarm) { scope.launch { DisplaySettings.setNightAlarm(context, !c.nightAlarm) } }
        SwitchRow("Next event", "From your calendars", c.nightEvent) { scope.launch { DisplaySettings.setNightEvent(context, !c.nightEvent) } }
        SwitchRow("Seconds", checked = c.nightSeconds) { scope.launch { DisplaySettings.setNightSeconds(context, !c.nightSeconds) } }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    Section("Deskdash") {
        SettingRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        RowDivider()
        NavRow("Source code and releases", "GitHub") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jupist/Deskdash")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    Section("Credits") {
        Txt(
            "Weather: Open-Meteo (CC BY 4.0). Place search: Photon by Komoot, © OpenStreetMap contributors (ODbL). " +
                "Rain recording: “October rain in Nepal” by Nirmal Dulal (CC BY-SA 4.0). " +
                "Fonts: Share Tech Mono, Space Mono, Space Grotesk, DM Serif Display and DM Sans (SIL Open Font License). " +
                "iCalendar parsing: biweekly (BSD).",
            Type.meta, Dash.muted, maxLines = 12,
        )
    }
}
