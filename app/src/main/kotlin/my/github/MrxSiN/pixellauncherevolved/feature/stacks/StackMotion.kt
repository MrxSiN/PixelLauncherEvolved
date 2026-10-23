package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.animation.TimeInterpolator

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * Every number widget stacks move by, in one place.
 *
 * The swipe settles on a spring (Material 3 Expressive's default spatial one,
 * stiffness 700, damping ratio 0.9: quick, no visible bounce) run from the
 * finger's own speed; the smaller effects, which nothing drags, run on the
 * emphasized curves for a fixed time. Every one of them follows the system's
 * animator duration scale, and none runs with animations off.
 */
internal object StackMotion {

    const val SWIPE_STIFFNESS = 700f
    const val SWIPE_DAMPING_RATIO = 0.9f

    /** How much smaller a widget is when fully out of the stack, for depth. */
    const val SWIPE_DEPTH = 0.03f

    /** A widget held over another for this long is offered the stack, so a drag passing over does not. */
    const val HOVER_DWELL_MS = 150L
    const val HOVER_MS = 200L
    const val RELEASE_MS = 300L

    /** The widget a stack just took in, settling from a little smaller. */
    const val JOIN_MS = 450L

    const val INDICATOR_FADE_MS = 250L
    const val INDICATOR_REST_DELAY_MS = 900L

    /** Smart rotate's change: a cross-fade, quieter than a swipe. */
    const val ROTATE_FADE_MS = 280L

    val SETTLE: TimeInterpolator = ExpressiveMotion.spatialSpring(JOIN_MS)
    val ENTER: TimeInterpolator = ExpressiveMotion.EMPHASIZED_DECELERATE
}
