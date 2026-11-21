package com.desk.dashboard.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.data.conditionWord
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val tempStyle = Type.big.copy(fontSize = 48.sp, lineHeight = 46.sp)

@Composable
fun WeatherTile(modifier: Modifier = Modifier) {
    val state by WeatherRepo.get(LocalContext.current).state.collectAsStateWithLifecycle()
    val f = state.forecast

    if (f == null) {
        val msg = when {
            !state.loaded -> ""
            state.city == null -> "Set a city in settings"
            state.failed -> "Offline · ${state.city}"
            else -> "Loading ${state.city}…"
        }
        Tile("Weather", modifier, meta = state.city) {
            Spacer(Modifier.weight(1f))
            Txt("--°", tempStyle, Dash.muted)
            Spacer(Modifier.height(6.dp))
            Txt(msg, Type.meta, Dash.muted)
        }
        return
    }

    // Cached data after a failed fetch is shown muted.
    val ink = if (state.failed) Dash.muted else Dash.ink
    val days = f.daily.max.indices.take(5)
    Tile("Weather", modifier, meta = conditionWord(f.current.code)) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Txt("${f.current.temp.roundToInt()}°", tempStyle, ink, Modifier.weight(1f))
            if (days.isNotEmpty()) {
                Txt("${f.daily.max[0].roundToInt()}° / ${f.daily.min[0].roundToInt()}°", Type.body, ink)
            }
        }
        Spacer(Modifier.height(10.dp))

        // Five bars for the daily highs, scaled between 35% and 100% of the bar area.
        val highs = days.map { f.daily.max[it] }
        val lo = highs.minOrNull() ?: 0.0
        val hi = highs.maxOrNull() ?: 1.0
        val alpha = if (state.failed) 0.4f else 1f
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            highs.forEachIndexed { i, h ->
                val frac = if (hi - lo < 0.5) 0.7f else (0.35 + 0.65 * (h - lo) / (hi - lo)).toFloat()
                Box(
                    Modifier.weight(1f).fillMaxHeight(frac)
                        .background((if (i == 0) Dash.slate else Dash.sand).copy(alpha = alpha))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val dates = f.daily.dates
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach {
                Txt(
                    dates[it].dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                    Type.small.copy(textAlign = TextAlign.Center), Dash.muted, Modifier.weight(1f),
                )
            }
        }
    }
}
