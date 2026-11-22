package com.desk.dashboard.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.data.Forecast
import com.desk.dashboard.data.Sky
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.data.conditionWord
import com.desk.dashboard.data.skyOf
import com.desk.dashboard.system.rememberBattery
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.FitText
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Overlay
import com.desk.dashboard.ui.PanelHeader
import com.desk.dashboard.ui.Rule
import com.desk.dashboard.ui.Segmented
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun skyIcon(code: Int, day: Boolean = true): ImageVector = when (skyOf(code)) {
    Sky.CLEAR -> if (day) DashIcons.sun else DashIcons.moon
    Sky.PARTLY -> if (day) DashIcons.partly else DashIcons.moon
    Sky.CLOUD -> DashIcons.cloud
    Sky.FOG -> DashIcons.fog
    Sky.RAIN -> DashIcons.rain
    Sky.SNOW -> DashIcons.snow
    Sky.STORM -> DashIcons.storm
}

private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()

object WeatherModule : Module(
    "weather", "Weather", "Current conditions and the days ahead (Open-Meteo, no key).",
    DashIcons.cloud, Size(3, 2), Size(1, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val repo = remember { WeatherRepo.get(context) }
        val state by repo.state.collectAsStateWithLifecycle()
        val actions = LocalDashActions.current
        val overlay = LocalOverlay.current
        val f = state.forecast

        if (f == null) {
            Tile(label(t), Modifier.pressable({ actions?.openSettings?.invoke("weather") })) {
                Spacer(Modifier.weight(1f))
                FitText("--°", Type.big, tc.muted, Modifier.fillMaxWidth().weight(1f), maxSize = 64f)
                Txt(
                    when {
                        !state.loaded -> ""
                        state.city == null -> "Tap to set a place"
                        state.failed -> "Offline · ${state.city}"
                        else -> "Loading ${state.city}…"
                    },
                    Type.meta, tc.muted, maxLines = 2,
                )
            }
            return
        }

        // Cached data after a failed fetch is shown muted.
        val ink = if (state.failed) tc.muted else tc.ink
        val meta = if (t.bool("place", false)) state.city else conditionWord(f.current.code)
        Tile(label(t), Modifier.pressable({ overlay { WeatherDetail(f, state.city, state.failed) { overlay(null) } } }, 0.98f), meta = meta) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                val roomForDays = maxHeight > 110.dp && maxWidth > 120.dp
                val maxDays = ((maxWidth / 34.dp).toInt()).coerceIn(2, 7)
                val days = f.daily.max.indices.take(minOf(t.int("days", 5), maxDays))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
                        if (boxW > 90.dp) {
                            Icon(skyIcon(f.current.code, f.current.isDay == 1), ink, 22.dp, Modifier.padding(bottom = 10.dp, end = 8.dp))
                        }
                        FitText("${f.current.temp.roundToInt()}°", Type.big, ink, Modifier.weight(1f).fillMaxHeight(), sample = "-00°", maxSize = 72f)
                        if (boxW > 140.dp && f.daily.max.isNotEmpty()) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                                Txt("${f.daily.max[0].roundToInt()}° / ${f.daily.min[0].roundToInt()}°", Type.body, ink)
                                f.current.feels?.let { Txt("Feels ${it.roundToInt()}°", Type.meta, tc.muted) }
                            }
                        }
                    }
                    if (roomForDays && t.bool("bars", true) && days.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        DayBars(f, days, state.failed)
                    }
                }
            }
        }
    }

    @Composable
    private fun DayBars(f: Forecast, days: List<Int>, stale: Boolean) {
        // Bars for the daily highs, scaled between 35% and 100% of the bar area.
        val highs = days.map { f.daily.max[it] }
        val lo = highs.minOrNull() ?: 0.0
        val hi = highs.maxOrNull() ?: 1.0
        val alpha = if (stale) 0.4f else 1f
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            highs.forEachIndexed { i, h ->
                val frac = if (hi - lo < 0.5) 0.7f else (0.35 + 0.65 * (h - lo) / (hi - lo)).toFloat()
                Box(
                    Modifier.weight(1f).fillMaxHeight(frac).clip(Dash.smallShape)
                        .background((if (i == 0) tc.accentFill else tc.muted).copy(alpha = alpha))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach {
                Txt(dayName(f.daily.dates[it]), Type.small.copy(textAlign = TextAlign.Center), tc.muted, Modifier.weight(1f))
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val actions = LocalDashActions.current
        SettingRow("Days shown", "Fewer if the tile is narrow") {
            Segmented(listOf(3, 5, 7), t.int("days", 5), { "$it" }) { t.set("days", it) }
        }
        SwitchRow("Forecast bars", checked = t.bool("bars", true), onToggle = { t.set("bars", !t.bool("bars", true)) })
        SwitchRow("Show place instead of condition", checked = t.bool("place", false), onToggle = { t.set("place", !t.bool("place", false)) })
        SettingRow("Place and units", "Shared by all weather tiles") {
            MiniButton("Open", Dash.ink) { actions?.openSettings?.invoke("weather") }
        }
    }
}

@Composable
private fun WeatherDetail(f: Forecast, city: String?, stale: Boolean, onClose: () -> Unit) {
    Overlay(onDismiss = onClose, width = 520.dp) {
        PanelHeader(city ?: "Weather", onClose)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(skyIcon(f.current.code, f.current.isDay == 1), Dash.ink, 34.dp)
            Spacer(Modifier.width(14.dp))
            Txt("${f.current.temp.roundToInt()}°", Type.big, Dash.ink)
            Spacer(Modifier.width(16.dp))
            Column {
                Txt(conditionWord(f.current.code), Type.body, Dash.ink)
                Txt(
                    listOfNotNull(
                        f.current.feels?.let { "Feels ${it.roundToInt()}°" },
                        f.current.humidity?.let { "Humidity ${it.roundToInt()}%" },
                        f.current.wind?.let { "Wind ${it.roundToInt()}" },
                    ).joinToString(" · "),
                    Type.meta, Dash.muted,
                )
            }
        }
        if (stale) {
            Spacer(Modifier.height(6.dp))
            Txt("Offline: showing the last forecast", Type.meta, Dash.danger)
        }
        f.hourly?.let { h ->
            // The next 24 hours, from the current hour.
            val nowHour = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
            val hours = h.time.indices.filter { runCatching { !java.time.LocalDateTime.parse(h.time[it]).isBefore(nowHour) }.getOrDefault(false) }.take(24)
            if (hours.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(hours.size) { k ->
                        val i = hours[k]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(if (k == 0) "NOW" else h.time[i].substring(11, 13), Type.small, Dash.muted)
                            Spacer(Modifier.height(6.dp))
                            Icon(skyIcon(h.code.getOrElse(i) { 3 }, h.isDay.getOrElse(i) { 1 } == 1), Dash.ink, 16.dp)
                            Spacer(Modifier.height(6.dp))
                            Txt("${h.temp[i].roundToInt()}°", Type.body, Dash.ink)
                            val r = h.rain.getOrNull(i) ?: 0
                            Txt(if (r >= 10) "$r%" else " ", Type.small, Dash.accentText)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        f.daily.dates.forEachIndexed { i, d ->
            Rule(color = Dash.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (i == 0) "TODAY" else dayName(d), Type.body, Dash.ink, Modifier.width(70.dp))
                Icon(skyIcon(f.daily.code.getOrElse(i) { 3 }), Dash.ink, 18.dp)
                Spacer(Modifier.width(10.dp))
                Txt(conditionWord(f.daily.code.getOrElse(i) { 3 }), Type.bodySmall, Dash.muted, Modifier.weight(1f))
                f.daily.rain.getOrNull(i)?.let { Txt("$it%", Type.meta, Dash.accentText, Modifier.width(44.dp)) }
                Txt("${f.daily.max[i].roundToInt()}° / ${f.daily.min[i].roundToInt()}°", Type.body, Dash.ink)
            }
        }
    }
}

object SystemModule : Module(
    "system", "System", "Battery, storage, network and uptime.",
    DashIcons.battery, Size(3, 2), Size(1, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val battery = rememberBattery().value
        val minute by rememberMinute()
        val stats = remember(minute) { SystemStats.read(context) }
        Tile(label(t), meta = battery?.let { if (it.charging) "Charging" else if (it.full) "Full" else null }) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                if (maxHeight < 90.dp || maxWidth < 120.dp) {
                    FitText("${battery?.percent ?: "--"}%", Type.big, tc.ink, Modifier.fillMaxSize(), sample = "100%", maxSize = 64f)
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        StatRow("Battery", "${battery?.percent ?: "--"}%" + (stats.batteryTemp?.let { " · $it°C" } ?: ""))
                        StatRow("Storage", stats.storage)
                        if (boxH > 130.dp) StatRow("Network", stats.network)
                        if (boxH > 160.dp) StatRow("Uptime", stats.uptime)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatRow(k: String, v: String) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Txt(k.uppercase(), Type.meta, tc.muted, Modifier.weight(1f))
            Txt(v, Type.body, tc.ink)
        }
    }
}

private class SystemStats(val storage: String, val network: String, val uptime: String, val batteryTemp: Int?) {
    companion object {
        fun read(c: Context): SystemStats {
            val fs = StatFs(Environment.getDataDirectory().path)
            val free = fs.availableBytes / 1e9
            val total = fs.totalBytes / 1e9
            val cm = c.getSystemService(ConnectivityManager::class.java)
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            val net = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            } + if (caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) " · no internet" else ""
            val up = SystemClock.elapsedRealtime() / 60_000
            val uptime = if (up >= 1440) "${up / 1440}d ${up / 60 % 24}h" else "${up / 60}h ${up % 60}m"
            val temp = c.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10 }
            return SystemStats("%.0f of %.0f GB free".format(free, total), net, uptime, temp)
        }
    }
}

object QuoteModule : Module(
    "quote", "Quote", "A new line every day. Tap for another.",
    DashIcons.quote, Size(4, 2), Size(2, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val today = rememberMinute().value.toLocalDate()
        var bump by remember { mutableIntStateOf(0) }
        val index = ((today.toEpochDay() + bump) % Quotes.all.size).toInt()
        val (text, author) = Quotes.all[index]
        Tile(label(t), Modifier.pressable({ bump++ }, 0.98f)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxW = maxWidth; val boxH = maxHeight
                val lines = (maxHeight / 22.dp).toInt().coerceAtLeast(1)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Txt("“$text”", Type.display20.copy(lineHeight = Type.display20.fontSize * 1.25f), tc.ink, maxLines = (lines - 1).coerceAtLeast(1))
                    if (lines > 2) {
                        Spacer(Modifier.height(8.dp))
                        Txt("— $author", Type.meta, tc.muted)
                    }
                }
            }
        }
    }
}
