package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.PathInterpolator

import java.lang.ref.WeakReference

import kotlin.math.hypot

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/** Reveals a newly-bound Focus page once the launcher is visible. */
internal class FocusPageReveal(private val logger: Logger) {

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
        view?.visibility = View.INVISIBLE
    }

    /** A failed reload must never leave the workspace hidden. */
    fun cancel() {
        generation++
        pending = false
        bound = false
        scheduled = false
        workspace?.get()?.visibility = View.VISIBLE
    }

    /** Called at the stable end of the workspace's complete-model bind. */
    fun onWorkspaceBound(view: View?) {
        if (view == null) return
        workspace = WeakReference(view)
        if (pending) {
            bound = true
            view.visibility = View.INVISIBLE
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
        view.visibility = View.VISIBLE
        animate(view)
    }

    private fun animate(view: View) {
        val width = view.width
        val height = view.height
        if (!view.isAttachedToWindow || width <= 0 || height <= 0) return

        running?.cancel()
        val centerX = width / 2
        val centerY = height / 2
        val radius = hypot(centerX.toFloat(), centerY.toFloat())
        val animator = ViewAnimationUtils.createCircularReveal(
            view,
            centerX,
            centerY,
            0f,
            radius,
        ).apply {
            duration = REVEAL_DURATION_MS
            interpolator = REVEAL_INTERPOLATOR
        }

        running = animator
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (running === animation) running = null
            }
        })
        animator.start()
        logger.info("Focus page reveal played")
    }

    private companion object {
        const val REVEAL_DURATION_MS = 750L
        const val HOME_TRANSITION_DELAY_MS = 400L
        val REVEAL_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
