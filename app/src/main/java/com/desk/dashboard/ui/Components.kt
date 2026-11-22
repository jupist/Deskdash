package com.desk.dashboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** False when the user hid tile labels for a tile. */
val LocalShowLabel = compositionLocalOf { true }

/** Size of the tile being drawn, so small tiles can use tighter padding. */
val LocalTileSize = compositionLocalOf { androidx.compose.ui.unit.DpSize.Unspecified }

/**
 * Tile body: standard padding and an uppercase label row. The fill and shape come from the
 * board (per tile style); pass [fill] only for a tile drawn outside the board.
 */
@Composable
fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    fill: Color? = null,
    ink: Color = tc.ink,
    metaColor: Color = tc.muted,
    content: @Composable ColumnScope.() -> Unit,
) {
    val size = LocalTileSize.current
    val padH = if (size.isSpecified && size.width < 150.dp) 12.dp else Dash.tilePadH
    val padV = if (size.isSpecified && size.height < 130.dp) 10.dp else Dash.tilePadV
    Column(
        modifier
            .let { if (fill != null) it.background(fill, Dash.shape) else it }
            .padding(horizontal = padH, vertical = padV)
    ) {
        if (LocalShowLabel.current) TileHeader(label, meta, ink, metaColor)
        content()
    }
}

/** Uppercase label at left, optional muted meta at right. */
@Composable
fun TileHeader(label: String, meta: String?, ink: Color = tc.ink, metaColor: Color = tc.muted) {
    // The label keeps its full width; the meta text gives way (ellipsis) on narrow tiles.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(label.uppercase(), Type.label, ink)
        if (meta != null) {
            Spacer(Modifier.width(10.dp))
            Txt(meta, Type.label.copy(letterSpacing = Type.meta.letterSpacing, textAlign = TextAlign.End), metaColor, Modifier.weight(1f))
        }
    }
}

/** Single-line-by-default text with ellipsis, so nothing ever overflows a tile. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
    )
}

/**
 * One line of text sized to fill the space it's given (up to [maxSize]), for clocks and big
 * numbers in tiles of any size. [sample] sets the width budget, so "12:34" and "08:00" render at
 * the same size and the clock doesn't jump as digits change.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    sample: String = text,
    maxSize: Float = 400f,
    minSize: Float = 10f,
    alignment: Alignment = Alignment.BottomStart,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val base = 100f
        val size = remember(sample, style, constraints.maxWidth, constraints.maxHeight) {
            val m = measurer.measure(
                sample, style.copy(fontSize = base.sp, lineHeight = base.sp), maxLines = 1, softWrap = false,
                constraints = Constraints(),
            )
            val wScale = if (constraints.hasBoundedWidth) constraints.maxWidth / m.size.width.toFloat() else Float.MAX_VALUE
            val hScale = if (constraints.hasBoundedHeight) constraints.maxHeight / m.size.height.toFloat() else Float.MAX_VALUE
            // Text size is linear in sp, so scale the probe size by the tighter of the two fits.
            (base * minOf(wScale, hScale) * 0.97f).coerceIn(minSize, maxSize)
        }
        Txt(text, style.copy(fontSize = size.sp, lineHeight = (size * 0.98f).sp), color)
    }
}

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Shrinks slightly while pressed: the "snap" feedback on anything tappable. */
fun Modifier.pressable(onClick: () -> Unit, scaleTo: Float = 0.96f, enabled: Boolean = true): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleTo else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Outlined, uppercase top-bar button (Settings, Lock, Done). */
@Composable
fun OutlineButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .border(1.dp, Dash.line, Dash.smallShape)
            .clip(Dash.smallShape)
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, Dash.ink, 14.dp)
        Txt(text.uppercase(), Type.label.copy(letterSpacing = Type.small.letterSpacing), Dash.ink)
    }
}

/** Square icon-only button for bars. */
@Composable
fun IconButton(icon: ImageVector, tint: Color = Dash.ink, size: Dp = 36.dp, iconSize: Dp = 16.dp, outline: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size)
            .let { if (outline) it.border(1.dp, Dash.line, Dash.smallShape) else it }
            .clip(Dash.smallShape)
            .pressable(onClick, 0.9f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** Round button used for media controls and mini tiles. */
@Composable
fun CircleButton(
    icon: ImageVector,
    fill: Color,
    tint: Color,
    size: Dp = 34.dp,
    iconSize: Dp = 14.dp,
    outline: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .let { if (outline != null) it.border(1.dp, outline, CircleShape) else it }
            .pressable(onClick, 0.88f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** 52 × 30 dp toggle with an animated knob. */
@Composable
fun Switch(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, on: Color = Dash.accent) {
    val x by animateDpAsState(if (checked) 25.dp else 3.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "knob")
    val track by animateColorAsState(if (checked) on else Dash.line, label = "track")
    Box(
        modifier
            .size(52.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Dash.onAccent else Dash.muted)
        )
    }
}

/** The 3 × 3 square dot glyph at the left of the top bar. */
@Composable
fun DotGrid(color: Color = Dash.ink, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { Box(Modifier.size(4.dp).background(color)) }
            }
        }
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Thin rule in the tile's line colour. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = tc.line) =
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
