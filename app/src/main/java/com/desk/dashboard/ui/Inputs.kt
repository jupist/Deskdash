package com.desk.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Form controls in the dashboard's flat style. No Material components.

private val fieldPad = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)

/** Uppercase 9 sp caption above a control. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Txt(label.uppercase(), Type.small.copy(letterSpacing = Type.label.letterSpacing), Dash.muted)
        content()
    }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(maxLength)) },
        singleLine = true,
        textStyle = Type.body.copy(color = Dash.ink),
        cursorBrush = SolidColor(Dash.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(Modifier.border(1.dp, Dash.line, Dash.smallShape).then(fieldPad), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Txt(placeholder, Type.body, Dash.muted)
                    inner()
                }
                // Character counter appears once the user is close to the limit.
                if (value.length > maxLength - 10) Txt("${value.length}/$maxLength", Type.small, Dash.muted)
            }
        },
    )
}

/** A tappable box that looks like a text field; used for date, time and dropdown fields. */
@Composable
fun PickerField(text: String?, placeholder: String, modifier: Modifier = Modifier, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(Dash.smallShape).border(1.dp, Dash.line, Dash.smallShape).clickable(onClick = onClick).then(fieldPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text ?: placeholder, Type.body, if (text == null) Dash.muted else Dash.ink, Modifier.weight(1f))
        if (text != null && onClear != null) {
            Txt("×", Type.body, Dash.muted, Modifier.clickable(onClick = onClear).padding(horizontal = 4.dp))
        }
    }
}

@Composable
fun <T> Dropdown(selected: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PickerField(label(selected) + "  ▾", "") { open = true }
        if (open) {
            Popup(onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(Modifier.width(200.dp).clip(Dash.smallShape).background(Dash.tile).border(1.dp, Dash.line, Dash.smallShape)) {
                    options.forEach { o ->
                        Txt(
                            label(o), Type.body, if (o == selected) Dash.accentText else Dash.ink,
                            Modifier.fillMaxWidth().clickable { onSelect(o); open = false }.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniButton(text: String, color: Color = Dash.muted, border: Color = Dash.line, onClick: () -> Unit) {
    Txt(
        text.uppercase(), Type.meta.copy(letterSpacing = Type.small.letterSpacing), color,
        Modifier.clip(Dash.smallShape).border(1.dp, border, Dash.smallShape).pressable(onClick, 0.94f).padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Txt(
        text.uppercase(), Type.label, Dash.onAccent,
        modifier.clip(Dash.smallShape).background(Dash.accent).pressable(onClick, 0.95f).padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Full-screen scrim with a centered panel. Tapping outside the panel dismisses. */
@Composable
fun Overlay(onDismiss: () -> Unit, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    // Pops in: quick fade and a slight scale, so dialogs feel attached to the tap that opened them.
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier.fillMaxSize().background(Dash.scrim).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ).imePadding().padding(16.dp), // stays above the keyboard
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            shown,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(160), initialScale = 0.96f),
        ) {
            Column(
                Modifier.widthIn(max = width).fillMaxWidth().clip(Dash.shape).background(Dash.tile).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
                ).padding(horizontal = Dash.tilePadH, vertical = Dash.tilePadV),
                content = content,
            )
        }
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** Month grid date picker; days before [min] are disabled. */
@Composable
fun DatePickerOverlay(initial: LocalDate?, min: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(initial ?: min)) }
    Overlay(onDismiss, 360.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(month.format(monthTitle).uppercase(), Type.label, Dash.ink, Modifier.weight(1f))
            val canGoBack = month > YearMonth.from(min)
            MiniButton("‹", if (canGoBack) Dash.ink else Dash.line) { if (canGoBack) month = month.minusMonths(1) }
            Spacer(Modifier.width(8.dp))
            MiniButton("›", Dash.ink) { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Txt(it, Type.small.copy(textAlign = TextAlign.Center), Dash.muted, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = lead + month.lengthOfMonth()
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val day = week * 7 + dow - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.2f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            DayCell(date, enabled = date >= min, selected = date == initial, today = date == min) { onPick(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DayCell(date: LocalDate, enabled: Boolean, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Dash.accent else Color.Transparent
    val fg = when {
        selected -> Dash.onAccent
        !enabled -> Dash.line
        today -> Dash.accentText
        else -> Dash.ink
    }
    Box(
        Modifier.matchParentSize().padding(2.dp).background(bg)
            .let { if (today && !selected) it.border(1.dp, Dash.line) else it }
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) { Txt(date.dayOfMonth.toString(), Type.display16, fg) }
}

/**
 * Hour grid + 5-minute grid, with ±1 minute nudges for odd times like 23:59.
 * [onClear] adds a "No time" button for optional fields.
 */
@Composable
fun TimePickerOverlay(initial: LocalTime?, onPick: (LocalTime) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial ?: LocalTime.of(9, 0)) }
    Overlay(onDismiss, 420.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("%02d:%02d".format(t.hour, t.minute), Type.big, Dash.ink, Modifier.weight(1f))
            MiniButton("−1", Dash.ink) { t = t.minusMinutes(1) }
            Spacer(Modifier.width(8.dp))
            MiniButton("+1", Dash.ink) { t = t.plusMinutes(1) }
        }
        Spacer(Modifier.height(14.dp))
        Txt("HOUR", Type.small, Dash.muted)
        Spacer(Modifier.height(6.dp))
        ChipGrid((0..23).toList(), columns = 8, selected = { it == t.hour }, label = { "%02d".format(it) }) {
            t = t.withHour(it)
        }
        Spacer(Modifier.height(12.dp))
        Txt("MINUTE", Type.small, Dash.muted)
        Spacer(Modifier.height(6.dp))
        ChipGrid((0..55 step 5).toList(), columns = 6, selected = { it == t.minute }, label = { ":%02d".format(it) }) {
            t = t.withMinute(it)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Set time") { onPick(t) }
            Spacer(Modifier.weight(1f))
            if (onClear != null) MiniButton("No time", onClick = onClear)
        }
    }
}

@Composable
private fun <T> ChipGrid(items: List<T>, columns: Int, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    val on = selected(item)
                    Box(
                        Modifier.weight(1f).height(32.dp)
                            .background(if (on) Dash.accent else Color.Transparent)
                            .border(1.dp, if (on) Dash.accent else Dash.line)
                            .clickable { onClick(item) },
                        contentAlignment = Alignment.Center,
                    ) { Txt(label(item), Type.body, if (on) Dash.onAccent else Dash.ink) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
