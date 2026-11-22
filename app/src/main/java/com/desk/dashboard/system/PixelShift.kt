package com.desk.dashboard.system

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Burn-in protection, the way always-on displays do it: the whole picture moves a few pixels to a
 * new spot in a small orbit every [periodMs], so no pixel shows the same thing for hours. The
 * moves glide slowly, so they aren't noticed. Returns zero when [enabled] is false.
 */
@Composable
fun rememberPixelShift(enabled: Boolean, radius: Dp = 3.dp, periodMs: Long = 120_000): State<DpOffset> {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled, periodMs) {
        while (enabled) {
            delay(periodMs)
            step++
        }
    }
    // A 3 × 3 walk that visits every spot before repeating, never jumping across the middle.
    val path = remember { listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1) }
    val (dx, dy) = if (enabled) path[step % path.size] else (0 to 0)
    val x by animateDpAsState(radius * dx, tween(4000), label = "shiftX")
    val y by animateDpAsState(radius * dy, tween(4000), label = "shiftY")
    return rememberUpdatedState(DpOffset(x, y))
}
