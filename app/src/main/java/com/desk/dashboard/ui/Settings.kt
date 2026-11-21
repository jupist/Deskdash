package com.desk.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.Category
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.UpcomingDate
import com.desk.dashboard.system.rememberToday
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ddMon = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
private val fullDate = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SettingsScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    Box(Modifier.fillMaxSize().background(Dash.frame)) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(Dash.gap),
            verticalArrangement = Arrangement.spacedBy(Dash.gap),
        ) {
            Row(
                Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("Settings", Type.secondary, Dash.ink, Modifier.weight(1f))
                OutlineButton("Done", onClick = onDone)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Dash.gap)) {
                Column(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    DatesSection(showOverlay = { overlay = it })
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dash.gap),
                ) {
                    Placeholder("Alarm", 6)
                    Placeholder("Calendar", 4)
                    Placeholder("Weather", 3)
                    Placeholder("Night mode", 8)
                    Placeholder("Display", 8)
                    Placeholder("Permissions", 8)
                }
            }
        }
        overlay?.invoke()
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Dash.tile).padding(horizontal = Dash.tilePadH, vertical = Dash.tilePadV)) {
        Txt(title.uppercase(), Type.label, Dash.ink)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Placeholder(title: String, milestone: Int) = Section(title) {
    Txt("Arrives in milestone $milestone", Type.bodySmall, Dash.muted)
}

/** Form state for adding or editing one entry. */
private class DateForm(today: LocalDate) {
    var editingId by mutableStateOf<Long?>(null)
    var title by mutableStateOf("")
    var date by mutableStateOf<LocalDate?>(null)
    var time by mutableStateOf<LocalTime?>(null)
    var category by mutableStateOf(Category.OTHER)
    var note by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var minDate = today

    fun load(d: UpcomingDate) {
        editingId = d.id; title = d.title; date = d.date; time = d.time
        category = d.category; note = d.note.orEmpty(); error = null
    }

    fun reset() {
        editingId = null; title = ""; date = null; time = null
        category = Category.OTHER; note = ""; error = null
    }

    /** Returns the entry to save, or null and sets [error]. */
    fun validate(): UpcomingDate? {
        val t = title.trim()
        val d = date
        error = when {
            t.isEmpty() -> "Title is required"
            d == null -> "Pick a date"
            d < minDate -> "Date must be today or later"
            else -> null
        }
        if (error != null || d == null) return null
        return UpcomingDate(
            id = editingId ?: 0, title = t, date = d, time = time, category = category,
            note = note.trim().ifEmpty { null },
        )
    }
}

@Composable
private fun DatesSection(showOverlay: ((@Composable () -> Unit)?) -> Unit) {
    val dao = DashDb.get(LocalContext.current).dates()
    val today by rememberToday()
    val entries by remember(today) { dao.upcoming(today) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val form = remember { DateForm(today) }
    form.minDate = today
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    Section("Upcoming dates") {
        if (entries.isEmpty()) {
            Txt("No dates yet. Add one below.", Type.bodySmall, Dash.muted)
            Spacer(Modifier.height(18.dp))
        } else {
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                entries.forEach { e ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Txt(e.date.format(ddMon).uppercase(), Type.display16.copy(fontSize = 15.sp), Dash.ink, Modifier.width(64.dp))
                        Txt(e.title, Type.body, if (e.id == form.editingId) Dash.slateLight else Dash.ink, Modifier.weight(1f))
                        if (confirmDelete == e.id) {
                            Txt("Delete?", Type.meta, Dash.ink)
                            MiniButton("Yes", Dash.ink, Dash.slate) {
                                scope.launch { dao.delete(e) }
                                if (form.editingId == e.id) form.reset()
                                confirmDelete = null
                            }
                            MiniButton("No") { confirmDelete = null }
                        } else {
                            MiniButton("Edit") { form.load(e); confirmDelete = null }
                            MiniButton("Delete") { confirmDelete = e.id }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))
            }
        }

        Txt(if (form.editingId == null) "ADD A DATE" else "EDIT DATE", Type.small, Dash.muted)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Title") {
                TextInput(form.title, { form.title = it; form.error = null }, "What’s happening", UpcomingDate.TITLE_MAX)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Date", Modifier.weight(1f)) {
                    PickerField(form.date?.format(fullDate), "Pick a date") {
                        focus.clearFocus()
                        showOverlay {
                            DatePickerOverlay(form.date, today, onPick = { form.date = it; form.error = null; showOverlay(null) }, onDismiss = { showOverlay(null) })
                        }
                    }
                }
                Field("Time (optional)", Modifier.weight(1f)) {
                    PickerField(form.time?.format(hhmm), "No time", onClear = { form.time = null }) {
                        focus.clearFocus()
                        showOverlay {
                            TimePickerOverlay(
                                form.time,
                                onPick = { form.time = it; showOverlay(null) },
                                onClear = { form.time = null; showOverlay(null) },
                                onDismiss = { showOverlay(null) },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Category", Modifier.weight(1f)) {
                    Dropdown(form.category, Category.entries, { it.label }, { form.category = it })
                }
                Field("Note (optional)", Modifier.weight(1f)) {
                    TextInput(form.note, { form.note = it }, "Room, reminder, details", UpcomingDate.NOTE_MAX)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (form.editingId == null) "Add date" else "Save changes") {
                    val entry = form.validate() ?: return@PrimaryButton
                    focus.clearFocus()
                    scope.launch { dao.upsert(entry) }
                    form.reset()
                }
                if (form.editingId != null) MiniButton("Cancel") { form.reset() }
                form.error?.let { Txt(it, Type.meta, Dash.slateLight) }
            }
        }
    }
}
