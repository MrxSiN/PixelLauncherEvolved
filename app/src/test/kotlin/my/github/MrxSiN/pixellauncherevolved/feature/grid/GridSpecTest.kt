package my.github.MrxSiN.pixellauncherevolved.feature.grid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridSpecTest {

    @Test
    fun `a stored count reads as the launcher's own when unset or out of range`() {
        assertEquals(4, GridSpec.count(GridSpec.SYSTEM, stock = 4))
        assertEquals(4, GridSpec.count(2, stock = 4))
        assertEquals(4, GridSpec.count(11, stock = 4))
        assertEquals(6, GridSpec.count(6, stock = 4))
    }

    @Test
    fun `the launcher's own count is stored as System`() {
        assertEquals(GridSpec.SYSTEM, GridSpec.stored(4, stock = 4))
        assertEquals(5, GridSpec.stored(5, stock = 4))
    }

    @Test
    fun `columns fit a Pixel 8 Pro width with relaxed gaps and 48dp cells`() {
        // 1223px of cells, 49px gaps widened to 61, 144px touch targets.
        assertEquals(6, GridSpec.fits(1223, 144f, 61f))
        assertEquals(GridSpec.FEWEST, GridSpec.fits(100, 144f, 61f))
        assertEquals(GridSpec.MOST, GridSpec.fits(100_000, 144f, 61f))
    }

    @Test
    fun `the launcher's own count is always offered`() {
        assertEquals(3..6, GridSpec.choices(stock = 4, fits = 6))
        assertEquals(3..5, GridSpec.choices(stock = 5, fits = 4))
    }

    @Test
    fun `an imported count is kept when it fits and System otherwise`() {
        assertEquals(6, GridSpec.fitted(6, stock = 4, fits = 6))
        assertEquals(GridSpec.SYSTEM, GridSpec.fitted(7, stock = 4, fits = 6))
        assertEquals(GridSpec.SYSTEM, GridSpec.fitted(GridSpec.SYSTEM, stock = 4, fits = 6))
        assertEquals(GridSpec.SYSTEM, GridSpec.fitted(4, stock = 4, fits = 6))
    }

    @Test
    fun `icon sizes snap to the nearest step`() {
        assertEquals(100, GridSpec.nearestIconSize(0))
        assertEquals(85, GridSpec.nearestIconSize(10))
        assertEquals(115, GridSpec.nearestIconSize(118))
        assertEquals(130, GridSpec.nearestIconSize(200))
        assertEquals(1.3f, GridSpec.iconScale(130), 0.0001f)
    }

    @Test
    fun `spacing moves the gap into the padding and keeps the cells`() {
        // Pixel Launcher's 4 column width spec, as shares of the width.
        val (start, end, gap) = GridSpec.spread(0.045f, 0.045f, 0.036f, cells = 4, factor = GridSpec.spacingFactor(GridSpec.COMPACT))
        assertEquals(0.009f, gap, 0.0001f)
        assertEquals(0.045f + 0.0405f, start, 0.0001f)
        assertEquals(1f - 2 * 0.045f - 3 * 0.036f, 1f - start - end - 3 * gap, 0.0001f)

        // Relaxed only takes what the padding has, keeping some of it.
        val relaxed = GridSpec.spread(0.045f, 0.045f, 0.036f, cells = 4, factor = GridSpec.spacingFactor(GridSpec.RELAXED))
        assertEquals(0.045f * GridSpec.PADDING_KEPT, relaxed[0], 0.0001f)
        assertEquals(0.036f + 0.09f * (1 - GridSpec.PADDING_KEPT) / 3, relaxed[2], 0.0001f)

        // Uneven paddings keep their proportion; nothing to spread leaves the spec alone.
        val uneven = GridSpec.spread(0.016f, 0.052f, 0.016f, cells = 6, factor = 0.25f)
        assertEquals(0.016f / 0.052f, uneven[0] / uneven[1], 0.0001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), GridSpec.spread(0f, 0f, 0f, cells = 4, factor = 3f), 0f)
        assertEquals(1f, GridSpec.spacingFactor(0), 0f)
    }

    /** Items match on what they open, as the launcher's `DbEntry` does; the page is not part of it. */
    private class Item(val app: String, val screen: Int) {
        override fun equals(other: Any?) = other is Item && other.app == app
        override fun hashCode() = app.hashCode()
    }

    @Test
    fun `a remembered item keeps its cell only on the page it is on now`() {
        val src = listOf(Item("mail", 0), Item("maps", 1), Item("new", 0))
        val dest = listOf(Item("mail", 0), Item("maps", 0), Item("gone", 2))
        val (added, removed) = GridSpec.remembered(src, dest) { it.screen }
        // Mail stays as remembered; Maps moved pages since, so it is placed afresh; Gone is no longer there.
        assertEquals(listOf("maps" to 1, "new" to 0), added.map { it.app to it.screen })
        assertEquals(setOf("maps" to 0, "gone" to 2), removed.map { it.app to it.screen }.toSet())
    }

    private fun cell(screen: Int, x: Int, y: Int, span: Int = 1, minSpan: Int = 1) = GridSpec.Cell(screen, x, y, span, span, minSpan, minSpan)

    private fun place(screen: Int, columns: Int, rows: Int, items: List<GridSpec.Cell>) =
        GridSpec.placeOnPage(screen, columns, rows, Array(columns) { BooleanArray(rows) }, items)

    @Test
    fun `the launcher's page loop sends a full page's overflow to a new page`() {
        // The owner's 4 x 6 Home screen (pages 0 to 3) going to 3 x 6, as the launcher's loop asks page by page.
        val p0 = listOf(cell(0, 0, 3), cell(0, 3, 3), cell(0, 0, 4), cell(0, 1, 4), cell(0, 2, 4), cell(0, 3, 4), cell(0, 0, 5), cell(0, 1, 5), cell(0, 2, 5), cell(0, 3, 5))
        val p1 = listOf(GridSpec.Cell(1, 0, 0, 4, 2, 2, 1), cell(1, 0, 2), cell(1, 1, 2), cell(1, 2, 2), cell(1, 3, 2), GridSpec.Cell(1, 0, 3, 4, 2, 2, 1), cell(1, 0, 5), cell(1, 1, 5), cell(1, 2, 5), cell(1, 3, 5))
        val p3 = listOf(cell(3, 0, 0), cell(3, 1, 0), cell(3, 2, 0), cell(3, 3, 0), cell(3, 0, 1))
        val remaining = (p0 + p1 + p3).toMutableList()
        val pageOf = HashMap<GridSpec.Cell, Int>()
        var screen = 0
        while (remaining.isNotEmpty() && screen < 10) {
            val placed = place(screen, 3, 6, remaining)
            remaining.indices.filter { placed[it] != null }.forEach { pageOf[remaining[it]] = screen }
            val keep = remaining.filterIndexed { index, _ -> placed[index] == null }
            remaining.clear(); remaining += keep
            screen++
        }
        // Page 1's two items that no longer fit (column 3 of a full page) open page 4; page 3 keeps its own.
        assertEquals(setOf(4), p1.filter { pageOf[it] != 1 }.map { pageOf[it] }.toSet())
        assertTrue(p3.all { pageOf[it] == 3 })
    }

    @Test
    fun `a bigger grid keeps every item on its page and cell`() {
        val items = listOf(cell(0, 0, 0), cell(0, 3, 5), cell(1, 0, 0))
        val page = place(0, columns = 5, rows = 6, items)
        assertEquals(listOf(0 to 0, 3 to 5), page.take(2).map { it!!.x to it.y })
        // Page 1's item is not pulled onto page 0's free cells.
        assertEquals(null, page[2])
        assertEquals(0 to 0, place(1, 5, 6, items.drop(2))[0]!!.let { it.x to it.y })
    }

    @Test
    fun `a smaller grid moves what does not fit onto new pages instead of dropping it`() {
        // A full 4 x 2 page and one item on page 1, onto a 3 x 2 grid.
        val items = (0 until 4).flatMap { x -> (0 until 2).map { y -> cell(0, x, y) } } + cell(1, 0, 0)
        val page0 = place(0, columns = 3, rows = 2, items)
        assertEquals(6, page0.count { it != null && it !== GridSpec.Placement.DROPPED })
        val left = items.filterIndexed { index, _ -> page0[index] == null }
        assertEquals(3, left.size)
        // Page 1 keeps its own item where it was; page 0's overflow waits for a new page.
        val page1 = place(1, 3, 2, left)
        assertEquals(0 to 0, page1.last()!!.let { it.x to it.y })
        assertEquals(2, page1.count { it == null })
        val page2 = place(2, 3, 2, left.filterIndexed { index, _ -> page1[index] == null })
        assertTrue(page2.all { it != null && it !== GridSpec.Placement.DROPPED })
    }

    @Test
    fun `an item that cannot fit at its smallest is dropped and a widget shrinks to fit`() {
        val page = place(0, columns = 3, rows = 3, listOf(cell(0, 0, 0, span = 4, minSpan = 4), cell(0, 0, 0, span = 4, minSpan = 2)))
        assertTrue(page[0] === GridSpec.Placement.DROPPED)
        assertEquals(3, page[1]!!.spanX)
    }

    @Test
    fun `this module's databases can never be one of Google's`() {
        assertEquals("launcher_ple_5_by_7.db", GridSpec.dbFile(5, 7))
        assertTrue(GridSpec.isOwnDb(GridSpec.dbFile(5, 7)))
        assertFalse(GridSpec.isOwnDb("launcher_5_by_6.db"))
        assertFalse(GridSpec.isOwnDb(null))
        // The launcher's backup agent copies every launcher*.db.
        assertTrue(GridSpec.dbFile(5, 7).startsWith("launcher") && GridSpec.dbFile(5, 7).endsWith(".db"))
    }

    @Test
    fun `a grid too small for a widget's smallest size is refused`() {
        val items = listOf(
            GridSpec.Item("Clock", 1, 1),
            GridSpec.Item("Weather", 4, 2),
            GridSpec.Item("Calendar", 2, 5),
        )
        assertEquals(listOf("Weather"), GridSpec.misfits(items, columns = 3, rows = 6).map { it.title })
        assertEquals(listOf("Calendar"), GridSpec.misfits(items, columns = 4, rows = 4).map { it.title })
        assertTrue(GridSpec.misfits(items, columns = 4, rows = 5).isEmpty())
    }
}
