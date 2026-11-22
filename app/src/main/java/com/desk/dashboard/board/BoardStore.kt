package com.desk.dashboard.board

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A named dashboard. It keeps one layout per grid size, so a phone and a tablet each get theirs. */
@Serializable
data class Preset(
    val id: String,
    val name: String,
    val layouts: Map<String, GridLayout> = emptyMap(),
    /** Grid size last edited: the source when this preset meets a new screen size. */
    val lastKey: String? = null,
)

@Serializable
data class Boards(val presets: List<Preset>, val activeId: String) {
    val active get() = presets.firstOrNull { it.id == activeId } ?: presets.first()
}

/**
 * Presets and layouts, saved as JSON in app storage. One preset is active: that's the dashboard.
 * Others are saved alternatives the user can switch to.
 */
object BoardStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _boards = MutableStateFlow<Boards?>(null)
    val boards: StateFlow<Boards?> = _boards
    private lateinit var file: File
    private lateinit var scope: CoroutineScope

    fun init(context: Context, scope: CoroutineScope) {
        this.scope = scope
        file = File(context.filesDir, "boards.json")
        _boards.value = runCatching { json.decodeFromString<Boards>(file.readText()) }.getOrNull()
            ?.takeIf { it.presets.isNotEmpty() }
            ?: Boards(listOf(Preset(newId(), "My dashboard")), "").let { it.copy(activeId = it.presets[0].id) }
    }

    /** A tile by id from any preset and size (for receivers that run without the UI). */
    fun findTile(context: Context, id: String): TileSpec? {
        if (_boards.value == null && context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            init(context, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        }
        return _boards.value?.presets?.asSequence()?.flatMap { it.layouts.values.asSequence() }
            ?.flatMap { it.tiles.asSequence() }?.firstOrNull { it.id == id }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    private fun save(b: Boards) {
        _boards.value = b
        val text = json.encodeToString(Boards.serializer(), b)
        scope.launch(Dispatchers.IO) {
            synchronized(this@BoardStore) {
                val tmp = File(file.parentFile, "boards.json.tmp")
                tmp.writeText(text)
                tmp.renameTo(file)
            }
        }
    }

    private fun updateActive(block: (Preset) -> Preset) {
        val b = _boards.value ?: return
        save(b.copy(presets = b.presets.map { if (it.id == b.activeId) block(it) else it }))
    }

    /**
     * The active preset's layout for a [cols]×[rows] grid: saved if there is one, else adapted
     * from the last edited size, else the default layout for that size.
     */
    fun layoutFor(preset: Preset, cols: Int, rows: Int): GridLayout {
        preset.layouts[GridLayout.key(cols, rows)]?.let { return it }
        // Prefer a saved layout of the same shape (landscape vs portrait), the last edited first.
        val landscape = cols >= rows
        val sameShape = preset.layouts.values.filter { (it.cols >= it.rows) == landscape }
        val source = preset.lastKey?.let { preset.layouts[it] }?.takeIf { (it.cols >= it.rows) == landscape }
            ?: sameShape.firstOrNull()
        if (source != null) return Grid.adapt(source, cols, rows, Modules::minSize).first
        val other = preset.lastKey?.let { preset.layouts[it] } ?: preset.layouts.values.firstOrNull()
            ?: return Defaults.layout(cols, rows)
        return Defaults.reshape(other, cols, rows)
    }

    fun saveLayout(layout: GridLayout) = updateActive {
        it.copy(layouts = it.layouts + (layout.key to layout), lastKey = layout.key)
    }

    /** Changes one tile in every saved size of the active preset (style and settings are shared). */
    fun updateTileEverywhere(id: String, block: (TileSpec) -> TileSpec) = updateActive { p ->
        p.copy(layouts = p.layouts.mapValues { (_, l) -> l.copy(tiles = l.tiles.map { if (it.id == id) block(it) else it }) })
    }

    fun setActive(id: String) {
        val b = _boards.value ?: return
        if (b.presets.any { it.id == id }) save(b.copy(activeId = id))
    }

    /** New preset: a copy of the current one, or the default layout. Becomes active. */
    fun create(name: String, copyCurrent: Boolean): String {
        val b = _boards.value ?: return ""
        val p = if (copyCurrent) b.active.copy(id = newId(), name = name) else Preset(newId(), name)
        save(Boards(b.presets + p, p.id))
        return p.id
    }

    fun rename(id: String, name: String) {
        val b = _boards.value ?: return
        save(b.copy(presets = b.presets.map { if (it.id == id) it.copy(name = name) else it }))
    }

    fun delete(id: String) {
        val b = _boards.value ?: return
        if (b.presets.size <= 1) return
        val rest = b.presets.filter { it.id != id }
        save(Boards(rest, if (b.activeId == id) rest.first().id else b.activeId))
    }

    /** Forgets every saved size of the active preset, so it falls back to the default layout. */
    fun resetActive() = updateActive { it.copy(layouts = emptyMap(), lastKey = null) }
}
