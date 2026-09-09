package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Swaps one Focus page for another once the launcher is ready to be looked at.
 *
 * Only the sequencing is here. A Mode changes, the outgoing page is sent away,
 * the launcher rebinds its whole model, and the incoming page may not be shown
 * until three things are true at once: the bind has finished, the window has
 * focus, and any home transition the launcher was already playing is over.
 * Missing any one of them shows a half-built home screen.
 *
 * What the going and the coming look like is [FocusRevealMotion]'s business.
 */
internal class FocusPageReveal(
    private val logger: Logger,
    private val motion: FocusRevealMotion = MaterialExpressiveMotion(),
) {

    @Volatile
    private var pending = false

    @Volatile
    private var workspace: WeakReference<View>? = null

    private var generation = 0
    private var bound = false
    private var waitForHomeTransition = false
    private var scheduled = false
    private var running: Animator? = null

    /** Marks only an active-Mode transition; settings-only reloads stay instant. */
    fun request(waitForHomeTransition: Boolean) {
        generation++
        pending = true
        bound = false
        scheduled = false
        val view = workspace?.get()
        this.waitForHomeTransition = waitForHomeTransition || view?.hasWindowFocus() != true

        // Played rather than cut, because the model takes long enough to bind
        // that a page vanishing between two frames is the part that was seen.
        if (view != null) play(motion.exit(view))
    }

    /** A failed reload must never leave the workspace hidden. */
    fun cancel() {
        generation++
        pending = false
        bound = false
        scheduled = false
        running?.cancel()
        running = null
        workspace?.get()?.let(motion::settle)
    }

    /** Called at the stable end of the workspace's complete-model bind. */
    fun onWorkspaceBound(view: View?) {
        if (view == null) return

        // A recreated activity brings a new workspace, and the animation still
        // running belongs to the old one.
        if (workspace?.get() !== view) {
            running?.cancel()
            running = null
        }
        workspace = WeakReference(view)

        if (pending) {
            bound = true
            // Already hidden when the exit is still playing or has finished;
            // this covers the bind that arrives with no exit ever having run,
            // because the workspace did not exist when the Mode changed.
            if (running == null) motion.hide(view)
        }
        revealWhenReady(view)
    }

    /** A Mode changed behind the shade; reveal when Home becomes visible again. */
    fun onWindowFocused() {
        workspace?.get()?.let(::revealWhenReady)
    }

    private fun revealWhenReady(view: View) {
        if (!pending || !bound || !view.hasWindowFocus() || !view.isAttachedToWindow) return
        if (waitForHomeTransition) {
            scheduleAfterHomeTransition(view)
        } else {
            reveal(view)
        }
    }

    private fun scheduleAfterHomeTransition(view: View) {
        if (scheduled) return
        scheduled = true
        val request = generation

        view.postDelayed({
            if (request != generation) return@postDelayed
            scheduled = false
            if (view.hasWindowFocus() && view.isAttachedToWindow) reveal(view)
        }, HOME_TRANSITION_DELAY_MS)
    }

    private fun reveal(view: View) {
        if (!pending || !bound) return
        pending = false
        bound = false
        waitForHomeTransition = false

        // A workspace with no size cannot be animated into view, and leaving it
        // hidden because of that would be worse than showing it at once.
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            motion.settle(view)
            return
        }

        play(motion.enter(view))
        logger.info("Focus page reveal played")
    }

    /** One animation at a time: a newer Mode change always wins the workspace. */
    private fun play(animator: Animator) {
        running?.cancel()
        running = animator
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (running === animation) running = null
            }
        })
        animator.start()
    }

    private companion object {
        const val HOME_TRANSITION_DELAY_MS = 400L
    }
}
