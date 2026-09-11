package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

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
        interpolator = ExpressiveMotion.EMPHASIZED_ACCELERATE
        onCompleted { hide(view) }
    }

    override fun enter(view: View): Animator {
        view.visibility = View.VISIBLE

        val fade = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 1f).apply {
            duration = ENTER_FADE_DURATION_MS
            interpolator = ExpressiveMotion.EMPHASIZED_DECELERATE
        }
        val grow = view.scaleTo(1f).apply {
            duration = ENTER_SCALE_DURATION_MS
            interpolator = ExpressiveMotion.spatialSpring(ENTER_SCALE_DURATION_MS)
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
    }
}
