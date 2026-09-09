package my.github.MrxSiN.pixellauncherevolved.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The depths below are the launcher's own: zero for home, and higher as a state
 * takes over the screen. See `HOOK_NOTES.md` for where they were read.
 */
class HomeBlurDepthTest {

    private val controller = Any()
    private val home = Any()

    @Test
    fun homeReportsTheBlurDepthWhenSwitchedOn() {
        val blur = HomeBlurDepth().apply { isEnabled = true }

        assertEquals(0.15f, blur.depthForHome(0f), TOLERANCE)
    }

    @Test
    fun theMiddleIsHalfOfTheLaunchersDeepestBlur() {
        assertEquals(
            HomeBlurDepth.FULL_DEPTH / 2f,
            HomeBlurDepth.depthFor(HomeBlurDepth.DEFAULT_STRENGTH),
            TOLERANCE,
        )
    }

    @Test
    fun strengthRunsFromNothingToTheLaunchersDeepestBlur() {
        assertEquals(0f, HomeBlurDepth.depthFor(0), TOLERANCE)
        assertEquals(HomeBlurDepth.FULL_DEPTH, HomeBlurDepth.depthFor(100), TOLERANCE)
    }

    @Test
    fun aStrengthOutsideTheRangeIsBroughtBackIntoIt() {
        assertEquals(0f, HomeBlurDepth.depthFor(-20), TOLERANCE)
        assertEquals(HomeBlurDepth.FULL_DEPTH, HomeBlurDepth.depthFor(400), TOLERANCE)
    }

    @Test
    fun aStrongerSettingBlursDeeper() {
        val blur = HomeBlurDepth().apply { isEnabled = true }

        blur.strength = 100
        val strong = blur.depthForHome(0f)
        blur.strength = 10
        val weak = blur.depthForHome(0f)

        assertTrue("$strong should be deeper than $weak", strong > weak)
    }

    @Test
    fun homeReportsItsOwnDepthWhenSwitchedOff() {
        val blur = HomeBlurDepth()

        assertEquals(0f, blur.depthForHome(0f), 0f)
    }

    @Test
    fun aLauncherThatRestsDeeperKeepsItsOwnAnswer() {
        val blur = HomeBlurDepth().apply { isEnabled = true }

        assertEquals(0.4f, blur.depthForHome(0.4f), TOLERANCE)
    }

    @Test
    fun theStateThatWasAppliedIsTheStateToApplyAgain() {
        val blur = HomeBlurDepth()
        blur.remember(controller, home)

        assertEquals(listOf(controller to home), blur.remembered())
    }

    @Test
    fun onlyTheLastStateOfEachControllerIsKept() {
        val blur = HomeBlurDepth()
        val drawer = Any()
        blur.remember(controller, home)
        blur.remember(controller, drawer)

        assertEquals(listOf(controller to drawer), blur.remembered())
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
