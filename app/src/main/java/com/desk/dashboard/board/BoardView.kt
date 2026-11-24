package com.desk.dashboard.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.LocalShowLabel
import com.desk.dashboard.ui.LocalTileSize
import com.desk.dashboard.ui.LocalTileColors
import com.desk.dashboard.ui.TileStyle
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.tileColors
import kotlin.math.roundToInt

private enum class DragKind { MOVE, RESIZE }

private class Drag(val id: String, val kind: DragKind, val start: TileSpec) {
    var offset by mutableStateOf(Offset.Zero)
}

private val snap = spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
private val snapSize = spring<IntSize>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

/**
 * The tile grid. Normal mode: tiles are live; a long press on any tile enters edit mode and
 * picks that tile up in the same gesture. Edit mode: drag to move (dropping on a tile of the
 * same size swaps them), drag the corner grip to resize, × removes, tap opens the tile's settings.
 */
@Composable
fun BoardView(
    layout: GridLayout,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onChange: (GridLayout) -> Unit,
    onTileSettings: (TileSpec) -> Unit,
    onTileConfig: (TileSpec, kotlinx.serialization.json.JsonObject) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Which tiles show their real content. The others show their name and open their settings
     * on a tap: a phone editing a tablet can't show the tablet's alarm or weather, only its own.
     */
    live: (TileSpec) -> Boolean = { true },
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<Drag?>(null) }
    val currentLayout by rememberUpdatedState(layout)
    val isEditing by rememberUpdatedState(editing)

    BoxWithConstraints(modifier) {
        val cols = layout.cols
        val rows = layout.rows
        val gap = with(density) { Dash.gap.toPx() }
        val cellW = (constraints.maxWidth - gap * (cols - 1)) / cols
        val cellH = (constraints.maxHeight - gap * (rows - 1)) / rows
        val stepX = cellW + gap
        val stepY = cellH + gap

        fun origin(x: Int, y: Int) = IntOffset((x * stepX).roundToInt(), (y * stepY).roundToInt())
        fun size(w: Int, h: Int) = IntSize((w * cellW + (w - 1) * gap).roundToInt(), (h * cellH + (h - 1) * gap).roundToInt())

        /** Where the dragged tile would land, and whether that's allowed. */
        fun moveTarget(d: Drag): Pair<TileSpec, List<TileSpec>?> {
            val nx = (d.start.x + d.offset.x / stepX).roundToInt().coerceIn(0, cols - d.start.w)
            val ny = (d.start.y + d.offset.y / stepY).roundToInt().coerceIn(0, rows - d.start.h)
            return d.start.at(nx, ny) to Grid.movePush(currentLayout.tiles, d.start, nx, ny, cols, rows)
        }

        fun resizeTarget(d: Drag): TileSpec {
            val nw = (d.start.w + d.offset.x / stepX).roundToInt()
            val nh = (d.start.h + d.offset.y / stepY).roundToInt()
            return Grid.resize(currentLayout.tiles, d.start, nw, nh, Modules.minSize(d.start.type), cols, rows)
        }

        fun finish() {
            val d = drag ?: return
            when (d.kind) {
                DragKind.MOVE -> moveTarget(d).second?.let { onChange(currentLayout.copy(tiles = it)) }
                DragKind.RESIZE -> {
                    val r = resizeTarget(d)
                    if (r != d.start) onChange(currentLayout.copy(tiles = currentLayout.tiles.map { if (it.id == r.id) r else it }))
                }
            }
            drag = null
        }

        // Empty cells, shown while editing.
        val gridAlpha by animateFloatAsState(if (editing) 1f else 0f, label = "grid")
        if (gridAlpha > 0f) {
            val lineColor = Dash.line
            val radius = with(density) { Dash.radius.toPx() }
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = gridAlpha }) {
                for (cx in 0 until cols) for (cy in 0 until rows) {
                    drawRoundRect(
                        lineColor, Offset(cx * stepX, cy * stepY), GSize(cellW, cellH),
                        CornerRadius(radius, radius), style = Stroke(with(density) { 1.dp.toPx() }),
                    )
                }
            }
        }

        // While moving: where it would land, and where the tiles it pushes aside would go.
        val movePreview = drag?.takeIf { it.kind == DragKind.MOVE }?.let { moveTarget(it) }
        val pushed = movePreview?.second?.associateBy { it.id }
        movePreview?.let { (target, result) ->
            val o = origin(target.x, target.y)
            val s = size(target.w, target.h)
            Box(
                Modifier.offset { o }.size(with(density) { s.width.toDp() }, with(density) { s.height.toDp() })
                    .border(2.dp, if (result != null) Dash.accentText else Dash.danger, Dash.shape)
                    .background((if (result != null) Dash.accentText else Dash.danger).copy(alpha = 0.08f), Dash.shape)
            )
        }

        for (spec in layout.tiles) key(spec.id) {
            val module = Modules.get(spec.type)
            val d = drag?.takeIf { it.id == spec.id }
            val shown = when {
                d?.kind == DragKind.RESIZE -> resizeTarget(d)
                d == null && pushed != null -> pushed[spec.id] ?: spec // other tiles make room live
                else -> spec
            }
            val targetOrigin = origin(shown.x, shown.y)
            val targetSize = size(shown.w, shown.h)
            val pos = remember { Animatable(targetOrigin, IntOffset.VectorConverter) }
            val dim = remember { Animatable(targetSize, IntSize.VectorConverter) }
            val moving = d?.kind == DragKind.MOVE
            // While held, the tile sits under the finger; when let go it springs into its slot from there.
            LaunchedEffect(moving, targetOrigin) {
                if (moving && d != null) {
                    snapshotFlow { d.offset }.collect { off ->
                        pos.snapTo(IntOffset((d.start.x * stepX + off.x).roundToInt(), (d.start.y * stepY + off.y).roundToInt()))
                    }
                } else {
                    pos.animateTo(targetOrigin, snap)
                }
            }
            LaunchedEffect(targetSize) { dim.animateTo(targetSize, snapSize) }
            val lift by animateFloatAsState(if (d != null) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
            val style = runCatching { TileStyle.valueOf(spec.style) }.getOrDefault(TileStyle.PLAIN)
            val colors = tileColors(style)

            Box(
                Modifier
                    .offset { pos.value }
                    .size(with(density) { dim.value.width.toDp() }, with(density) { dim.value.height.toDp() })
                    .zIndex(if (d != null) 2f else 0f)
                    .graphicsLayer {
                        val s = 1f + 0.03f * lift
                        scaleX = s; scaleY = s
                    }
                    .shadow((10 * lift).dp, Dash.shape, clip = false)
                    .clip(Dash.shape)
                    .background(colors.fill)
                    // Long press anywhere on a live tile: enter edit mode and carry the tile.
                    .pointerInput(spec.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (isEditing) return@awaitEachGesture
                            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                    if (!c.pressed) return@withTimeoutOrNull true
                                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                                }
                                @Suppress("UNREACHABLE_CODE") true
                            }
                            if (released != null) return@awaitEachGesture
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartEditing()
                            val tile = currentLayout.tiles.firstOrNull { it.id == spec.id } ?: return@awaitEachGesture
                            val dr = Drag(spec.id, DragKind.MOVE, tile)
                            drag = dr
                            var lastCell = tile.x to tile.y
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                c.consume()
                                if (!c.pressed) break
                                dr.offset += c.position - c.previousPosition
                                val cell = moveTarget(dr).first.let { it.x to it.y }
                                if (cell != lastCell) { lastCell = cell; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                            finish()
                        }
                    }
            ) {
                CompositionLocalProvider(
                    LocalTileColors provides colors,
                    LocalTileSize provides with(density) { androidx.compose.ui.unit.DpSize(targetSize.width.toDp(), targetSize.height.toDp()) },
                    LocalShowLabel provides spec.config.let { (it["showLabel"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "false" },
                ) {
                    if (module != null && !live(spec)) {
                        Box(Modifier.fillMaxSize().pointerInput(spec.id) { detectTapGestures { onTileSettings(spec) } }, contentAlignment = Alignment.Center) {
                            // In edit mode the edit chrome names the tile instead.
                            if (!editing) androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(module.icon, colors.muted, 22.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                                Txt(module.label(remember(spec) { TileScope(spec) {} }), Type.meta, colors.muted)
                            }
                        }
                    } else if (module != null) {
                        val scope = remember(spec) { TileScope(spec) { cfg -> onTileConfig(spec, cfg) } }
                        module.Content(scope)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Txt("Unknown tile", Type.meta, colors.muted)
                        }
                    }
                }

                // Edit chrome: outline, grab surface, remove and resize.
                AnimatedVisibility(editing, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Dash.frame.copy(alpha = 0.35f))
                            .border(1.5.dp, Dash.accentText.copy(alpha = 0.8f), Dash.shape)
                            .pointerInput(spec.id) {
                                detectTapGestures { onTileSettings(spec) }
                            }
                            .pointerInput(spec.id, "move") {
                                detectDragGestures(
                                    onDragStart = {
                                        val tile = currentLayout.tiles.firstOrNull { it.id == spec.id } ?: return@detectDragGestures
                                        drag = Drag(spec.id, DragKind.MOVE, tile)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = { finish() },
                                    onDragCancel = { drag = null },
                                ) { c, delta ->
                                    c.consume()
                                    drag?.let { it.offset += delta }
                                }
                            }
                    ) {
                        // Name in the middle, when the tile is big enough not to crowd the handles.
                        if (dim.value.width > with(density) { 150.dp.roundToPx() } && dim.value.height > with(density) { 90.dp.roundToPx() }) {
                            Box(
                                Modifier.align(Alignment.Center).clip(RoundedCornerShape(50))
                                    .background(Dash.frame.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Txt((module?.name ?: spec.type).uppercase(), Type.small, Dash.ink)
                            }
                        }
                        EditKnob(
                            DashIcons.close, Modifier.align(Alignment.TopEnd),
                            onTap = { onChange(currentLayout.copy(tiles = currentLayout.tiles.filter { it.id != spec.id })) },
                        )
                        EditKnob(
                            DashIcons.resize, Modifier.align(Alignment.BottomEnd),
                            onDrag = { phase, delta ->
                                when (phase) {
                                    0 -> {
                                        val tile = currentLayout.tiles.firstOrNull { it.id == spec.id }
                                        if (tile != null) drag = Drag(spec.id, DragKind.RESIZE, tile)
                                    }
                                    1 -> drag?.let { it.offset += delta }
                                    else -> finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Round handle on a tile's corner in edit mode. Tap or drag (phase 0 start, 1 move, 2 end). */
@Composable
private fun EditKnob(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onTap: (() -> Unit)? = null,
    onDrag: ((Int, Offset) -> Unit)? = null,
) {
    Box(
        modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(Dash.ink)
            .let { m ->
                if (onTap != null) m.pointerInput(Unit) { detectTapGestures { onTap() } } else m
            }
            .let { m ->
                if (onDrag != null) m.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDrag(0, Offset.Zero) },
                        onDragEnd = { onDrag(2, Offset.Zero) },
                        onDragCancel = { onDrag(2, Offset.Zero) },
                    ) { c, delta -> c.consume(); onDrag(1, delta) }
                } else m
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, Dash.frame, 12.dp) }
}
