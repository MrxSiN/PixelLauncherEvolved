package my.github.MrxSiN.pixellauncherevolved.feature.dock

import org.junit.Assert.assertEquals
import org.junit.Test

class DockIconsTest {

    @Test
    fun `counts run from one to what fits, never below the grid's own`() {
        assertEquals(1..7, DockIcons.choices(stock = 4, fits = 7))
        assertEquals(1..4, DockIcons.choices(stock = 4, fits = 3))
        assertEquals(1..DockIcons.MOST, DockIcons.choices(stock = 4, fits = 20))
    }

    @Test
    fun `a count out of range shows the grid's own`() {
        assertEquals(1, DockIcons.shown(1, 4))
        assertEquals(3, DockIcons.shown(3, 4))
        assertEquals(5, DockIcons.shown(5, 4))
        assertEquals(4, DockIcons.shown(0, 4))
        assertEquals(4, DockIcons.shown(DockIcons.MOST + 1, 4))
    }

    @Test
    fun `capacity rises with the count and never falls below the grid's`() {
        // Below the grid's capacity the launcher would delete pinned apps at load.
        assertEquals(4, DockIcons.capacity(3, 4))
        assertEquals(4, DockIcons.capacity(0, 4))
        assertEquals(5, DockIcons.capacity(5, 4))
    }

    @Test
    fun `fit counts cells that keep a touch target`() {
        // Pixel 8 Pro, 4x6: a 1149px dock, 8dp = 24px gap, 47.5dp = 142.5px cells.
        assertEquals(7, DockIcons.fits(1149, 142.5f, 24))
        assertEquals(0, DockIcons.fits(1149, 0f, 24))
        // Seven cells are 143px wide, so the 198px icons shrink to 143px.
        assertEquals(143, DockIcons.cellWidth(1149, 24, 7))
        assertEquals(198, DockIcons.cellWidth(1149, 119, 4))
    }

    @Test
    fun `the grid's own count is stored as System`() {
        assertEquals(0, DockIcons.stored(4, 4))
        assertEquals(5, DockIcons.stored(5, 4))
    }

    @Test
    fun `kept slots hold the capacity up`() {
        assertEquals(6, DockIcons.capacity(3, 4, kept = 6))
        assertEquals(4, DockIcons.capacity(3, 4, kept = 0))
    }

    @Test
    fun `a hidden dock gives the Home screen one more row`() {
        assertEquals(7, DockIcons.rows(6, hidden = true))
        assertEquals(6, DockIcons.rows(6, hidden = false))
    }

    @Test
    fun `the first free cell fits the item's span`() {
        val taken = setOf(0 to 0, 1 to 0, 3 to 0)
        val occupied = { x: Int, y: Int -> (x to y) in taken }
        assertEquals(listOf(2, 0), DockIcons.firstFree(4, 6, 1, 1, occupied)!!.toList())
        // A 2x1 item skips the lone free cell in row 0.
        assertEquals(listOf(0, 1), DockIcons.firstFree(4, 6, 2, 1, occupied)!!.toList())
        assertEquals(null, DockIcons.firstFree(4, 1, 2, 1, occupied))
    }
}
