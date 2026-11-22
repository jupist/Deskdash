package com.desk.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
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
import com.desk.dashboard.data.CalendarSource
import com.desk.dashboard.alarm.AlarmService
import com.desk.dashboard.alarm.Alarms
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.DayOfWeek
import com.desk.dashboard.data.DeviceCalendar
import com.desk.dashboard.data.Keys
import com.desk.dashboard.system.rememberCalendarPermission
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.desk.dashboard.data.Place
import com.desk.dashboard.data.UpcomingDate
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.data.prefs
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
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
                    // Weather first: its search results then stay above the on-screen keyboard.
                    WeatherSection()
                    AlarmSection(showOverlay = { overlay = it })
                    CalendarSection()
                    NightSection(showOverlay = { overlay = it })
                    DisplaySection()
                    PermissionsSection()
                }
            }
        }
        overlay?.invoke()
    }
}

@Composable
internal fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Dash.tile).padding(horizontal = Dash.tilePadH, vertical = Dash.tilePadV)) {
        Txt(title.uppercase(), Type.label, Dash.ink)
        Spacer(Modifier.height(14.dp))
        content()
    }
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
            // No category picker: the tile shows no tags. Entries keep the stored category (default Other).
            Field("Note (optional)") {
                TextInput(form.note, { form.note = it }, "Room, reminder, details", UpcomingDate.NOTE_MAX)
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

@Composable
private fun WeatherSection() {
    val context = LocalContext.current
    val repo = remember { WeatherRepo.get(context) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val city by remember { context.prefs.data.map { it[Keys.weatherCity] } }.collectAsStateWithLifecycle(null)
    val unit by remember { context.prefs.data.map { it[Keys.tempUnit] ?: "celsius" } }.collectAsStateWithLifecycle("celsius")

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    // Debounced search as the user types.
    LaunchedEffect(query) {
        results = emptyList()
        val q = query.trim()
        if (q.length < 2) { status = null; return@LaunchedEffect }
        delay(400)
        status = "Searching…"
        status = runCatching { repo.searchCity(q) }.fold(
            onSuccess = { results = it; if (it.isEmpty()) "No matches" else null },
            onFailure = { "Search failed — check the connection" },
        )
    }

    Section("Weather") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("City", Type.body, Dash.muted, Modifier.width(64.dp))
            Txt(city ?: "Not set", Type.body, if (city == null) Dash.muted else Dash.ink, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        TextInput(query, { query = it }, "Search a city", 60)
        status?.let {
            Spacer(Modifier.height(8.dp))
            Txt(it, Type.meta, Dash.muted)
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            results.forEach { place ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.line))
                Row(
                    Modifier.fillMaxWidth().clickable {
                        focus.clearFocus()
                        query = ""
                        scope.launch { repo.setCity(place) }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(place.name, Type.body, Dash.ink)
                    Spacer(Modifier.width(10.dp))
                    Txt(place.detail, Type.meta, Dash.muted, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("Units", Type.body, Dash.muted, Modifier.width(64.dp))
            listOf("celsius" to "°C", "fahrenheit" to "°F").forEach { (value, label) ->
                val on = unit == value
                MiniButton(label, if (on) Dash.onSlate else Dash.muted, if (on) Dash.slate else Dash.line) {
                    if (!on) scope.launch { repo.setUnit(value) }
                }
            }
        }
    }
}

@Composable
private fun CalendarSection() {
    val context = LocalContext.current
    val permission = rememberCalendarPermission()
    val scope = rememberCoroutineScope()

    Section("Calendar") {
        if (!permission.granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Calendar access is off", Type.body, Dash.muted, Modifier.weight(1f))
                MiniButton("Grant", Dash.ink, Dash.slate, permission.request)
            }
            return@Section
        }
        var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
        LaunchedEffect(Unit) { calendars = CalendarSource.calendars(context) }
        val stored by remember { CalendarSource.selectedIds(context) }.collectAsStateWithLifecycle(null)
        val selected = stored ?: CalendarSource.defaultSelection(calendars)

        if (calendars.isEmpty()) {
            Txt("No calendars on this tablet yet. Add a Google account in Android settings.", Type.bodySmall, Dash.muted, maxLines = 2)
            return@Section
        }
        // Group by the label shown, so e.g. all on-device calendars share one heading.
        calendars.groupBy { accountLabel(it.account, it.accountType) }.forEach { (label, cals) ->
            Txt(label, Type.small.copy(letterSpacing = Type.label.letterSpacing), Dash.muted)
            Spacer(Modifier.height(4.dp))
            cals.forEach { cal ->
                val on = cal.id in selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { CalendarSource.setSelected(context, if (on) selected - cal.id else selected + cal.id) }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(cal.color or (0xFF shl 24))))
                    Spacer(Modifier.width(8.dp))
                    Txt(cal.name, Type.body, if (cal.visible) Dash.ink else Dash.muted, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun accountLabel(account: String, type: String) = when (type) {
    "com.google" -> account
    "LOCAL" -> "On this tablet"
    "com.osp.app.signin" -> "Samsung account"
    else -> account.ifBlank { type }
}.uppercase()

@Composable
private fun Checkbox(checked: Boolean) {
    Box(
        Modifier.size(18.dp)
            .background(if (checked) Dash.slate else Color.Transparent)
            .border(1.dp, if (checked) Dash.slate else Dash.muted),
        contentAlignment = Alignment.Center,
    ) { if (checked) Txt("✓", Type.meta.copy(fontSize = 12.sp, lineHeight = 12.sp), Dash.onSlate) }
}

@Composable
private fun AlarmSection(showOverlay: ((@Composable () -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by remember { Alarms.config(context) }.collectAsStateWithLifecycle(null)
    val c = cfg ?: return

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // "Default" (or nothing) means the system default alarm sound.
        val value = uri?.takeIf { it != RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }?.toString()
        scope.launch { Alarms.setSound(context, value) }
    }
    val soundName = remember(c.soundUri) {
        val uri = c.soundUri?.let(Uri::parse)
        if (uri == null) "Default alarm"
        else runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "Custom sound"
    }

    Section("Alarm") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Time", Modifier.weight(1f)) {
                PickerField("%02d:%02d".format(c.time.hour, c.time.minute), "") {
                    showOverlay {
                        TimePickerOverlay(
                            c.time,
                            onPick = { t -> scope.launch { Alarms.setTime(context, t) }; showOverlay(null) },
                            onClear = null,
                            onDismiss = { showOverlay(null) },
                        )
                    }
                }
            }
            Field("Sound", Modifier.weight(1.4f)) {
                PickerField(soundName, "") {
                    soundPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                c.soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("One-time", Type.bodySmall, Dash.ink)
                Spacer(Modifier.height(2.dp))
                Txt("Rings once at the next ${"%02d:%02d".format(c.time.hour, c.time.minute)}, then turns off", Type.meta, Dash.muted)
            }
            Switch(c.once, onToggle = { scope.launch { Alarms.setOnce(context, !c.once) } })
        }
        Spacer(Modifier.height(14.dp))
        Field("Repeat") {
            Row(
                // Days are kept but don't apply while One-time is on.
                Modifier.alpha(if (c.once) 0.35f else 1f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { d ->
                    val on = d in c.days
                    Box(
                        Modifier.size(30.dp)
                            .background(if (on) Dash.slate else Color.Transparent)
                            .border(1.dp, if (on) Dash.slate else Dash.line)
                            .clickable(enabled = !c.once) { scope.launch { Alarms.setDays(context, if (on) c.days - d else c.days + d) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(d.name.take(1), Type.meta.copy(fontSize = 11.sp), if (on) Dash.onSlate else Dash.muted)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Txt(if (c.days.isEmpty() && !c.once) "Rings once" else "", Type.meta, Dash.muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Field("Snooze") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Alarms.SNOOZE_CHOICES.forEach { m ->
                    val on = m == c.snoozeMinutes
                    MiniButton("$m min", if (on) Dash.onSlate else Dash.muted, if (on) Dash.slate else Dash.line) {
                        scope.launch { Alarms.setSnoozeMinutes(context, m) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Turn the alarm on from the dashboard tile.", Type.meta, Dash.muted, Modifier.weight(1f))
            MiniButton("Test ring", Dash.ink) { AlarmService.ring(context) }
        }
    }
}
