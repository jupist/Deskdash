package com.desk.dashboard.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.system.is24h
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.system.rememberSecond
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.FitText
import com.desk.dashboard.ui.IconButton
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Overlay
import com.desk.dashboard.ui.PanelHeader
import com.desk.dashboard.ui.Rule
import com.desk.dashboard.ui.Segmented
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.TextInput
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.TileStyle
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.tc
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun timeText(t: java.time.temporal.TemporalAccessor, h24: Boolean, seconds: Boolean = false): Pair<String, String?> {
    val pattern = (if (h24) "HH:mm" else "h:mm") + if (seconds) ":ss" else ""
    val main = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(t)
    val ampm = if (h24) null else DateTimeFormatter.ofPattern("a", Locale.ENGLISH).format(t).uppercase()
    return main to ampm
}

/** Reads the 12/24-hour choice: "system", "24" or "12". */
@Composable
fun TileScope.uses24h(): Boolean {
    val context = LocalContext.current
    return when (str("hours", "system")) { "24" -> true; "12" -> false; else -> is24h(context) }
}

@Composable
fun HoursSetting(t: TileScope) = SettingRow("Hour format") {
    Segmented(listOf("system", "24", "12"), t.str("hours", "system"), { if (it == "system") "Auto" else "${it}h" }) { t.set("hours", it) }
}

object ClockModule : Module(
    "clock", "Clock", "The time and date, as large as the tile allows.",
    DashIcons.clock, Size(4, 2), Size(2, 1), TileStyle.ACCENT,
) {
    override fun label(t: TileScope) = t.str("title", "Now")

    @Composable
    override fun Content(t: TileScope) {
        val seconds = t.bool("seconds", false)
        val now = if (seconds) rememberSecond().value else rememberMinute().value
        val (time, ampm) = timeText(now, t.uses24h(), seconds)
        val sample = (if (t.uses24h()) "00:00" else "10:00") + if (seconds) ":00" else ""
        val showDate = t.bool("date", true)
        Tile(label(t)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                val wide = maxWidth > maxHeight * 2.2f
                val date1 = now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
                val date2 = now.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())).uppercase()
                if (wide && showDate) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                        FitText(time, Type.clock, tc.ink, Modifier.weight(1f).fillMaxSize(), sample = sample, maxSize = 220f)
                        Column(horizontalAlignment = Alignment.End) {
                            if (ampm != null) Txt(ampm, Type.body, tc.ink)
                            Txt(date1, Type.body, tc.ink)
                            Txt(date2, Type.body, tc.ink)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
                            FitText(time, Type.clock, tc.ink, Modifier.weight(1f).fillMaxSize(), sample = sample, maxSize = 220f)
                            if (ampm != null) Txt(ampm, Type.body, tc.ink, Modifier.padding(start = 6.dp, bottom = 6.dp))
                        }
                        if (showDate && boxH > 70.dp) {
                            Spacer(Modifier.height(4.dp))
                            Txt("$date1 · $date2", Type.body, tc.ink)
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        HoursSetting(t)
        SwitchRow("Show seconds", checked = t.bool("seconds", false), onToggle = { t.set("seconds", !t.bool("seconds", false)) })
        SwitchRow("Show date", checked = t.bool("date", true), onToggle = { t.set("date", !t.bool("date", true)) })
    }
}

/** A city name for a zone id: "Asia/Kolkata" → "Kolkata". */
fun cityOf(zone: String) = zone.substringAfterLast('/').replace('_', ' ')

object WorldClockModule : Module(
    "worldclock", "World clocks", "The time in other cities, with the day offset.",
    DashIcons.globe, Size(3, 2), Size(2, 1),
) {
    override fun initialConfig() = mapOf("zones" to listOf("Europe/London", "America/New_York", "Asia/Tokyo"))

    @Composable
    override fun Content(t: TileScope) {
        val now = rememberMinute().value
        val h24 = t.uses24h()
        val zones = t.strings("zones")
        val here = ZoneId.systemDefault()
        Tile(label(t)) {
            if (zones.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Txt("Add cities in the tile's settings", Type.bodySmall, tc.muted, maxLines = 2)
                return@Tile
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                val rowH = (maxHeight / zones.size).coerceAtLeast(28.dp)
                Column {
                    zones.forEach { z ->
                        val zone = runCatching { ZoneId.of(z) }.getOrNull() ?: return@forEach
                        val there = ZonedDateTime.now(zone)
                        val local = now.atZone(here).withZoneSameInstant(zone)
                        val dayDiff = local.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
                        val (time, ampm) = timeText(there, h24)
                        Column(Modifier.fillMaxWidth().heightIn(min = 28.dp).height(rowH)) {
                            Rule()
                            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(cityOf(z), Type.body, tc.ink)
                                    if (rowH > 40.dp) {
                                        Txt(
                                            when (dayDiff) { 0L -> "Today"; 1L -> "Tomorrow"; -1L -> "Yesterday"; else -> "" } +
                                                offsetLabel(zone, here),
                                            Type.meta, tc.muted,
                                        )
                                    }
                                }
                                Txt(time + (ampm?.let { " $it" } ?: ""), Type.display20, tc.ink)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun offsetLabel(zone: ZoneId, here: ZoneId): String {
        val now = java.time.Instant.now()
        val diff = (zone.rules.getOffset(now).totalSeconds - here.rules.getOffset(now).totalSeconds) / 60
        if (diff == 0) return ""
        val sign = if (diff > 0) "+" else "−"
        val h = kotlin.math.abs(diff) / 60
        val m = kotlin.math.abs(diff) % 60
        return " · $sign${h}h" + if (m != 0) " ${m}m" else ""
    }

    @Composable
    override fun Settings(t: TileScope) {
        val overlay = LocalOverlay.current
        HoursSetting(t)
        val zones = t.strings("zones")
        zones.forEachIndexed { i, z ->
            SettingRow(cityOf(z), z) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (i > 0) IconButton(DashIcons.back, size = 30.dp, iconSize = 12.dp) {
                        t.set("zones", zones.toMutableList().apply { add(i - 1, removeAt(i)) })
                    }
                    IconButton(DashIcons.close, size = 30.dp, iconSize = 12.dp) { t.set("zones", zones - z) }
                }
            }
        }
        SettingRow("Add a city") {
            MiniButton("Add", Dash.ink, Dash.accent) {
                overlay { ZonePicker(onPick = { t.set("zones", (zones + it).distinct()); overlay(null) }, onClose = { overlay(null) }) }
            }
        }
    }
}

private val allZones by lazy {
    ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it[0].isUpperCase() }
        .sortedBy { cityOf(it) }
}

@Composable
fun ZonePicker(onPick: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    Overlay(onDismiss = onClose, width = 460.dp) {
        PanelHeader("Add a city", onClose)
        TextInput(q, { q = it }, "Search, e.g. Tokyo or London", 40)
        Spacer(Modifier.height(10.dp))
        val list = remember(q) {
            val needle = q.trim().lowercase()
            allZones.filter { needle.isEmpty() || cityOf(it).lowercase().contains(needle) || it.lowercase().contains(needle) }.take(60)
        }
        LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
            items(list) { z ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(z) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(cityOf(z), Type.body, Dash.ink, Modifier.weight(1f))
                    Txt(z.substringBefore('/'), Type.meta, Dash.muted)
                    Spacer(Modifier.width(10.dp))
                    Txt(
                        ZonedDateTime.now(ZoneId.of(z)).format(DateTimeFormatter.ofPattern("HH:mm")),
                        Type.bodySmall, Dash.muted,
                    )
                }
            }
        }
    }
}
