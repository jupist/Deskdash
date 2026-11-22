package com.desk.dashboard

import com.desk.dashboard.board.Grid
import com.desk.dashboard.board.GridLayout
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    private fun t(id: String, x: Int, y: Int, w: Int, h: Int, type: String = "clock") = TileSpec(id, type, x, y, w, h)
    private val min: (String) -> Size = { Size(1, 1) }

    @Test fun sizeAdaptsToScreen() {
        assertEquals(Size(12, 6), Grid.sizeFor(982f, 516f)) // 10" tablet, landscape
        assertEquals(Size(5, 9), Grid.sizeFor(387f, 700f)) // phone, portrait
        assertEquals(Size(4, 4), Grid.sizeFor(200f, 200f)) // never below 4 × 4
    }

    @Test fun fitsRejectsOverlapAndOutOfBounds() {
        val tiles = listOf(t("a", 0, 0, 2, 2))
        assertFalse(Grid.fits(t("b", 1, 1, 2, 2), tiles, 6, 4))
        assertTrue(Grid.fits(t("b", 2, 0, 2, 2), tiles, 6, 4))
        assertFalse(Grid.fits(t("b", 5, 0, 2, 2), tiles, 6, 4))
        // A tile never collides with itself.
        assertTrue(Grid.fits(t("a", 0, 0, 3, 2), tiles, 6, 4))
    }

    @Test fun firstFreeScansReadingOrder() {
        val tiles = listOf(t("a", 0, 0, 2, 2), t("b", 2, 0, 2, 1))
        assertEquals(2 to 1, Grid.firstFree(tiles, 2, 1, 4, 4))
        assertEquals(0 to 2, Grid.firstFree(tiles, 4, 1, 4, 4))
        assertNull(Grid.firstFree(tiles, 5, 1, 4, 4))
    }

    @Test fun moveIntoFreeSpaceOrSwapEqualSizes() {
        val tiles = listOf(t("a", 0, 0, 1, 1), t("b", 1, 0, 1, 1), t("c", 0, 1, 2, 1))
        val moved = Grid.move(tiles, tiles[0], 3, 0, 4, 2)!!
        assertEquals(3, moved.first { it.id == "a" }.x)
        // Dropping exactly onto a same-size tile swaps them.
        val swapped = Grid.move(tiles, tiles[0], 1, 0, 4, 2)!!
        assertEquals(1, swapped.first { it.id == "a" }.x)
        assertEquals(0, swapped.first { it.id == "b" }.x)
        // Partly over a bigger tile: refused.
        assertNull(Grid.move(tiles, tiles[0], 1, 1, 4, 2))
    }

    @Test fun resizeStopsAtNeighboursAndMinimum() {
        val tiles = listOf(t("a", 0, 0, 1, 1), t("b", 3, 0, 1, 1))
        val grown = Grid.resize(tiles, tiles[0], 6, 1, Size(1, 1), 6, 4)
        assertEquals(3, grown.w) // up to the neighbour, not through it
        val shrunk = Grid.resize(tiles, tiles[0].copy(w = 3, h = 2), 0, 0, Size(2, 1), 6, 4)
        assertEquals(2, shrunk.w)
        assertEquals(1, shrunk.h)
    }

    @Test fun adaptKeepsEveryTileWhenThereIsRoom() {
        val from = GridLayout(12, 6, listOf(t("a", 0, 0, 6, 3), t("b", 6, 0, 6, 3), t("c", 0, 3, 12, 3)))
        val (to, dropped) = Grid.adapt(from, 8, 6, min)
        assertTrue(dropped.isEmpty())
        assertEquals(3, to.tiles.size)
        for (x in to.tiles) assertTrue(Grid.fits(x, to.tiles, 8, 6))
    }

    @Test fun adaptDropsWhatCannotFit() {
        val from = GridLayout(4, 4, (0 until 16).map { t("t$it", it % 4, it / 4, 1, 1) })
        val (to, dropped) = Grid.adapt(from, 2, 2, min)
        assertEquals(4, to.tiles.size)
        assertEquals(12, dropped.size)
    }

    @Test fun adaptToSameSizeIsIdentity() {
        val from = GridLayout(6, 4, listOf(t("a", 1, 1, 2, 2)))
        assertNotNull(Grid.adapt(from, 6, 4, min).first.tiles.single { it.x == 1 && it.y == 1 })
    }
}
