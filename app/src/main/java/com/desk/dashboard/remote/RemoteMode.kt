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

    private val _primary = MutableStateFlow(false)
    /** "Use this device primarily as a remote": the app opens on the tablets, not its own dashboard. */
    val primary: StateFlow<Boolean> = _primary

    fun init(c: Context) {
        _target.value = sp(c).getString("controlling", null)?.split('\t')?.takeIf { it.size == 2 }
            ?.let { Target(it[0].ifEmpty { null }, it[1]) }
        _primary.value = sp(c).getBoolean("primary", false)
        // As a dedicated remote: straight to the only tablet, or a choice when there are several.
        if (_primary.value) openDefault(c)
    }

    private fun openDefault(c: Context) {
        val paired = Pairings.all(c)
        _target.value = paired.singleOrNull()?.let { Target(it.id, it.name) }
    }

    fun setPrimary(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("primary", on).apply()
        _primary.value = on
        if (on) openDefault(c) else stop(c)
    }

    /** Back to the list of tablets (as a dedicated remote), or out of remote control. */
    fun leave(c: Context) {
        if (_primary.value) {
            sp(c).edit().remove("controlling").apply()
            _target.value = null
        } else stop(c)
    }

    /** Opens a paired tablet that hasn't been seen on the network yet this session. */
    fun open(c: Context, p: Pairings.Pairing) {
        lastSeen = null
        sp(c).edit().putString("controlling", "${p.id}\t${p.name}").apply()
        _target.value = Target(p.id, p.name)
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
