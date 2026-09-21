package my.github.MrxSiN.pixellauncherevolved.reveal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CircleRevealTest {

    @Test
    fun `the circle reaches the furthest edge from the point`() {
        val reveal = CircleReveal.aroundPoint(x = 100, y = 400, width = 1000, height = 2000)

        assertEquals(1600, reveal.endRadius)
    }

    @Test
    fun `a point in a corner still covers the screen`() {
        val reveal = CircleReveal.aroundPoint(x = 0, y = 0, width = 1000, height = 2000)

        assertEquals(2000, reveal.endRadius)
    }

    @Test
    fun `fully revealed is the whole circle around the point`() {
        val reveal = CircleReveal(centerX = 500, centerY = 1000, endRadius = 1200)

        assertArrayEquals(floatArrayOf(-700f, -200f, 1700f, 2200f), reveal.bounds(1f), 0f)
    }

    @Test
    fun `unrevealed is the point itself`() {
        val reveal = CircleReveal(centerX = 500, centerY = 1000, endRadius = 1200)

        assertArrayEquals(floatArrayOf(500f, 1000f, 500f, 1000f), reveal.bounds(0f), 0f)
    }

    @Test
    fun `half revealed is half the circle`() {
        val reveal = CircleReveal(centerX = 500, centerY = 1000, endRadius = 1200)

        assertArrayEquals(floatArrayOf(-100f, 400f, 1100f, 1600f), reveal.bounds(0.5f), 0f)
    }

    @Test
    fun `the screen stays dark around the circle until it is half revealed`() {
        val reveal = CircleReveal(centerX = 500, centerY = 1000, endRadius = 1200)

        assertEquals(1f, reveal.endColorAlpha(0f), 0f)
        assertEquals(1f, reveal.endColorAlpha(0.5f), 0f)
    }

    @Test
    fun `past half revealed the rest of the screen fades in with it`() {
        val reveal = CircleReveal(centerX = 500, centerY = 1000, endRadius = 1200)

        assertEquals(0.5f, reveal.endColorAlpha(0.75f), 0f)
        assertEquals(0f, reveal.endColorAlpha(1f), 0f)
    }
}
