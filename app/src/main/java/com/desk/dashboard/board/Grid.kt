package com.desk.dashboard.board

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One tile on the grid: cell position and size, its module type, look and settings. */
@Serializable
data class TileSpec(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val style: String = "PLAIN",
    val config: JsonObject = JsonObject(emptyMap()),
) {
    fun overlaps(o: TileSpec) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    fun at(nx: Int, ny: Int) = copy(x = nx, y = ny)
    fun sized(nw: Int, nh: Int) = copy(w = nw, h = nh)
}

/** A layout for one grid size. */
@Serializable
data class GridLayout(val cols: Int, val rows: Int, val tiles: List<TileSpec>) {
    val key get() = key(cols, rows)

    companion object {
        fun key(cols: Int, rows: Int) = "${cols}x$rows"
    }
}

data class Size(val w: Int, val h: Int)

/** Grid geometry and placement rules. Pure functions, so they're unit tested. */
object Grid {
    /** Cells aim for this size (dp); the count adapts to the screen, the cells stretch to fill it. */
    const val TARGET_CELL_DP = 80f

    fun sizeFor(widthDp: Float, heightDp: Float): Size = Size(
        (widthDp / TARGET_CELL_DP).roundToInt().coerceIn(4, 16),
        (heightDp / TARGET_CELL_DP).roundToInt().coerceIn(4, 12),
    )

    fun inBounds(t: TileSpec, cols: Int, rows: Int) =
        t.x >= 0 && t.y >= 0 && t.w >= 1 && t.h >= 1 && t.x + t.w <= cols && t.y + t.h <= rows

    /** True if [t] lies inside the grid and overlaps none of [others] (other than itself). */
    fun fits(t: TileSpec, others: List<TileSpec>, cols: Int, rows: Int) =
        inBounds(t, cols, rows) && others.none { it.id != t.id && it.overlaps(t) }

    /** First free spot for a [w]×[h] tile, scanning rows top to bottom; null if none. */
    fun firstFree(tiles: List<TileSpec>, w: Int, h: Int, cols: Int, rows: Int, startY: Int = 0, startX: Int = 0): Pair<Int, Int>? {
        val probe = TileSpec("_", "_", 0, 0, w, h)
        for (y in 0..rows - h) for (x in 0..cols - w) {
            if (y < startY || (y == startY && x < startX)) continue
            if (fits(probe.at(x, y), tiles, cols, rows)) return x to y
        }
        if (startY > 0 || startX > 0) return firstFree(tiles, w, h, cols, rows)
        return null
    }

    /**
     * Moves [moving] to ([nx], [ny]). Valid if the spot is free; or if it lands exactly on one
     * other tile of the same size, the two swap places. Returns the new tile list, or null.
     */
    fun move(tiles: List<TileSpec>, moving: TileSpec, nx: Int, ny: Int, cols: Int, rows: Int): List<TileSpec>? {
        val target = moving.at(nx, ny)
        if (!inBounds(target, cols, rows)) return null
        val hit = tiles.filter { it.id != moving.id && it.overlaps(target) }
        if (hit.isEmpty()) return tiles.map { if (it.id == moving.id) target else it }
        val other = hit.singleOrNull() ?: return null
        if (other.x == nx && other.y == ny && other.w == moving.w && other.h == moving.h) {
            return tiles.map {
                when (it.id) {
                    moving.id -> target
                    other.id -> other.at(moving.x, moving.y)
                    else -> it
                }
            }
        }
        return null
    }

    /**
     * Like [move], but tiles in the way are pushed aside: each moves, at its size, to the free
     * spot nearest where it was. Null if the target is off the grid or a pushed tile has nowhere
     * to go.
     */
    fun movePush(tiles: List<TileSpec>, moving: TileSpec, nx: Int, ny: Int, cols: Int, rows: Int): List<TileSpec>? {
        move(tiles, moving, nx, ny, cols, rows)?.let { return it }
        val target = moving.at(nx, ny)
        if (!inBounds(target, cols, rows)) return null
        val hit = tiles.filter { it.id != moving.id && it.overlaps(target) }.sortedWith(compareBy({ it.y }, { it.x }))
        val placed = (tiles.filter { it.id != moving.id && it !in hit } + target).toMutableList()
        for (t in hit) placed += nearestFree(placed, t, cols, rows) ?: return null
        val byId = placed.associateBy { it.id }
        return tiles.map { byId[it.id] ?: it }
    }

    /**
     * Resizes [t] towards [nw]×[nh], clamped to [minSize] and the grid, shrinking back until it
     * no longer overlaps anything. Returns the largest valid size (at least the current one).
     */
    fun resize(tiles: List<TileSpec>, t: TileSpec, nw: Int, nh: Int, minSize: Size, cols: Int, rows: Int): TileSpec {
        val w = nw.coerceIn(minSize.w, cols - t.x)
        val h = nh.coerceIn(minSize.h, rows - t.y)
        // Prefer the requested width, then height; fall back towards the current size.
        for (tw in w downTo min(w, t.w)) for (th in h downTo min(h, t.h)) {
            val c = t.sized(tw, th)
            if (fits(c, tiles, cols, rows)) return c
        }
        for (tw in w downTo minSize.w) for (th in h downTo minSize.h) {
            val c = t.sized(tw, th)
            if (fits(c, tiles, cols, rows)) return c
        }
        return t
    }

    /**
     * Fits [from] onto a [cols]×[rows] grid. Scales the grid lines when every tile can keep its
     * minimum size that way; otherwise scales each tile, then packs them in reading order,
     * each as close to its scaled spot as it can go, shrinking towards its minimum size when
     * needed. Tiles that can't fit at all are dropped (and returned separately).
     */
    fun adapt(from: GridLayout, cols: Int, rows: Int, minSize: (String) -> Size): Pair<GridLayout, List<TileSpec>> {
        if (from.cols == cols && from.rows == rows) return from to emptyList()
        scaleLines(from, cols, rows, minSize)?.let { return it to emptyList() }
        val sx = cols.toFloat() / from.cols
        val sy = rows.toFloat() / from.rows
        val placed = mutableListOf<TileSpec>()
        val dropped = mutableListOf<TileSpec>()
        for (t in from.tiles.sortedWith(compareBy({ it.y }, { it.x }))) {
            val min = minSize(t.type)
            // Round sizes up generously: a tile that loses a row on a shorter screen loses half its height.
            val w = max(min.w, kotlin.math.ceil(t.w * sx - 0.3f).toInt()).coerceIn(1, cols)
            val h = max(min.h, kotlin.math.ceil(t.h * sy - 0.3f).toInt()).coerceIn(1, rows)
            val x = (t.x * sx).roundToInt().coerceIn(0, cols - w)
            val y = (t.y * sy).roundToInt().coerceIn(0, rows - h)
            val spot = placeNear(placed, t.copy(x = x, y = y, w = w, h = h), min, cols, rows)
            if (spot != null) placed += spot else dropped += t
        }
        return GridLayout(cols, rows, placed) to dropped
    }

    /**
     * Scales the grid lines rather than each tile, so tiles that touched still touch and the
     * layout keeps its shape with no gaps. Null if some tile would end up below its minimum size.
     */
    private fun scaleLines(from: GridLayout, cols: Int, rows: Int, minSize: (String) -> Size): GridLayout? {
        val ts = from.tiles.filter { inBounds(it, from.cols, from.rows) }
        if (ts.size != from.tiles.size) return null
        val xs = mapLines(from.cols, cols, ts.map { Triple(it.x, it.x + it.w, minSize(it.type).w) }) ?: return null
        val ys = mapLines(from.rows, rows, ts.map { Triple(it.y, it.y + it.h, minSize(it.type).h) }) ?: return null
        return GridLayout(cols, rows, ts.map {
            it.copy(x = xs[it.x], y = ys[it.y], w = xs[it.x + it.w] - xs[it.x], h = ys[it.y + it.h] - ys[it.y])
        })
    }

    /**
     * Maps lines 0..[from] onto 0..[to], in order and as close to proportional as it can, while
     * every span (start, end, minimum) stays at least its minimum wide. Null if that's impossible.
     */
    internal fun mapLines(from: Int, to: Int, spans: List<Triple<Int, Int, Int>>): IntArray? {
        val mins = spans.map { it.copy(third = max(1, it.third)) }
        // need[b]: cells the spans from line b onwards require before the far edge.
        val need = IntArray(from + 1)
        for (b in from - 1 downTo 0) {
            need[b] = max(need[b + 1], mins.filter { it.first == b }.maxOfOrNull { it.third + need[it.second] } ?: 0)
        }
        if (need[0] > to) return null
        val f = IntArray(from + 1)
        for (b in 1..from) {
            val lo = max(f[b - 1], mins.filter { it.second == b }.maxOfOrNull { f[it.first] + it.third } ?: 0)
            val hi = to - need[b]
            if (lo > hi) return null
            f[b] = (b * to.toFloat() / from).roundToInt().coerceIn(lo, hi)
        }
        return f
    }

    /** [t] at its own spot if free, else the nearest free spot at its size, then smaller sizes. */
    private fun placeNear(placed: List<TileSpec>, t: TileSpec, min: Size, cols: Int, rows: Int): TileSpec? {
        var w = t.w
        var h = t.h
        while (true) {
            val c = t.sized(w, h)
            if (fits(c, placed, cols, rows)) return c
            nearestFree(placed, c, cols, rows)?.let { return it }
            when {
                w > min.w && w >= h -> w--
                h > min.h -> h--
                w > min.w -> w--
                else -> return null
            }
        }
    }

    private fun nearestFree(placed: List<TileSpec>, t: TileSpec, cols: Int, rows: Int): TileSpec? {
        var best: TileSpec? = null
        var bestD = Int.MAX_VALUE
        for (y in 0..rows - t.h) for (x in 0..cols - t.w) {
            val c = t.at(x, y)
            if (!fits(c, placed, cols, rows)) continue
            val d = (x - t.x) * (x - t.x) + 2 * (y - t.y) * (y - t.y)
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }
}
