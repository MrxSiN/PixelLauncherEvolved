package my.github.MrxSiN.pixellauncherevolved.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageOrderTest {

    @Test
    fun aPageMovesToWhereItIsDropped() {
        assertEquals(listOf(0, 2, 1, 5), PageOrder.move(listOf(0, 1, 2, 5), from = 2, to = 1))
        assertEquals(listOf(0, 2, 5, 1), PageOrder.move(listOf(0, 1, 2, 5), from = 1, to = 3))
    }

    @Test
    fun theFirstPageNeverMovesAndNothingGoesBeforeIt() {
        val order = listOf(0, 1, 2)
        assertEquals(order, PageOrder.move(order, from = 0, to = 2))
        assertEquals(listOf(0, 2, 1), PageOrder.move(order, from = 2, to = 0))
    }

    @Test
    fun eachMovedPageTakesTheIdOfItsNewPosition() {
        // Pages 0, 1, 2, 5 shown in that order; page 5 dragged to second.
        val mapping = PageOrder.renumbering(current = listOf(0, 1, 2, 5), wanted = listOf(0, 5, 1, 2))

        assertEquals(mapOf(5 to 1, 1 to 2, 2 to 5), mapping)
    }

    @Test
    fun anUnchangedOrderRenumbersNothing() {
        assertTrue(PageOrder.renumbering(listOf(0, 3, 4), listOf(0, 3, 4)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun theFirstPageCannotBeRenumbered() {
        PageOrder.renumbering(listOf(0, 1), listOf(1, 0))
    }
}
