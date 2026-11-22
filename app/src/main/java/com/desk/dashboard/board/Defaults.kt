package com.desk.dashboard.board

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Starting layouts: one designed for landscape (12 × 6) and one for portrait (5 × 10). Any other
 * grid gets whichever matches its shape, adapted to fit.
 */
object Defaults {
    private fun t(type: String, x: Int, y: Int, w: Int, h: Int, style: String = "PLAIN", vararg cfg: Pair<String, Any>) =
        TileSpec(
            "$type-$x-$y", type, x, y, w, h, style,
            JsonObject(cfg.associate { (k, v) -> k to (if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString())) }),
        )

    val landscape = GridLayout(
        12, 6,
        listOf(
            t("clock", 0, 0, 5, 2, "ACCENT"),
            t("agenda", 5, 0, 7, 2),
            t("music", 0, 2, 4, 4),
            t("countdown", 4, 2, 8, 2),
            t("alarm", 4, 4, 4, 2),
            t("weather", 8, 4, 3, 2),
            t("ambient", 11, 4, 1, 1, "ALT"),
            t("timer", 11, 5, 1, 1),
        ),
    )

    val portrait = GridLayout(
        5, 10,
        listOf(
            t("clock", 0, 0, 5, 2, "ACCENT"),
            t("agenda", 0, 2, 5, 2),
            t("weather", 0, 4, 3, 2),
            t("alarm", 3, 4, 2, 2),
            t("countdown", 0, 6, 5, 2),
            t("music", 0, 8, 4, 2),
            t("ambient", 4, 8, 1, 2, "ALT"),
        ),
    )

    /**
     * The user's tiles on a screen of the other shape (a landscape layout shown in portrait):
     * arranged like the default template for that shape, each template slot taken by the user's
     * tile of the same kind (keeping its style and settings), then the rest wherever they fit.
     */
    fun reshape(from: GridLayout, cols: Int, rows: Int): GridLayout {
        val template = if (cols >= rows) landscape else portrait
        val pool = from.tiles.toMutableList()
        val placed = template.tiles.mapNotNull { slot ->
            val mine = pool.firstOrNull { it.type == slot.type } ?: return@mapNotNull null
            pool.remove(mine)
            mine.copy(x = slot.x, y = slot.y, w = slot.w, h = slot.h)
        }.toMutableList()
        // Leftovers: at their own size if there's a free spot, else at their minimum size.
        for (t in pool) {
            val min = Modules.minSize(t.type)
            for ((w, h) in listOf(t.w.coerceAtMost(template.cols) to t.h.coerceAtMost(template.rows), min.w to min.h)) {
                val spot = Grid.firstFree(placed, w, h, template.cols, template.rows) ?: continue
                placed += t.copy(x = spot.first, y = spot.second, w = w, h = h)
                break
            }
        }
        return Grid.adapt(GridLayout(template.cols, template.rows, placed), cols, rows, Modules::minSize).first
    }

    fun layout(cols: Int, rows: Int): GridLayout {
        val base = if (cols >= rows) landscape else portrait
        return Grid.adapt(base, cols, rows, Modules::minSize).first
    }
}
