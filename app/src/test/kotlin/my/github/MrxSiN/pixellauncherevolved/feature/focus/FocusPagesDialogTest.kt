package my.github.MrxSiN.pixellauncherevolved.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPagesDialogTest {

    @Test
    fun pagesOwnedByOtherModesAreNotSelectable() {
        val assignments = mapOf(
            "work" to setOf(3),
            "driving" to setOf(2),
        )

        assertEquals(listOf(1, 4), selectablePages(listOf(1, 2, 3, 4), assignments, "transit"))
        assertEquals(listOf(1, 3, 4), selectablePages(listOf(1, 2, 3, 4), assignments, "work"))
    }

    @Test
    fun theLastOrdinaryPageCannotBeGivenToAMode() {
        val assignments = mapOf("work" to setOf(3))

        assertTrue(leavesOrdinaryPage(listOf(1, 2, 3), assignments, "driving", setOf(2)))
        assertFalse(leavesOrdinaryPage(listOf(1, 2, 3), assignments, "driving", setOf(1, 2)))
        // A Mode's own pages, given back, count as ordinary again.
        assertTrue(leavesOrdinaryPage(listOf(1, 2, 3), mapOf("work" to setOf(1, 2, 3)), "work", setOf(1, 2)))
    }
}
