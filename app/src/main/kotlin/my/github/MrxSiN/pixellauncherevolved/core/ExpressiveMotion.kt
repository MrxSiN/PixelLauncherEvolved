package my.github.MrxSiN.pixellauncherevolved.core

import android.animation.TimeInterpolator
import android.view.animation.PathInterpolator

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The curves Material 3 Expressive motion is written on.
 *
 * Kept here rather than beside any one animation because more than one thing
 * this module animates has to move the same way: a page swap on the home screen
 * and a row of buttons arriving in Overview are different animations of the
 * same system, and two copies of these numbers would be two things to keep in
 * agreement.
 */
internal object ExpressiveMotion {

    /** Material 3's emphasized accelerate, for content being taken away. */
    val EMPHASIZED_ACCELERATE: TimeInterpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** Material 3's emphasized decelerate, for content arriving. */
    val EMPHASIZED_DECELERATE: TimeInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /**
     * Material 3 Expressive's fast spatial spring, over a given run.
     *
     * The damping ratio and stiffness are the specification's own numbers for
     * movement that should feel quick and alive, rather than the calmer default
     * meant for things that merely reposition themselves. The duration belongs
     * to the caller because a spring written as a curve has to know how much
     * real time one pass of the animator covers.
     */
    fun spatialSpring(durationMs: Long): TimeInterpolator = SpringInterpolator(
        dampingRatio = 0.6f,
        stiffness = 800f,
        durationMs = durationMs,
    )
}

/**
 * A spring's journey to rest, written as a curve an animator can run on.
 *
 * Material 3 Expressive describes its spatial motion as springs rather than as
 * eases: a value is not walked to its destination, it is pulled there and
 * settles, overshooting slightly on the way. Android's animators want a
 * function of elapsed fraction, so the spring's closed-form step response is
 * evaluated here rather than integrated frame by frame. That keeps the feel of
 * a spring with no physics runtime to depend on, and leaves a curve a unit test
 * can check.
 *
 * @param dampingRatio how quickly the oscillation dies away. Below one, because
 * at one and above there is no overshoot left and no spring worth the name.
 * @param stiffness the spring constant for a unit mass, so the undamped
 * frequency is its square root. Higher is faster and tighter.
 * @param durationMs how much real time one full pass of the animator covers,
 * needed because the curve is written in seconds and an animator counts in
 * fractions.
 */
internal class SpringInterpolator(
    dampingRatio: Float,
    stiffness: Float,
    private val durationMs: Long,
) : TimeInterpolator {

    init {
        require(dampingRatio > 0f && dampingRatio < 1f) {
            "A spring that overshoots needs a damping ratio between 0 and 1, not $dampingRatio"
        }
        require(stiffness > 0f) { "A spring needs a positive stiffness, not $stiffness" }
    }

    private val damping = dampingRatio.toDouble()
    private val naturalFrequency = sqrt(stiffness.toDouble())
    private val dampedFrequency = naturalFrequency * sqrt(1.0 - damping * damping)

    override fun getInterpolation(input: Float): Float {
        val seconds = input.toDouble() * durationMs / MILLIS_PER_SECOND
        val decay = exp(-damping * naturalFrequency * seconds)
        val swing = cos(dampedFrequency * seconds) +
            damping * naturalFrequency / dampedFrequency * sin(dampedFrequency * seconds)

        return (1.0 - decay * swing).toFloat()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}
