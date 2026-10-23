package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * The dots beside every stack on the home screen, which tell it apart from a single widget.
 *
 * One column of small dots per stack, in the page margin just past the stack's
 * trailing edge, so nothing is drawn over the widget itself; when the stack
 * reaches the page edge and there is no margin, the column goes just inside it.
 * At rest the dots are quiet ([REST]); while the stack is swiped they come up
 * to full strength and the one for the widget on show, drawn as a short pill
 * as the launcher's page indicator draws its own, slides with the finger
 * towards the next. Just after a widget joins a stack they pulse once.
 *
 * Drawn in the overlay of the workspace, which pages scroll in, at the stack's
 * page position read off at each draw, so the dots move with the page and can
 * sit in the margin past the page's own bounds (drawn in the page's own overlay
 * they were cut to it and landed on the widget, seen on the device). A stack
 * moved within its page is followed without being told; one moved off it draws
 * nothing until it is attached again from its new page. Pages scaled for a drag
 * hide the dots, and a fading page fades them.
 */
internal class StackIndicators {

    private val all = ArrayList<StackIndicator>()

    /** Shows dots beside [anchor], the widget a stack has on show, replacing any for the same stack. */
    fun attach(anchor: View, stack: StackGraph.Stack, host: StackHost) {
        val container = anchor.parent as? ViewGroup ?: return
        val page = container.parent as? ViewGroup ?: return
        val workspace = page.parent as? ViewGroup ?: return
        prune(host)
        val indicator = all.firstOrNull { it.anchor === anchor || it.isFor(stack) } ?: StackIndicator().also { all += it }
        indicator.bind(anchor, container, page, workspace, stack)
    }

    /** Every set of dots at its resting strength again, after the setting changed. */
    fun restAll() {
        for (indicator in all) indicator.restNow()
    }

    fun find(anchor: View): StackIndicator? {
        for (i in all.indices) if (all[i].anchor === anchor) return all[i]
        return null
    }

    /** Takes every dot off every page, for a new workspace. */
    fun clear() {
        for (indicator in all) indicator.detach()
        all.clear()
    }

    /** Drops dots whose widget is no longer on a page or no longer on show in a stack. */
    private fun prune(host: StackHost) {
        val it = all.iterator()
        while (it.hasNext()) {
            val indicator = it.next()
            val anchor = indicator.anchor
            val tag = anchor?.tag
            if (anchor == null || anchor.parent == null || tag == null || !host.isWidget(tag) || !WidgetStacks.isAnchor(host.id.getInt(tag))) {
                indicator.detach()
                it.remove()
            }
        }
    }
}

internal class StackIndicator : Drawable() {

    var anchor: View? = null
        private set
    private var container: ViewGroup? = null
    private var page: ViewGroup? = null
    private var workspace: ViewGroup? = null
    private var count = 0
    private var shown = 0
    private var progress = 0f
    private var direction = 1
    private var strength = restStrength()
    private var animator: ValueAnimator? = null
    private var memberIds = IntArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = RectF()

    fun isFor(stack: StackGraph.Stack): Boolean {
        for (id in memberIds) if (stack.indexOf(id) >= 0) return true
        return false
    }

    fun bind(anchor: View, container: ViewGroup, page: ViewGroup, workspace: ViewGroup, stack: StackGraph.Stack) {
        if (this.workspace !== workspace) {
            this.workspace?.overlay?.remove(this)
            workspace.overlay.add(this)
            this.workspace = workspace
        }
        this.page = page
        this.anchor = anchor
        this.container = container
        memberIds = stack.members.copyOf()
        count = stack.members.size
        shown = stack.shown
        progress = 0f
        // The workspace's content is as wide as all its pages; scrolling moves the canvas, not the bounds.
        setBounds(Int.MIN_VALUE / 2, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
        val density = page.resources.displayMetrics.density
        paint.setShadowLayer(SHADOW_DP * density, 0f, 0f, SHADOW)
        invalidateSelf()
    }

    fun detach() {
        animator?.cancel()
        workspace?.overlay?.remove(this)
        workspace = null
        page = null
        anchor = null
        container = null
    }

    /** While swiping: [value] of the way from the widget on show to the next one in [towards]. */
    fun swipe(value: Float, towards: Int) {
        animator?.cancel()
        strength = 1f
        progress = value
        direction = towards
        invalidateSelf()
    }

    /** After a swipe: back to how the dots rest, a moment later. */
    fun rest() = fade(to = restStrength(), delay = StackMotion.INDICATOR_REST_DELAY_MS)

    /** A widget just joined: up to full strength and back. */
    fun pulse() {
        strength = restStrength()
        fade(to = 1f, delay = 0L) { fade(to = restStrength(), delay = StackMotion.INDICATOR_REST_DELAY_MS) }
    }

    /** Applies a change of the Stack dots setting. */
    fun restNow() {
        animator?.cancel()
        strength = restStrength()
        invalidateSelf()
    }

    /** Quiet (the default), full, or gone at rest, as the Stack dots setting says. */
    private fun restStrength(): Float = when (WidgetStacks.indicatorMode) {
        MODE_ALWAYS -> 1f
        MODE_SWIPING -> 0f
        else -> REST
    }

    private fun fade(to: Float, delay: Long, then: (() -> Unit)? = null) {
        animator?.cancel()
        val from = strength
        val run = ValueAnimator.ofFloat(0f, 1f)
        run.startDelay = delay
        run.duration = StackMotion.INDICATOR_FADE_MS
        run.interpolator = ExpressiveMotion.EMPHASIZED_DECELERATE
        run.addUpdateListener {
            strength = from + (to - from) * it.animatedFraction
            if (it.animatedFraction >= 1f) progress = 0f
            invalidateSelf()
        }
        if (then != null) run.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = then()
        })
        animator = run
        run.start()
    }

    override fun draw(canvas: Canvas) {
        val anchor = anchor ?: return
        val container = container ?: return
        val page = page ?: return
        val workspace = workspace ?: return
        if (count < 2 || strength <= 0f || anchor.parent !== container || page.parent !== workspace) return
        if (page.scaleX != 1f || page.alpha <= 0f) return
        val density = page.resources.displayMetrics.density
        val dot = DOT_DP * density
        val gap = GAP_DP * density
        val length = count * dot + (count - 1) * gap + (PILL - 1f) * dot
        val fade = page.alpha
        // Where the pill is: the dot on show, moving towards the next with the finger.
        val at = (shown + direction * progress).coerceIn(0f, (count - 1).toFloat())
        val left = page.left + container.left + anchor.left
        val right = page.left + container.left + anchor.right
        val sideways = WidgetStacks.sideways
        // Up and down: a column in the margin past the trailing edge (just inside it when
        // there is none). Sideways: a row just under the stack, as a page indicator sits.
        var fixed = if (sideways) page.top + container.top + anchor.bottom + MARGIN_DP * density + dot / 2
        else right + MARGIN_DP * density + dot / 2
        if (!sideways && fixed + dot > page.right + workspace.paddingRight) fixed = right - MARGIN_DP * density - dot / 2
        val first = if (sideways) (left + right) / 2f - length / 2
        else page.top + container.top + (anchor.top + anchor.bottom) / 2f - length / 2
        paint.color = DOT
        paint.alpha = (DIM_ALPHA * strength * fade).toInt()
        for (i in 0 until count) {
            val along = first + i * (dot + gap) + (if (i > at) (PILL - 1f) * dot else 0f) + dot / 2
            if (sideways) canvas.drawCircle(along, fixed, dot / 2, paint) else canvas.drawCircle(fixed, along, dot / 2, paint)
        }
        val pillStart = first + at * (dot + gap)
        if (sideways) pill.set(pillStart, fixed - dot / 2, pillStart + PILL * dot, fixed + dot / 2)
        else pill.set(fixed - dot / 2, pillStart, fixed + dot / 2, pillStart + PILL * dot)
        paint.alpha = (255 * strength * fade).toInt()
        canvas.drawRoundRect(pill, dot / 2, dot / 2, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val DOT_DP = 5f
        const val GAP_DP = 4f
        const val MARGIN_DP = 6f
        const val SHADOW_DP = 1.5f
        const val PILL = 2.2f
        const val REST = 0.55f
        const val DOT = 0xFFFFFFFF.toInt()
        const val SHADOW = 0x66000000
        const val DIM_ALPHA = 140
        const val MODE_ALWAYS = 1
        const val MODE_SWIPING = 2
    }
}
