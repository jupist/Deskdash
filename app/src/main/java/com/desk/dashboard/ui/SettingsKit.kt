package com.desk.dashboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Building blocks for settings screens and tile settings sheets.

/** A titled card holding rows. */
@Composable
fun SettingsCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Txt(title.uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing), Dash.muted, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(Dash.shape).background(Dash.tile)
                .padding(horizontal = Dash.tilePadH, vertical = 6.dp),
            content = content,
        )
    }
}

/** Title (and optional hint) at left, a control at right. */
@Composable
fun SettingRow(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, Dash.muted, 18.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, Type.body, Dash.ink)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Txt(hint, Type.meta, Dash.muted, maxLines = 3)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
fun SwitchRow(title: String, hint: String? = null, checked: Boolean, onToggle: () -> Unit) =
    SettingRow(title, hint, onClick = onToggle) { Switch(checked, onToggle) }

/** A row that leads somewhere: shows its current value and a chevron. */
@Composable
fun NavRow(title: String, value: String? = null, hint: String? = null, icon: ImageVector? = null, onClick: () -> Unit) =
    SettingRow(title, hint, icon = icon, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) Txt(value, Type.bodySmall, Dash.muted, Modifier.wrapContentWidth())
            Spacer(Modifier.width(6.dp))
            Icon(DashIcons.chevronRight, Dash.muted, 14.dp)
        }
    }

/** Divider between rows of a card. */
@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))

/** Joined buttons, one selected. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.clip(Dash.smallShape).border(1.dp, Dash.line, Dash.smallShape)) {
        options.forEachIndexed { i, o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) Dash.accent else Color.Transparent, label = "seg")
            Box(
                Modifier.background(bg).clickable { onSelect(o) }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(label(o).uppercase(), Type.meta.copy(letterSpacing = Type.small.letterSpacing, textAlign = TextAlign.Center), if (on) Dash.onAccent else Dash.ink)
            }
            if (i < options.lastIndex) Box(Modifier.width(1.dp).height(30.dp).background(Dash.line))
        }
    }
}

/** Wrapping row of selectable chips. */
@Composable
fun <T> Chips(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            val on = selected(o)
            Txt(
                label(o), Type.meta, if (on) Dash.onAccent else Dash.ink,
                Modifier.clip(Dash.smallShape)
                    .background(if (on) Dash.accent else Color.Transparent)
                    .border(1.dp, if (on) Dash.accent else Dash.line, Dash.smallShape)
                    .clickable { onClick(o) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** Panel title row for overlays: title and a close button. */
@Composable
fun PanelHeader(title: String, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Type.secondary.copy(fontSize = Type.display20.fontSize, lineHeight = Type.display20.lineHeight), Dash.ink, Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(8.dp))
        IconButton(DashIcons.close, onClick = onClose)
    }
}
