package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a Focus page leaves the screen and how the next one arrives.
 *
 * Kept apart from [FocusPageReveal] because the two answer different questions.
 * The reveal decides when a page may be swapped — the model has finished
 * binding, the window has focus, the home transition is over. This decides only
 * what that swap looks like, so the look can be changed, or replaced for a
 * test, without touching the sequencing that has to stay right.
 */
internal interface FocusRevealMotion {

    /** Puts the workspace into the hidden state at once, with no animation. */
    fun hide(view: View)

    /** Takes the outgoing page away, ending where [hide] would have left it. */
    fun exit(view: View): Animator

    /** Brings the newly-bound page in, ending where [settle] would leave it. */
    fun enter(view: View): Animator

    /** Restores the workspace's resting state, whatever it was left at. */
    fun settle(view: View)
}

/**
 * Material 3 Expressive motion for the page swap.
 *
 * The reveal this replaces was a circular wipe: the workspace was cut to
 * invisible the moment a Mode changed, stayed blank for as long as the model
 * took to bind, and was then uncovered from the middle over three quarters of a
 * second. Three things about that read as unfinished — the disappearance had no
 * motion at all, the wipe describes a shape the home screen does not have, and
 * the curve it ran on was a plain ease.
 *
 * Material 3 Expressive answers each of those. Content being replaced fades and
 * shrinks a little on an accelerating curve, so leaving looks deliberate.
 * Content arriving is not eased into place but sprung there, and it is the
 * spring — a real one, slightly underdamped, overshooting before it settles —
 * that gives the motion the liveliness the specification is named for. Opacity
 * is sprung by nothing: an overshoot past opaque is not something a screen can
 * show, so the fade keeps an emphasized curve.
 *
 * One caution. Scale and opacity on the workspace belong to the launcher too:
 * its own state transitions drive both when the app drawer or Overview opens.
 * Nothing here holds them for longer than [ENTER_SCALE_DURATION_MS], every path
 * out of this class ends at the resting values, and the hidden scale is close
 * enough to one that a frame caught in between does not look broken. That is
 * the whole of the defence, and it is deliberate: the alternative is animating
 * a copy of the workspace, which costs a snapshot of every page to avoid a
 * collision measured in a few hundred milliseconds.
 */
internal class MaterialExpressiveMotion : FocusRevealMotion {

    override fun hide(view: View) {
        view.visibility = View.INVISIBLE
        view.alpha = 0f
        view.setScale(HIDDEN_SCALE)
    }

    override fun settle(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.setScale(1f)
    }

    /**
     * Starts from wherever the workspace is now rather than from opaque.
     *
     * A Mode that changes twice in quick succession interrupts this animation
     * with the next one, and reading the live value is what keeps the second
     * start from jumping back to full opacity first.
     */
    override fun exit(view: View): Animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
            view.scaleTo(HIDDEN_SCALE),
        )
        duration = EXIT_DURATION_MS
        interpolator = EMPHASIZED_ACCELERATE
        onCompleted { hide(view) }
    }

    override fun enter(view: View): Animator {
        view.visibility = View.VISIBLE

        val fade = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 1f).apply {
            duration = ENTER_FADE_DURATION_MS
            interpolator = EMPHASIZED_DECELERATE
        }
        val grow = view.scaleTo(1f).apply {
            duration = ENTER_SCALE_DURATION_MS
            interpolator = SPATIAL_SPRING
        }

        return AnimatorSet().apply {
            playTogether(fade, grow)
            onCompleted { settle(view) }
        }
    }

    private fun View.setScale(value: Float) {
        scaleX = value
        scaleY = value
    }

    /** One animator for both axes, so a page can never be scaled unevenly. */
    private fun View.scaleTo(target: Float): ValueAnimator =
        ValueAnimator.ofFloat(scaleX, target).apply {
            addUpdateListener { setScale(it.animatedValue as Float) }
        }

    /**
     * Runs [action] only when the animation was allowed to finish.
     *
     * A cancelled animation is one the next Mode change has taken over, and
     * landing on the end state there would undo what that change has just
     * asked for.
     */
    private fun Animator.onCompleted(action: () -> Unit) {
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) action()
            }
        })
    }

    private companion object {

        /**
         * How small the workspace goes while it is away.
         *
         * Far enough for the return to be a movement rather than a fade, near
         * enough that a frame the launcher's own state transition happens to
         * catch mid-swap does not look wrong.
         */
        const val HIDDEN_SCALE = 0.94f

        const val EXIT_DURATION_MS = 150L
        const val ENTER_FADE_DURATION_MS = 250L
        const val ENTER_SCALE_DURATION_MS = 400L

        /** Material 3's emphasized accelerate, for content being taken away. */
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

        /** Material 3's emphasized decelerate, for content arriving. */
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

        /**
         * Material 3 Expressive's fast spatial spring.
         *
         * The damping ratio and stiffness are the specification's own numbers
         * for movement that should feel quick and alive, rather than the calmer
         * default meant for things that merely reposition themselves.
         */
        val SPATIAL_SPRING = SpringInterpolator(
            dampingRatio = 0.6f,
            stiffness = 800f,
            durationMs = ENTER_SCALE_DURATION_MS,
        )
    }
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
