package my.github.MrxSiN.pixellauncherevolved.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModeTest {

    @Test
    fun `choosing a mode switches every other mode off`() {
        assertEquals(
            mapOf(Settings.OVERVIEW_ONLY to false, Settings.TASKBAR_ONLY to true, Settings.TABLET_MODE to false),
            LayoutMode.TASKBAR_ONLY.switches(),
        )
    }

    @Test
    fun `the default is every mode switched off`() {
        assertTrue(LayoutMode.DEFAULT.switches().values.none { it })
        assertEquals(LayoutMode.DEFAULT, LayoutMode.current { false })
    }

    @Test
    fun `the mode whose switch is on is the current one`() {
        assertEquals(LayoutMode.FULL_TABLET, LayoutMode.current { it == Settings.TABLET_MODE })
    }

    @Test
    fun `an inverted switch reads on while its setting is off`() {
        assertTrue(FeatureCatalog.SCREENSHOT.shown(stored = false))
        assertFalse(FeatureCatalog.SCREENSHOT.stored(shown = true))
        assertTrue(FeatureCatalog.BUBBLE.shown(stored = true))
    }

    @Test
    fun `every stored switch is carried across by the migration`() {
        assertTrue(Settings.TASKBAR_ONLY in FeatureCatalog.switches)
        assertTrue(Settings.OVERVIEW_HIDE_SELECT in FeatureCatalog.switches)
    }
}
