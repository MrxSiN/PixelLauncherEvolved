package my.github.MrxSiN.pixellauncherevolved.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressiveGroupingTest {

    @Test
    fun `a run of one row is rounded on both ends`() {
        assertEquals(RowPlacement.SINGLE, ExpressiveGrouping.placementOf(index = 0, size = 1))
    }

    @Test
    fun `the ends of a run carry the card's corners`() {
        assertEquals(RowPlacement.TOP, ExpressiveGrouping.placementOf(index = 0, size = 4))
        assertEquals(RowPlacement.BOTTOM, ExpressiveGrouping.placementOf(index = 3, size = 4))
    }

    @Test
    fun `rows between the ends are squared off`() {
        assertEquals(RowPlacement.MIDDLE, ExpressiveGrouping.placementOf(index = 1, size = 4))
        assertEquals(RowPlacement.MIDDLE, ExpressiveGrouping.placementOf(index = 2, size = 4))
    }

    @Test
    fun `a heading starts a new card`() {
        val rows = listOf("one", "HEAD", "two", "three")

        assertEquals(
            listOf(listOf("one"), listOf("two", "three")),
            ExpressiveGrouping.runs(rows) { it == "HEAD" },
        )
    }

    @Test
    fun `a screen that opens with a heading has no card above it`() {
        val rows = listOf("HEAD", "one", "two")

        assertEquals(listOf(listOf("one", "two")), ExpressiveGrouping.runs(rows) { it == "HEAD" })
    }

    @Test
    fun `a heading with nothing under it draws no card`() {
        assertEquals(emptyList<List<String>>(), ExpressiveGrouping.runs(listOf("HEAD")) { it == "HEAD" })
    }
}
