package my.github.MrxSiN.pixellauncherevolved.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringInterpolatorTest {

    @Test
    fun startsAtRestAndEndsAtItsDestination() {
        val spring = spring()

        assertEquals(0f, spring.getInterpolation(0f), 0f)
        assertEquals(1f, spring.getInterpolation(1f), TOLERANCE)
    }

    /** The overshoot is the whole point: without it this is an ease. */
    @Test
    fun overshootsBeforeItSettles() {
        val spring = spring()

        val peak = (0..100).maxOf { spring.getInterpolation(it / 100f) }

        assertTrue("A spring that never passes its destination is not sprung: $peak", peak > 1f)
    }

    /** Heavier damping is the calmer spring, so it passes its target by less. */
    @Test
    fun heavierDampingOvershootsLess() {
        val lively = (0..100).maxOf { spring(dampingRatio = 0.5f).getInterpolation(it / 100f) }
        val calm = (0..100).maxOf { spring(dampingRatio = 0.9f).getInterpolation(it / 100f) }

        assertTrue("Damping 0.9 overshot $calm, damping 0.5 only $lively", calm < lively)
    }

    @Test
    fun aSpringThatCannotOvershootIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { spring(dampingRatio = 1f) }
        assertThrows(IllegalArgumentException::class.java) { spring(dampingRatio = 0f) }
    }

    private fun spring(dampingRatio: Float = 0.6f) = SpringInterpolator(
        dampingRatio = dampingRatio,
        stiffness = 800f,
        durationMs = 400L,
    )

    private companion object {

        /** The tail of a spring approaches its rest rather than arriving on it. */
        const val TOLERANCE = 0.01f
    }
}
