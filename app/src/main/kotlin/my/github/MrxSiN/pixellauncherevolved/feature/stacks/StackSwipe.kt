package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.graphics.Outline
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityManager

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Swiping through a stack: up and down, or sideways when the person chose it.
 *
 * The widget keeps every touch until the finger has clearly moved along the
 * stack's axis past the touch slop, and keeps it for good when a view under the
 * finger can still scroll that way, so a list widget scrolls as it always did.
 * Only then is the gesture taken, by intercepting: the widget gets a cancel, as
 * from any scrolling parent, and every parent is held off from then on, so the
 * home screen's paging cannot take a slightly diagonal swipe halfway. Up and
 * down, movement mostly sideways before that is the home screen's paging;
 * sideways, the home screen is held off from the first touch. A stack does not
 * wrap from its last widget to its first, either way: past either end it gives
 * a little and springs back, and the home screen pages from outside the stack.
 *
 * The widgets either side of the one on show are kept inflated and attached,
 * fully transparent, in the overlay of the view holding the page's widgets, laid
 * over the stack's cells ([park]). Attached, a list widget connects to its data,
 * so the widget sliding in shows its content; in the overlay rather than among
 * the page's items, the launcher's reorder, occupancy and item lookups never see
 * them. Transparent rather than invisible: a widget that is not visible gets no
 * content, and one at alpha 0 is not drawn. The overlay never lays them out
 * again, so each is laid out again, its whole tree, when it is brought in.
 *
 * The widget on show follows the finger and the next one comes in behind it,
 * both clipped by a rounded outline to the stack's bounds and a little smaller
 * the further they are from settled. Let go, a spring takes over from the
 * finger's own speed: the change is kept when the swipe passed a third of the
 * stack or was flung its way, and a touch while it settles catches it where it
 * is. With animations off the stack changes at once.
 *
 * One gesture at a time; every field is touched on the UI thread only. Nothing
 * is allocated while the finger moves or the spring runs.
 */
internal class StackSwipe(
    private val host: StackHost,
    private val logger: Logger,
    private val indicators: StackIndicators,
) {

    private var view: View? = null
    private var tracking = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var start = 0f
    private var base = 0f
    private var slop = 0

    /** The speed past which a release keeps the change, read with [slop] at the first touch. */
    private var minFling = 0
    private var size = 0
    private var horizontal = false
    private var nextSign = -1
    private var offset = 0f
    private var pastThreshold = false
    private var stack: StackGraph.Stack? = null
    private var neighbor: View? = null
    private var neighborIndex = -1
    private var velocity: VelocityTracker? = null
    private val outgoingClip = StackClip()
    private val incomingClip = StackClip()
    private var outgoingProvider: ViewOutlineProvider? = null
    private var incomingProvider: ViewOutlineProvider? = null
    private var outgoingClipped = false
    private var incomingClipped = false
    private var indicator: StackIndicator? = null
    private var radius = -1f

    // The spring that settles a swipe ([settle]).
    private val ticker = TimeAnimator()
    private var springing = false
    private var springTarget = 0f
    private var springVelocity = 0f
    private var springScale = 1f
    private var announce = false

    init {
        ticker.setTimeListener { _, _, deltaMs -> step(deltaMs) }
    }

    /** Member views not on the page, by item id: parked in a page's overlay, or detached. */
    private val prepared = SparseArray<View>()

    /** For each parked view in [prepared], the view whose overlay holds it. */
    private val parkedIn = SparseArray<ViewGroup>()

    /** Every view is new after a workspace bind: nothing parked or drawn stays. */
    fun forgetViews() {
        if (springing) {
            springing = false
            ticker.end()
        }
        dragging = false
        neighbor = null
        view = null
        prepared.clear()
        parkedIn.clear()
        indicators.clear()
    }

    /** Keeps [view], just taken off the home screen, for when member [id] is shown again. */
    fun keep(id: Int, view: View) {
        prepared.put(id, view)
        parkedIn.remove(id)
    }

    /** Member [id]'s view, while it is kept off the page. */
    fun parked(id: Int): View? = prepared.get(id)

    /** Forgets member [id]'s view, which has left every stack. */
    fun drop(id: Int) {
        unpark(id)
        prepared.remove(id)
    }

    /**
     * A stack's widget on show has just been put on a page (bound, moved, swiped to):
     * dots beside it, and its neighbours parked under it.
     */
    fun onAnchorAdded(anchor: View) {
        if (anchor.parent == null || !isAnchor(anchor)) return
        val id = host.id.getInt(anchor.tag)
        val stack = WidgetStacks.graph.stackOf(id)?.takeIf { it.shownId == id } ?: return
        indicators.attach(anchor, stack, host)
        runCatching { parkAround(anchor, stack) }
            .onFailure { logger.warn("Widget stacks: the widgets next in the stack could not be prepared", it) }
    }

    /** After a widget joined [anchor]'s stack: dots up to full and back, and a small settle of the stack. */
    fun stacked(anchor: View) {
        onAnchorAdded(anchor)
        indicators.find(anchor)?.pulse()
        anchor.scaleX = JOINED_SCALE
        anchor.scaleY = JOINED_SCALE
        anchor.animate().scaleX(1f).scaleY(1f).setDuration(StackMotion.JOIN_MS).setInterpolator(StackMotion.SETTLE).start()
    }

    /** Whether [anchor] is busy moving, so nothing else should change it now. */
    fun isBusy(anchor: View): Boolean = view === anchor && (dragging || springing || fading)

    /** From the widget's `onInterceptTouchEvent`; true takes the gesture away from the widget. */
    fun intercept(widget: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (springing && widget === view) return catchSpring(event)
                if (springing || fading || !isAnchor(widget)) {
                    tracking = false
                    return false
                }
                view = widget
                tracking = true
                dragging = false
                downX = event.rawX
                downY = event.rawY
                horizontal = WidgetStacks.sideways
                if (slop == 0) {
                    val config = ViewConfiguration.get(widget.context)
                    slop = config.scaledTouchSlop
                    minFling = config.scaledMinimumFlingVelocity * FLING_FACTOR
                }
                track(event)
                // Up and down, only the drag layer's swipes are held off, as for a scrollable
                // widget; sideways, the home screen's paging too, until the stack lets it go.
                if (horizontal) widget.parent?.requestDisallowInterceptTouchEvent(true)
                else dragLayer(widget)?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking || widget !== view) return false
                track(event)
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val along = if (horizontal) dx else dy
                val across = if (horizontal) dy else dx
                if (abs(across) > slop && abs(across) > abs(along)) {
                    tracking = false
                    if (horizontal) {
                        // A vertical swipe on a sideways stack: the launcher's own, unless a list takes it.
                        val content = if (dy < 0) 1 else -1
                        if (!canScroll(widget, event.rawX.toInt(), event.rawY.toInt(), content, vertical = true)) {
                            widget.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    } else {
                        dragLayer(widget)?.requestDisallowInterceptTouchEvent(false)
                    }
                    return false
                }
                if (abs(along) <= slop || abs(along) <= abs(across)) return false
                tracking = false
                val content = if (along < 0) 1 else -1
                if (canScroll(widget, event.rawX.toInt(), event.rawY.toInt(), content, vertical = !horizontal)) return false
                val claimed = begin(widget, if (horizontal) event.rawX else event.rawY)
                if (!claimed && horizontal) widget.parent?.requestDisallowInterceptTouchEvent(false)
                return claimed
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }

    /** From the widget's `onTouchEvent`; true when the stack handled it. */
    fun touch(widget: View, event: MotionEvent): Boolean {
        if (!dragging || widget !== view) return false
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                follow(base + (if (horizontal) event.rawX else event.rawY) - start)
                val past = neighbor != null && abs(offset) > size * COMMIT_SHARE
                if (past != pastThreshold) {
                    pastThreshold = past
                    widget.performHapticFeedback(
                        if (past) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
                    )
                }
            }
            MotionEvent.ACTION_UP -> {
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val speed = if (horizontal) tracker?.xVelocity ?: 0f else tracker?.yVelocity ?: 0f
                val flung = abs(speed) > minFling && (speed < 0) == (offset < 0)
                val keep = neighbor != null && offset != 0f && (abs(offset) > size * COMMIT_SHARE || flung)
                if (keep && !pastThreshold) widget.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                settle(if (keep) (if (offset < 0) -size else size).toFloat() else 0f, speed, announce = false)
            }
            MotionEvent.ACTION_CANCEL -> settle(0f, 0f, announce = false)
        }
        return true
    }

    /**
     * Moves [widget]'s stack one widget forwards ([by] 1) or back (-1), as a swipe
     * would, for accessibility and Smart rotate; false when there is none that way.
     */
    fun stepBy(widget: View, by: Int, announce: Boolean): Boolean {
        if (dragging || springing || !isAnchor(widget)) return false
        val id = host.id.getInt(widget.tag)
        val current = WidgetStacks.graph.stackOf(id)?.takeIf { it.shownId == id } ?: return false
        val index = current.shown + by
        if (index !in current.members.indices) return false
        horizontal = WidgetStacks.sideways
        nextSign = nextSign(widget)
        if (!prepare(widget, current)) return false
        val direction = if (by > 0) nextSign else -nextSign
        if (!bring(widget, index)) {
            finishGesture(widget)
            return false
        }
        offset = 0f
        settle((direction * size).toFloat(), 0f, announce)
        widget.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        return true
    }

    /**
     * Brings member [index] forward with a cross-fade, quieter than a swipe, for Smart
     * rotate and for going back to the first widget; at once with animations off.
     */
    fun fadeTo(widget: View, index: Int): Boolean {
        if (dragging || springing || fading || !isAnchor(widget)) return false
        val id = host.id.getInt(widget.tag)
        val current = WidgetStacks.graph.stackOf(id)?.takeIf { it.shownId == id } ?: return false
        if (index == current.shown || index !in current.members.indices) return false
        if (durationScale() == 0f) return showNow(widget, current, index)
        val next = park(widget, current.members[index]) ?: return false
        stack = current
        view = widget
        neighbor = next
        neighborIndex = index
        fading = true
        automatic = true
        next.alpha = 0f
        next.animate().alpha(1f).setDuration(StackMotion.ROTATE_FADE_MS).setInterpolator(StackMotion.ENTER).withEndAction {
            fading = false
            if (neighbor === next && runCatching { commit(widget, next) }.getOrDefault(false)) return@withEndAction
            next.alpha = 0f
            finishGesture(widget)
        }.start()
        return true
    }

    private var fading = false

    /** The change under way was the launcher's, not the person's, so Smart rotate's grace is not restarted. */
    private var automatic = false

    /** Shows member [index] of [stack] at once, for Edit stack and when home comes back. */
    fun showNow(widget: View, stack: StackGraph.Stack, index: Int): Boolean {
        if (dragging || springing || index == stack.shown) return false
        host.launcher(widget)?.let(host::closeFloating)
        this.stack = stack
        val next = park(widget, stack.members[index]) ?: return false
        next.alpha = 1f
        neighbor = next
        neighborIndex = index
        view = widget
        return commit(widget, next)
    }

    private fun begin(widget: View, at: Float): Boolean {
        val id = host.id.getInt(widget.tag)
        val current = WidgetStacks.graph.stackOf(id)?.takeIf { it.shownId == id } ?: return false
        nextSign = nextSign(widget)
        if (!prepare(widget, current)) return false
        dragging = true
        widget.cancelLongPress()
        // The swipe is the stack's now, at either end too: no parent may take it (the home
        // screen's paging grabbed slightly diagonal up-and-down swipes mid-drag).
        widget.parent?.requestDisallowInterceptTouchEvent(true)
        start = at
        base = 0f
        follow(0f)
        return true
    }

    private fun prepare(widget: View, current: StackGraph.Stack): Boolean {
        stack = current
        view = widget
        size = if (horizontal) widget.width else widget.height
        if (size == 0) return false
        offset = 0f
        pastThreshold = false
        neighbor = null
        neighborIndex = -1
        val corner = cornerRadius(widget)
        outgoingClip.radius = corner
        incomingClip.radius = corner
        outgoingProvider = widget.outlineProvider
        outgoingClipped = widget.clipToOutline
        widget.outlineProvider = outgoingClip
        widget.clipToOutline = true
        indicator = indicators.find(widget)
        return true
    }

    /** The member a swipe [along] the axis brings in. */
    private fun indexFor(current: StackGraph.Stack, along: Float): Int =
        if ((along < 0) == (nextSign < 0)) current.shown + 1 else current.shown - 1

    /** Where the widget on show is [raw] px along the axis, from the finger: picks the neighbour that way. */
    private fun follow(raw: Float) {
        val widget = view ?: return
        val current = stack ?: return
        val wanted = if (raw == 0f) -1 else indexFor(current, raw)
        val want = if (wanted >= 0 && wanted < current.members.size) wanted else -1
        if (want != neighborIndex) {
            releaseNeighbor()
            if (want >= 0 && !bring(widget, want)) return
        }
        place(if (neighbor == null) rubber(raw) else raw.coerceIn(-size.toFloat(), size.toFloat()))
    }

    /** Takes member [index] out of the overlay's shadow and under the stack. */
    private fun bring(widget: View, index: Int): Boolean {
        val current = stack ?: return false
        val next = runCatching { park(widget, current.members[index]) }
            .onFailure { logger.warn("Widget stacks: the next widget could not be shown", it) }
            .getOrNull() ?: return false
        incomingProvider = next.outlineProvider
        incomingClipped = next.clipToOutline
        next.outlineProvider = incomingClip
        next.clipToOutline = true
        neighbor = next
        neighborIndex = index
        next.alpha = 1f
        return true
    }

    private fun releaseNeighbor() {
        val next = neighbor ?: return
        reset(next, incomingProvider, incomingClipped)
        next.alpha = 0f
        neighbor = null
        neighborIndex = -1
    }

    /** Past an end: the stack gives a little and no more. */
    private fun rubber(raw: Float): Float {
        val limit = size * RUBBER_SHARE
        return limit * raw / (abs(raw) + limit)
    }

    /** Puts the widget on show [at] px along the axis and its neighbour beside it. */
    private fun place(at: Float) {
        val widget = view ?: return
        offset = at
        val fraction = (abs(at) / size).coerceIn(0f, 1f)
        move(widget, at, 1f - DEPTH * fraction, outgoingClip)
        val next = neighbor
        if (next != null) {
            move(next, if (at < 0) at + size else at - size, 1f - DEPTH * (1f - fraction), incomingClip)
            indicator?.swipe(fraction, if (neighborIndex > (stack?.shown ?: 0)) 1 else -1)
        } else {
            indicator?.swipe(0f, 1)
        }
    }

    private fun move(view: View, at: Float, scale: Float, clip: StackClip) {
        if (horizontal) view.translationX = at else view.translationY = at
        view.scaleX = scale
        view.scaleY = scale
        // The stack's bounds in the view's own coordinates, whatever its translation.
        val t = at.toInt()
        if (horizontal) {
            clip.left = maxOf(0, -t)
            clip.right = maxOf(clip.left, minOf(view.width, size - t))
            clip.top = 0
            clip.bottom = view.height
        } else {
            clip.top = maxOf(0, -t)
            clip.bottom = maxOf(clip.top, minOf(view.height, size - t))
            clip.left = 0
            clip.right = view.width
        }
        view.invalidateOutline()
    }

    private fun catchSpring(event: MotionEvent): Boolean {
        springing = false
        ticker.end()
        dragging = true
        tracking = false
        downX = event.rawX
        downY = event.rawY
        start = if (horizontal) event.rawX else event.rawY
        base = offset
        track(event)
        view?.parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    /** Hands the stack to a spring heading for [target] px, from the finger's [speed] px/s. */
    private fun settle(target: Float, speed: Float, announce: Boolean) {
        dragging = false
        springTarget = target
        springVelocity = speed
        this.announce = announce
        val scale = durationScale()
        if (scale == 0f) {
            place(target)
            land()
            return
        }
        springScale = scale
        springing = true
        ticker.start()
    }

    /** One frame of the spring, in small steps for stability. */
    private fun step(deltaMs: Long) {
        if (!springing) return
        val seconds = (deltaMs / 1000f / springScale).coerceAtMost(MAX_FRAME_S)
        val steps = ceil(seconds / SUBSTEP_S).toInt().coerceAtLeast(1)
        val h = seconds / steps
        var x = offset
        var v = springVelocity
        repeat(steps) {
            val a = -STIFFNESS * (x - springTarget) - DAMPING * v
            v += a * h
            x += v * h
        }
        springVelocity = v
        if (abs(x - springTarget) < REST_PX && abs(v) < REST_SPEED) {
            place(springTarget)
            springing = false
            ticker.end()
            land()
        } else {
            place(x)
        }
    }

    /** The spring has come to rest: keep the change, or put everything back. */
    private fun land() {
        val widget = view ?: return
        val next = neighbor
        if (springTarget != 0f && next != null) {
            val kept = runCatching { commit(widget, next) }
                .onFailure { logger.warn("Widget stacks: the swipe could not be kept", it) }
                .getOrDefault(false)
            if (!kept) cancel(widget)
        } else {
            cancel(widget)
        }
        indicator?.rest()
        indicator = null
    }

    /** Swaps the widget on show for [next] in the database and on the page; the one leaving is parked. */
    private fun commit(widget: View, next: View): Boolean {
        val launcher = host.launcher(widget) ?: return false
        val workspace = host.workspace(launcher) ?: return false
        val stack = stack ?: return false
        val target = neighborIndex
        val shown = widget.tag
        val incoming = next.tag
        val container = widget.parent as? ViewGroup ?: return false
        val cellLayout = container.parent as? ViewGroup ?: return false
        val screen = host.screenId.getInt(incoming)
        val x = host.cellX.getInt(incoming)
        val y = host.cellY.getInt(incoming)
        val incomingWidth = host.spanX.getInt(incoming)
        val incomingHeight = host.spanY.getInt(incoming)
        val anchorScreen = host.screenId.getInt(shown)
        val anchorX = host.cellX.getInt(shown)
        val anchorY = host.cellY.getInt(shown)
        val spanWidth = host.spanX.getInt(shown)
        val spanHeight = host.spanY.getInt(shown)

        reset(widget, outgoingProvider, outgoingClipped)
        if (next.outlineProvider is StackClip) reset(next, incomingProvider, incomingClipped) else reset(next, next.outlineProvider, next.clipToOutline)
        val incomingId = host.id.getInt(incoming)
        unpark(incomingId)
        prepared.remove(incomingId)
        next.alpha = 1f
        cellLayout.removeView(widget)
        // Placed at the stack's cells before either item is written, while the one leaving still names them.
        host.addAt(workspace, next, shown)
        // The incoming widget takes the stack's size (after a resize of the stack) and is told so.
        host.move(launcher, shown, screen, x, y, incomingWidth.coerceAtLeast(1), incomingHeight.coerceAtLeast(1))
        host.move(launcher, incoming, anchorScreen, anchorX, anchorY, spanWidth, spanHeight)
        if (incomingWidth != spanWidth || incomingHeight != spanHeight) host.resized(next, spanWidth, spanHeight)

        val leaving = host.id.getInt(shown)
        widget.alpha = 0f
        container.overlay.add(widget)
        prepared.put(leaving, widget)
        parkedIn.put(leaving, container)

        val anchorsNow = WidgetStacks.anchors.copyOf().also { ids ->
            val at = ids.indexOf(stack.shownId)
            if (at >= 0) ids[at] = stack.members[target]
        }
        val shownNow = stack.showing(target)
        WidgetStacks.store(WidgetStacks.graph.replace(stack, shownNow), WidgetStacks.hiddenScreens, anchorsNow)
        if (!automatic) WidgetStacks.chosen(shownNow)
        automatic = false
        neighbor = null
        neighborIndex = -1
        view = null
        // The page hook saw [next] arrive before it was the stack's widget on show, so its
        // dots and neighbours are set up here; the dots stay at full strength until they rest.
        onAnchorAdded(next)
        indicator = indicators.find(next)?.apply { swipe(0f, 1) }
        if (announce) announceShown(next, shownNow)
        return true
    }

    private fun cancel(widget: View) {
        reset(widget, outgoingProvider, outgoingClipped)
        releaseNeighbor()
        finishGesture(widget)
    }

    private fun finishGesture(widget: View) {
        if (widget.outlineProvider is StackClip) reset(widget, outgoingProvider, outgoingClipped)
        neighbor = null
        neighborIndex = -1
        view = null
    }

    private fun reset(view: View, provider: ViewOutlineProvider?, clipped: Boolean) {
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        if (view.outlineProvider is StackClip) {
            view.outlineProvider = provider
            view.clipToOutline = clipped
        }
    }

    /** Says which widget is now on show, for TalkBack. */
    private fun announceShown(widget: View, stack: StackGraph.Stack) {
        val manager = widget.context.getSystemService(AccessibilityManager::class.java)
        if (manager?.isEnabled != true) return
        val label = host.label(widget) ?: return
        widget.announceForAccessibility(WidgetStacks.describe(label, stack.shown + 1, stack.members.size))
    }

    private fun parkAround(anchor: View, stack: StackGraph.Stack) {
        val next = stack.members.getOrNull(stack.shown + 1)
        val previous = stack.members.getOrNull(stack.shown - 1)
        // Only the two a swipe can bring in stay attached.
        for (i in prepared.size() - 1 downTo 0) {
            val id = prepared.keyAt(i)
            if (id != next && id != previous && stack.indexOf(id) >= 0) unpark(id)
        }
        if (next != null) park(anchor, next)
        if (previous != null) park(anchor, previous)
    }

    /** The view for member [id], attached and laid over [anchor]'s cells in its page's overlay, transparent. */
    private fun park(anchor: View, id: Int): View? {
        val container = anchor.parent as? ViewGroup ?: return null
        val launcher = host.launcher(anchor) ?: return null
        val item = host.modelItem(launcher, id) ?: return null
        if (!host.isWidget(item)) return null
        val cached = prepared.get(id)
        val view = if (cached != null && cached.tag === item) cached else host.inflate(launcher, item) ?: return null
        if (parkedIn.get(id) !== container || view.parent == null) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            container.overlay.add(view)
            parkedIn.put(id, container)
        }
        prepared.put(id, view)
        lay(view, anchor)
        return view
    }

    private fun unpark(id: Int) {
        val view = prepared.get(id) ?: return
        parkedIn.get(id)?.overlay?.remove(view)
        parkedIn.remove(id)
    }

    /** Lays [view] out over [anchor]'s cells, its whole tree; the overlay holding it never does. */
    private fun lay(view: View, anchor: View) {
        val width = anchor.width
        val height = anchor.height
        if (width == 0 || height == 0) return
        forceLayout(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(anchor.left, anchor.top, anchor.right, anchor.bottom)
    }

    private fun forceLayout(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (i in 0 until view.childCount) forceLayout(view.getChildAt(i))
    }

    /** Which way along the axis a finger moves to bring in the next widget: up, left, or right in RTL. */
    private fun nextSign(widget: View): Int =
        if (horizontal && widget.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1 else -1

    /** The system's widget corner radius (`system_app_widget_background_radius`), read once. */
    private fun cornerRadius(widget: View): Float {
        if (radius < 0f) {
            radius = runCatching { widget.resources.getDimension(android.R.dimen.system_app_widget_background_radius) }
                .getOrDefault(0f)
        }
        return radius
    }

    private fun isAnchor(widget: View): Boolean {
        val tag = widget.tag ?: return false
        return host.id.declaringClass.isInstance(tag) && WidgetStacks.isAnchor(host.id.getInt(tag))
    }

    private fun track(event: MotionEvent) {
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) tracker.clear()
        // Raw coordinates, because the widget itself moves under the finger.
        val dx = event.rawX - event.x
        val dy = event.rawY - event.y
        event.offsetLocation(dx, dy)
        tracker.addMovement(event)
        event.offsetLocation(-dx, -dy)
    }

    private fun dragLayer(widget: View): ViewGroup? {
        var parent = widget.parent
        while (parent is ViewGroup) {
            if (parent.javaClass.name.endsWith(DRAG_LAYER)) return parent
            parent = parent.parent
        }
        return null
    }

    /** Whether a view under the raw point inside [root] can still scroll in [direction] (1 forwards). */
    private fun canScroll(root: View, rawX: Int, rawY: Int, direction: Int, vertical: Boolean): Boolean {
        if (if (vertical) root.canScrollVertically(direction) else root.canScrollHorizontally(direction)) return true
        if (root !is ViewGroup) return false
        root.getLocationOnScreen(location)
        val x = rawX - location[0]
        val y = rawY - location[1]
        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (x < child.left || x >= child.right || y < child.top || y >= child.bottom) continue
            if (canScroll(child, rawX, rawY, direction, vertical)) return true
        }
        return false
    }

    private val location = IntArray(2)

    /** The system's animator duration scale; 0 with animations off. */
    private fun durationScale(): Float =
        if (!ValueAnimator.areAnimatorsEnabled()) 0f else ValueAnimator.getDurationScale()

    private companion object {
        const val COMMIT_SHARE = 1f / 3f
        const val FLING_FACTOR = 8
        const val DRAG_LAYER = "DragLayer"
        const val DEPTH = StackMotion.SWIPE_DEPTH
        const val RUBBER_SHARE = 0.12f
        const val JOINED_SCALE = 0.94f

        // Material 3 Expressive's default spatial spring: stiffness 700, damping ratio 0.9.
        const val STIFFNESS = StackMotion.SWIPE_STIFFNESS
        val DAMPING = 2f * StackMotion.SWIPE_DAMPING_RATIO * sqrt(STIFFNESS)
        const val SUBSTEP_S = 0.004f
        const val MAX_FRAME_S = 0.05f
        const val REST_PX = 0.5f
        const val REST_SPEED = 30f
    }
}

/** A rounded rectangle inside a view, for clipping it while it slides. */
private class StackClip : ViewOutlineProvider() {
    @JvmField var left = 0
    @JvmField var top = 0
    @JvmField var right = 0
    @JvmField var bottom = 0
    @JvmField var radius = 0f

    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(left, top, right, bottom, radius)
    }
}
