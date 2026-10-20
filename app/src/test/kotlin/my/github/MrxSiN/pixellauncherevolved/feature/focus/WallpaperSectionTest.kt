package my.github.MrxSiN.pixellauncherevolved.feature.focus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.pixellauncherevolved.focus.FocusPlan

class WallpaperSectionTest {

    @Test
    fun pagesSpreadOverAtLeastFour() {
        assertEquals(0f, wallpaperOffset(0, 1), 0f)
        assertEquals(0f, wallpaperOffset(0, 3), 0f)
        assertEquals(1f / 3, wallpaperOffset(1, 2), 1e-6f)
        assertEquals(2f / 3, wallpaperOffset(2, 3), 1e-6f)
        assertEquals(1f, wallpaperOffset(5, 6), 0f)
        assertEquals("not among the pages", 0f, wallpaperOffset(-1, 6), 0f)
    }

    @Test
    fun aPageShowsOneScreenOfTheCrop() {
        // The owner's wallpaper: a 2017 x 2245 crop, a 1344 x 2992 screen.
        val aspect = 1344f / 2992f
        val first = wallpaperSection(2017, 2245, aspect, 0f, 1f)
        assertArrayEquals(floatArrayOf(0f, 0f, 2245 * aspect, 2245f), first, 0.01f)
        val last = wallpaperSection(2017, 2245, aspect, 1f, 1f)
        assertEquals(2017f, last[2], 0.01f)
    }

    @Test
    fun zoomShrinksAboutTheScreensCentre() {
        val section = wallpaperSection(1000, 2000, 0.5f, 0f, 1.25f)
        assertArrayEquals(floatArrayOf(100f, 200f, 900f, 1800f), section, 0.01f)
    }

    @Test
    fun aNarrowCropIsCoveredNotStretched() {
        val section = wallpaperSection(500, 2000, 0.5f, 0.7f, 1f)
        assertArrayEquals(floatArrayOf(0f, 500f, 500f, 1500f), section, 0.01f)
    }

    @Test
    fun aModesPageSitsAmongThatModesPages() {
        val order = listOf(0, 1, 6, 7)
        val assignments = mapOf("work" to setOf(7), "driving" to setOf(1, 6))
        assertEquals(listOf(7), FocusPlan.shownWith(7, order, assignments))
        assertEquals(listOf(1, 6), FocusPlan.shownWith(6, order, assignments))
        assertEquals("ordinary pages", listOf(0), FocusPlan.shownWith(0, order, assignments))
    }
}
