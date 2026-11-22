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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
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

/**
 * The other half of burn-in protection. Moving the picture only spares pixels near edges: inside a
 * large block (a filled tile, a big digit) the same pixels stay lit however far it shifts. This
 * blacks out every other pixel in a checkerboard and swaps which half every [periodMs], so each
 * pixel is lit about half as long and wear is spread evenly across solid areas too.
 * Drawn in device pixels, so it costs one shader fill per frame.
 */
@Composable
fun Modifier.pixelGrid(enabled: Boolean, periodMs: Long = 30_000): Modifier {
    if (!enabled) return this
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            phase = 1 - phase
        }
    }
    val brush = remember {
        // 2 × 2 tile: black on one diagonal, clear on the other; repeated across the screen.
        val bmp = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, android.graphics.Color.BLACK)
            setPixel(1, 1, android.graphics.Color.BLACK)
        }
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    return drawWithContent {
        drawContent()
        // Shifting the pattern by one pixel swaps which half of the pixels is dark.
        translate(left = phase.toFloat()) { drawRect(brush, topLeft = Offset(-1f, 0f), size = Size(size.width + 2f, size.height)) }
    }
}
