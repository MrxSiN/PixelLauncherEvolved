package my.github.MrxSiN.pixellauncherevolved.feature.focus

import org.junit.Assert.assertEquals
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
}
