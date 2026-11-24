package com.desk.dashboard.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remote control mode: while on, this phone's app shows and changes a tablet instead of itself.
 * It stays on (across restarts) until turned off, so a phone kept as the tablet's remote opens
 * straight into it.
 */
object RemoteMode {
    /** The tablet being controlled: its announced id, and its name for while it's being found. */
    data class Target(val id: String?, val name: String)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target

    /** Where the tablet was last seen, to connect at once rather than search again. */
    @Volatile var lastSeen: FoundTablet? = null

    private fun sp(c: Context) = c.getSharedPreferences("remote_client", Context.MODE_PRIVATE)

    fun init(c: Context) {
        _target.value = sp(c).getString("controlling", null)?.split('\t')?.takeIf { it.size == 2 }
            ?.let { Target(it[0].ifEmpty { null }, it[1]) }
    }

    fun start(c: Context, t: FoundTablet) {
        lastSeen = t
        sp(c).edit().putString("controlling", "${t.id.orEmpty()}\t${t.name}").apply()
        _target.value = Target(t.id, t.name)
    }

    fun stop(c: Context) {
        sp(c).edit().remove("controlling").apply()
        _target.value = null
    }

    fun matches(target: Target, t: FoundTablet) = if (target.id != null) t.id == target.id else t.name == target.name
}
