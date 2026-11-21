package com.desk.dashboard.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Registers [receiver] for [filter] while the composable is in the tree. */
@Composable
private fun SystemBroadcast(filter: IntentFilter, onIntent: (Intent?) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = onIntent(intent)
        }
        // Sticky broadcasts (battery) return their last value immediately.
        onIntent(context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/**
 * Current time truncated to the minute. Driven by ACTION_TIME_TICK, which the system sends at
 * the start of every minute, so the clock changes on the minute without a polling loop. Time and
 * timezone changes also update it.
 */
@Composable
fun rememberMinute(): State<LocalDateTime> {
    val state = remember { mutableStateOf(now()) }
    val filter = remember {
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
    }
    SystemBroadcast(filter) { state.value = now() }
    return state
}

/** Today's date; only changes (and recomposes readers) at midnight. */
@Composable
fun rememberToday(): State<LocalDate> {
    val minute = rememberMinute()
    return remember { derivedStateOf { minute.value.toLocalDate() } }
}

private fun now() = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

data class Battery(val percent: Int, val charging: Boolean, val full: Boolean) {
    override fun toString() = when {
        full -> "$percent% · full"
        charging -> "$percent% · charging"
        else -> "$percent%"
    }
}

@Composable
fun rememberBattery(): State<Battery?> {
    val state = remember { mutableStateOf<Battery?>(null) }
    val filter = remember { IntentFilter(Intent.ACTION_BATTERY_CHANGED) }
    SystemBroadcast(filter) { intent ->
        intent ?: return@SystemBroadcast
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level >= 0) {
            state.value = Battery(
                percent = level * 100 / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged && status != BatteryManager.BATTERY_STATUS_FULL),
                full = plugged && status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }
    return state
}
