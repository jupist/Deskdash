package com.desk.dashboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desk.dashboard.system.SystemBroadcast
import kotlin.math.roundToInt

/**
 * Flat slider: 3 dp track, slate fill, 20 dp ink thumb. Follows the finger from the moment it
 * touches (no drag threshold, no need to hit the thumb), and the touch area is the full [height]
 * plus [reach] past each end of the track, so the ends are easy to hit. [onRelease] fires when the
 * finger lifts.
 */
@Composable
fun FlatSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    reach: Dp = 12.dp,
    onRelease: () -> Unit = {},
) {
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)
    BoxWithConstraints(modifier.height(height)) {
        val thumb = 20.dp
        val density = LocalDensity.current
        val reachPx = with(density) { reach.toPx() }
        val thumbPx = with(density) { thumb.toPx() }
        val trackPx = constraints.maxWidth - 2 * reachPx
        val trackW = maxWidth - reach * 2
        fun at(x: Float) = ((x - reachPx - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().fillMaxHeight()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        change(at(down.position.x))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val p = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!p.pressed) break
                            p.consume()
                            change(at(p.position.x))
                        }
                        release()
                    }
                }
                .padding(horizontal = reach),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(tc.line))
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(3.dp).background(tc.accentFill))
            Box(
                Modifier.offset(x = (trackW - thumb) * value.coerceIn(0f, 1f))
                    .size(thumb).clip(CircleShape).background(tc.ink)
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, onChange: (Float) -> Unit, onRelease: () -> Unit = {},
    modifier: Modifier = Modifier, fill: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Txt(label.uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing), tc.muted, if (fill) Modifier.width(44.dp) else Modifier)
        // 150 dp track plus 12 dp of extra touch area at each end.
        FlatSlider(value, onChange, if (fill) Modifier.weight(1f) else Modifier.width(174.dp), onRelease = onRelease)
        Txt("${(value * 100).roundToInt()}", Type.display16.copy(textAlign = TextAlign.End), tc.ink, Modifier.width(30.dp))
    }
}

/** Volume (STREAM_MUSIC) slider; follows hardware volume key changes too. */
@Composable
fun VolumeSlider(modifier: Modifier = Modifier, fill: Boolean = false) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var v by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max) }
    val filter = remember { IntentFilter("android.media.VOLUME_CHANGED_ACTION") }
    SystemBroadcast(filter) { v = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max }

    LabeledSlider("Vol", v, modifier = modifier, fill = fill, onChange = {
        v = it
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0)
    })
}

/**
 * Brightness slider. Sets the window brightness for an instant change, and writes the system
 * value (manual mode) when "Modify system settings" is allowed. Without it, the first release
 * opens that permission screen.
 */
@Composable
fun BrightnessSlider(modifier: Modifier = Modifier, fill: Boolean = false) {
    val context = LocalContext.current
    val activity = context as Activity
    var v by remember { mutableFloatStateOf(readBrightness(context)) }
    var asked by remember { mutableStateOf(false) } // sent the user to the permission screen already

    LabeledSlider(
        "Light", v, modifier = modifier, fill = fill,
        onChange = {
            v = it
            val lp = activity.window.attributes
            lp.screenBrightness = it.coerceAtLeast(0.01f)
            activity.window.attributes = lp
            if (Settings.System.canWrite(context)) writeBrightness(context, it)
        },
        onRelease = {
            if (!Settings.System.canWrite(context) && !asked) {
                asked = true
                Toast.makeText(context, "Allow “Modify system settings” so brightness sticks outside the app", Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        },
    )
}

private fun readBrightness(context: Context): Float =
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

private fun writeBrightness(context: Context, v: Float) {
    val cr = context.contentResolver
    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
}
