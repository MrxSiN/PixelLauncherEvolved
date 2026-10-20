package my.github.MrxSiN.pixellauncherevolved.feature.focus

import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPageRenderer.State

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderBookTest {

    private val book = RenderBook<String>()

    @Test
    fun aPageIsDrawnOncePerModel() {
        val first = book.begin(SCREEN, 0)
        assertNotNull(first)
        assertNull("already on its way", book.begin(SCREEN, 0))
        assertTrue(book.finish(SCREEN, first!!, 0, "page"))
        assertEquals(State.READY, book.state(SCREEN))
        assertNull("already drawn from this model", book.begin(SCREEN, 0))
        assertNotNull("the model changed", book.begin(SCREEN, 1))
    }

    @Test
    fun anOlderDrawingNeverReplacesANewerRequest() {
        val old = book.begin(SCREEN, 0)!!
        book.renumber(mapOf(SCREEN to OTHER, OTHER to SCREEN))
        val new = book.begin(SCREEN, 0)!!

        assertFalse(book.finish(SCREEN, old, 0, "stale"))
        assertTrue(book.finish(SCREEN, new, 0, "fresh"))
        assertEquals("fresh", book.picture(SCREEN))
    }

    @Test
    fun aStalePictureIsShownUntilItsReplacementArrives() {
        book.finish(SCREEN, book.begin(SCREEN, 0)!!, 0, "old")
        val redraw = book.begin(SCREEN, 1)!!
        assertEquals(State.PENDING, book.state(SCREEN))
        assertEquals("old", book.picture(SCREEN))

        assertTrue(book.finish(SCREEN, redraw, 1, null))
        assertEquals("a failed redraw keeps the last picture", State.READY, book.state(SCREEN))
        assertEquals("old", book.picture(SCREEN))
        assertNotNull("and is tried again", book.begin(SCREEN, 1))
    }

    @Test
    fun aPageNeverDrawnFails() {
        book.finish(SCREEN, book.begin(SCREEN, 0)!!, 0, null)
        assertEquals(State.FAILED, book.state(SCREEN))
    }

    @Test
    fun picturesFollowTheirPagesAndGoWithThem() {
        book.finish(SCREEN, book.begin(SCREEN, 0)!!, 0, "five")
        book.finish(OTHER, book.begin(OTHER, 0)!!, 0, "seven")
        book.renumber(mapOf(SCREEN to OTHER, OTHER to SCREEN))
        assertEquals("seven", book.picture(SCREEN))
        assertEquals("five", book.picture(OTHER))

        book.retain(listOf(OTHER))
        assertNull(book.picture(SCREEN))
        assertEquals(State.NONE, book.state(SCREEN))
    }

    private companion object {
        const val SCREEN = 5
        const val OTHER = 7
    }
}
