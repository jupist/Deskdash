package com.desk.dashboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A grid placement, 1-based like SPEC.md's tile table (columns 1–12, rows 1–3, inclusive). */
data class Cell(val colStart: Int, val colEnd: Int, val rowStart: Int, val rowEnd: Int = rowStart)

/**
 * 12 × 3 grid of equal columns and rows separated by [Dash.gap]. Each child must carry
 * `Modifier.layoutId(Cell(...))`. Children get exact constraints, so tiles never resize the grid.
 */
@Composable
fun DashGrid(modifier: Modifier = Modifier, columns: Int = 12, rows: Int = 3, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = Dash.gap.toPx()
        val colStep = (constraints.maxWidth - gap * (columns - 1)) / columns + gap
        val rowStep = (constraints.maxHeight - gap * (rows - 1)) / rows + gap
        val placed = measurables.map { m ->
            val c = m.layoutId as Cell
            val x0 = ((c.colStart - 1) * colStep).roundToInt()
            val x1 = (c.colEnd * colStep - gap).roundToInt()
            val y0 = ((c.rowStart - 1) * rowStep).roundToInt()
            val y1 = (c.rowEnd * rowStep - gap).roundToInt()
            Triple(m.measure(Constraints.fixed(x1 - x0, y1 - y0)), x0, y0)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

/** Flat square tile with the standard 18 × 20 dp padding and an uppercase label row. */
@Composable
fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    fill: Color = Dash.tile,
    ink: Color = Dash.ink,
    metaColor: Color = Dash.muted,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(fill)
            .padding(horizontal = Dash.tilePadH, vertical = Dash.tilePadV)
    ) {
        TileHeader(label, meta, ink, metaColor)
        content()
    }
}

/** Uppercase label at left, optional muted meta at right. */
@Composable
fun TileHeader(label: String, meta: String?, ink: Color = Dash.ink, metaColor: Color = Dash.muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(label.uppercase(), Type.label, ink, Modifier.weight(1f))
        if (meta != null) Txt(meta, Type.label.copy(letterSpacing = Type.meta.letterSpacing), metaColor)
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

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Outlined, uppercase top-bar button (Settings, Lock, Done). */
@Composable
fun OutlineButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .border(1.dp, Dash.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, Dash.ink, 14.dp)
        Txt(text.uppercase(), Type.label.copy(letterSpacing = Type.small.letterSpacing), Dash.ink)
    }
}

/** Round button used for the ambient/reading actions (34 dp) and media controls (40/52 dp). */
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** 64 × 34 dp toggle, as in the mockup. */
@Composable
fun Switch(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp, 34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (checked) Dash.slate else Dash.line)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
    ) {
        Box(
            Modifier
                .offset(x = if (checked) 34.dp else 4.dp, y = 4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) Dash.onSlate else Dash.muted)
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
