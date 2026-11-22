package com.desk.dashboard.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.desk.dashboard.modules.AgendaModule
import com.desk.dashboard.modules.AlarmModule
import com.desk.dashboard.modules.AmbientModule
import com.desk.dashboard.modules.ChecklistModule
import com.desk.dashboard.modules.ClockModule
import com.desk.dashboard.modules.ControlsModule
import com.desk.dashboard.modules.CountdownModule
import com.desk.dashboard.modules.MusicModule
import com.desk.dashboard.modules.NoteModule
import com.desk.dashboard.modules.PhotoModule
import com.desk.dashboard.modules.QuoteModule
import com.desk.dashboard.modules.ShortcutModule
import com.desk.dashboard.modules.SystemModule
import com.desk.dashboard.modules.TimerModule
import com.desk.dashboard.modules.WeatherModule
import com.desk.dashboard.modules.WorldClockModule
import com.desk.dashboard.ui.TileStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What the dashboard lets a tile do outside itself. */
class DashActions(
    val openSettings: (section: String?) -> Unit,
    val lock: () -> Unit,
    val openApps: () -> Unit,
    val editLayout: () -> Unit,
)

val LocalDashActions = compositionLocalOf<DashActions?> { null }

/** Shows a centred overlay panel (dialogs, pickers) above everything; null closes it. */
val LocalOverlay = compositionLocalOf<(((@Composable () -> Unit)?) -> Unit)> { {} }

/** One tile as a module sees it: its size in cells and its settings. */
@Stable
class TileScope(
    val spec: TileSpec,
    private val onConfig: (JsonObject) -> Unit,
) {
    val id get() = spec.id
    val w get() = spec.w
    val h get() = spec.h
    val config get() = spec.config

    private fun p(k: String) = (config[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    fun bool(k: String, d: Boolean) = p(k)?.booleanOrNull ?: d
    fun int(k: String, d: Int) = p(k)?.intOrNull ?: d
    fun long(k: String, d: Long) = p(k)?.longOrNull ?: d
    fun double(k: String, d: Double) = p(k)?.doubleOrNull ?: d
    fun str(k: String, d: String) = p(k)?.contentOrNull ?: d
    fun strOrNull(k: String) = p(k)?.contentOrNull
    fun strings(k: String): List<String> =
        (config[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    fun set(k: String, v: Any?) = setAll(k to v)

    fun setAll(vararg kv: Pair<String, Any?>) {
        val m = config.toMutableMap()
        for ((k, v) in kv) {
            val e: JsonElement? = when (v) {
                null -> null
                is Boolean -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is String -> JsonPrimitive(v)
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
            if (e == null) m.remove(k) else m[k] = e
        }
        onConfig(JsonObject(m))
    }
}

/**
 * A kind of tile. [Content] must cope with any size from [minSize] up: tiles are resized freely
 * on grids that differ from screen to screen.
 */
abstract class Module(
    val type: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val defaultSize: Size,
    val minSize: Size,
    val defaultStyle: TileStyle = TileStyle.PLAIN,
) {
    /** Label shown at the top of the tile. */
    open fun label(t: TileScope): String = t.str("title", name)

    @Composable
    abstract fun Content(t: TileScope)

    /** Module-specific options in the tile's settings sheet (style, label and size are generic). */
    @Composable
    open fun Settings(t: TileScope) {}

    /** Settings for a tile placed from the picker. */
    open fun initialConfig(): Map<String, Any?> = emptyMap()
}

object Modules {
    val all: List<Module> by lazy {
        listOf(
            ClockModule, AgendaModule, CountdownModule, WeatherModule, MusicModule, AlarmModule,
            TimerModule, ChecklistModule, NoteModule, WorldClockModule, PhotoModule, QuoteModule,
            AmbientModule, ShortcutModule, SystemModule, ControlsModule,
        )
    }

    fun get(type: String): Module? = all.firstOrNull { it.type == type }

    fun minSize(type: String): Size = get(type)?.minSize ?: Size(1, 1)
}
