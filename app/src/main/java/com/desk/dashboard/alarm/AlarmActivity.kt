package com.desk.dashboard.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen ringing screen, shown over the lock screen. Finishes when ringing stops. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT < 27) {
            // Android 8.0 ignores the manifest's showWhenLocked/turnScreenOn.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) { if (!ringing) finish() }
            val snooze by remember { Alarms.config(this) }.collectAsStateWithLifecycle(null)
            BackHandler { /* back must not silently dismiss the alarm */ }
            val label by AlarmService.label.collectAsStateWithLifecycle()
            val canSnooze by AlarmService.snoozable.collectAsStateWithLifecycle()
            AlarmScreen(
                label = label,
                snoozeMinutes = if (canSnooze) snooze?.snoozeMinutes ?: 9 else null,
                onStop = { AlarmService.stop(this); finish() },
                onSnooze = { AlarmService.snooze(this); finish() },
            )
        }
    }
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val day = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH)

@Composable
private fun AlarmScreen(label: String, snoozeMinutes: Int?, onStop: () -> Unit, onSnooze: () -> Unit) {
    val now by rememberMinute()
    Column(Modifier.fillMaxSize().background(Dash.frame).padding(Dash.gap), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        // Time, on slate like the dashboard's time tile.
        Column(
            Modifier.fillMaxWidth().weight(1.2f).clip(Dash.shape).background(Dash.accent).padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Txt(label.uppercase(), Type.label, Dash.onAccent)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Txt(now.format(hhmm), Type.clock.copy(fontSize = 160.sp, lineHeight = 140.sp), Dash.onAccent, Modifier.weight(1f))
                Txt(now.format(day).uppercase(), Type.body.copy(fontSize = 18.sp, lineHeight = 22.sp), Dash.onAccent)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Dash.gap)) {
            if (snoozeMinutes != null) BigButton("Snooze $snoozeMinutes min", Dash.tile, Dash.ink, Dash.line, Modifier.weight(1f), onSnooze)
            BigButton(if (snoozeMinutes != null) "Stop" else "Dismiss", Dash.ink, Dash.frame, null, Modifier.weight(1f), onStop)
        }
    }
}

@Composable
private fun BigButton(text: String, fill: Color, ink: Color, outline: Color?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(Dash.shape).background(fill)
            .let { if (outline != null) it.border(1.dp, outline, Dash.shape) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text.uppercase(), Type.secondary.copy(fontSize = 34.sp, lineHeight = 38.sp), ink)
    }
}
