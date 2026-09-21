package my.github.MrxSiN.pixellauncherevolved.reveal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepRevealOriginTest {

    private var now = 1_000L
    private val origin = SleepRevealOrigin(freshForMillis = 2_000L) { now }

    @Test
    fun `nothing is drawn around a point until one is announced`() {
        assertNull(origin.current())
    }

    @Test
    fun `the announced point stands while the screen off is on its way`() {
        origin.remember(x = 300, y = 900)

        now += 1_500L

        assertEquals(Origin(300, 900), origin.current())
    }

    @Test
    fun `a point that never reached a screen off is dropped`() {
        origin.remember(x = 300, y = 900)

        now += 2_001L

        assertNull(origin.current())
    }

    @Test
    fun `the reveal that used the point gives it back`() {
        origin.remember(x = 300, y = 900)
        origin.forget()

        assertNull(origin.current())
    }

    @Test
    fun `a second gesture replaces the first point and its age`() {
        origin.remember(x = 300, y = 900)

        now += 1_500L
        origin.remember(x = 700, y = 100)
        now += 1_500L

        assertEquals(Origin(700, 100), origin.current())
    }
}
