package my.github.MrxSiN.pixellauncherevolved.recovery

import my.github.MrxSiN.pixellauncherevolved.layout.Manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryListingTest {

    @Test
    fun aListingNamesEachCopyThatHasBothHalves() {
        val before = Manifest("launcher_4_by_6.db", 40, mapOf("idp_grid_name" to "medium"), mapOf("home_dock_icons" to 0)).encode()
        val listing = "@@before 1792582558\n$before@@latest \n"

        val copies = RecoveryActivity.parse(listing)

        assertEquals(setOf("before"), copies.keys)
        assertEquals(40, copies.getValue("before").manifest.items)
        assertEquals(1792582558L, copies.getValue("before").seconds)
    }

    @Test
    fun nothingKeptListsNothing() {
        assertTrue(RecoveryActivity.parse("@@before \n@@latest \n").isEmpty())
        assertTrue(RecoveryActivity.parse("").isEmpty())
    }
}
