package com.desk.dashboard.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.desk.dashboard.data.Backup
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.DatesDao
import com.desk.dashboard.data.UpcomingDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/**
 * The context the app's screens get while the phone is a remote control. Settings read and saved
 * through it ([com.desk.dashboard.data.prefs], the alarm's store, important dates) are the
 * tablet's; everything else (the phone's own window, permissions, files) is still the phone's.
 */
class RemoteContext(base: Context, val session: RemoteSession) : ContextWrapper(base)

val Context.isRemote get() = this is RemoteContext

/** Important dates: the tablet's while remote, else this device's. */
fun Context.datesDao(): DatesDao = (this as? RemoteContext)?.session?.dates ?: DashDb.get(this).dates()

/** The activity behind a (possibly wrapped) context. */
fun Context.findActivity(): Activity {
    var c: Context = this
    while (c !is Activity) c = (c as ContextWrapper).baseContext
    return c
}

/**
 * One of the tablet's settings stores, mirrored on the phone. Reads come from the last copy the
 * tablet sent; a change shows here at once and goes to the tablet as just the keys that changed.
 */
class RemoteStore(private val name: String, private val send: suspend (String, JsonObject, List<String>) -> Unit) : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences?>(null)
    private val lock = Mutex()
    override val data: Flow<Preferences> = state.filterNotNull()

    /** Replaces the mirror with the tablet's copy (values in the backup format). */
    fun load(values: JsonObject) {
        state.value = mutablePreferencesOf().also { Backup.restoreInto(it, values) }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = lock.withLock {
        val old = data.first()
        val new = transform(old)
        if (new != old) {
            state.value = new
            val before = old.asMap()
            val after = new.asMap()
            val changed = after.filter { (k, v) -> before[k] != v }
            val removed = before.keys.filter { it !in after }.map { it.name }
            send(name, Backup.encode(changed), removed)
        }
        new
    }
}

/** The tablet's important dates, through the session. */
class RemoteDatesDao(private val session: RemoteSession) : DatesDao {
    override fun upcoming(today: LocalDate): Flow<List<UpcomingDate>> = session.datesFlow.map { list ->
        list.filter { !it.date.isBefore(today) }
    }

    override suspend fun upsert(d: UpcomingDate) = session.saveDateNow(d)
    override suspend fun delete(d: UpcomingDate) = session.deleteDateNow(d.id)
    override suspend fun purgeBefore(today: LocalDate) {} // the tablet tidies its own
}
