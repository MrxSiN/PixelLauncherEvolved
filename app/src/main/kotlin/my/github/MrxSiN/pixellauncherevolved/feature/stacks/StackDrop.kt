package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion
import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Dropping a widget onto another widget stacks them, instead of moving one aside.
 *
 * ```
 * Workspace.onDragOver(DragObject)    mDragTargetLayout, mDragInfo (CellInfo.cell = the view being dragged)
 *   DragObject.getVisualCenter(float[]) + mapPointFromDropLayout(CellLayout, float[])   cell layout coordinates
 *   setDragMode(int)                  0 cancels reorder and folder creation
 *   setCurrentDropLayout(CellLayout)  null reverts the layout's temporary reorder and drop outline
 * Workspace.onDragExit(DragObject)    DragObject.dragComplete is true when a drop follows
 * Workspace.onDrop(DragObject, DragOptions)
 *   DragView.detachContentView(boolean)   true puts the dragged widget back in its own cell layout
 *   DragLayer.animateViewIntoPosition(DragView, View, int, Workspace)
 *   DragObject.deferDragViewCleanupPostAnimation   the drag view goes when that animation ends
 * Workspace.onDragEnd()
 * ```
 *
 * The launcher already treats an icon held over another icon this way: past a
 * radius around the cell it stops reordering and offers a folder. Here a home
 * screen widget held with its centre anywhere over another widget stops
 * reordering in the same way (an inset let the reorder push the widget away while
 * the drag crossed its edge, measured on the device), and the widget
 * under it shrinks a little inside a drop frame ([DropFrame]) to show it will take the drop. Moving off it hands
 * the drag back to the launcher, which reorders as before. Only widgets already
 * on the home screen: one from the widget picker drops as it always has, and so
 * does a widget that is itself a stack.
 *
 * On the drop the drag view flies into the widget under it, the dragged widget
 * joins that widget's stack at its size ([StackDialogs.stack]), and the home
 * screen leaves the drag state as after any drop. A widget whose app does not go
 * as small as the one under it is not offered the stack, and moves as usual.
 *
 * The drag-over check runs on every drag move: one cached switch read, field
 * reads, two reflective calls on kept argument arrays ([Invoke.with]) and index
 * walks over the stacks and the page's items; nothing is allocated.
 */
internal class StackDrop(
    private val host: StackHost,
    private val actions: StackActions,
    private val logger: Logger,
    private val dragTargetLayout: Field,
    private val dragInfo: Field,
    private val cellOf: Field,
    private val shortcutsAndWidgets: Field,
    private val setDragMode: Method,
    private val setCurrentDropLayout: Method,
    private val visualCenter: Method,
    private val mapPoint: Method,
    private val dragComplete: Field,
    private val dragViewOf: Field,
    private val deferCleanup: Field,
    private val detachContent: Method,
    private val animateInto: Method,
    private val dragLayerOf: Method,
    private val stateManagerOf: Field,
    private val goToState: Method,
    private val normal: Any,
) {

    private var target: View? = null
    private var layout: ViewGroup? = null
    private val point = FloatArray(2)
    private val pointArgs = arrayOf<Any?>(point)
    private val mapArgs = arrayOf<Any?>(null, point)
    private val frame = DropFrame()

    /** A widget under the drag, waiting out [StackMotion.HOVER_DWELL_MS] before it is offered the stack. */
    private var candidate: View? = null
    private var refused: View? = null
    private var dwellWorkspace: Any? = null
    private val arm = Runnable { candidate?.let { under -> layout?.let { cells -> hold(cells, under) } } }

    /**
     * From `onDragOver`; true while the widget is held over one it can stack with, so
     * the launcher does not reorder. A widget it cannot join (a full stack, or a size its
     * app does not go to) shows a refusal frame and is left to the launcher's own drop.
     */
    fun over(workspace: Any, drag: Any): Boolean {
        if (!WidgetStacks.on) return release(haptic = false)
        val dragged = dragInfo.get(workspace)?.let(cellOf::get) as? View ?: return release(haptic = true)
        val item = dragged.tag
        if (!host.isWidget(item) || WidgetStacks.graph.stackOf(host.id.getInt(item)) != null) return release(haptic = true)

        val cells = layout ?: dragTargetLayout.get(workspace) as? ViewGroup ?: return release(haptic = true)
        // getVisualCenter fills and returns [point]; the argument arrays are kept, not made per move.
        Invoke.with(visualCenter, drag, pointArgs)
        mapArgs[0] = cells
        Invoke.with(mapPoint, workspace, mapArgs)
        mapArgs[0] = null
        val under = widgetAt(cells, point[0], point[1], dragged) ?: return release(haptic = true)
        val full = WidgetStacks.graph.stackOf(host.id.getInt(under.tag))?.isFull == true
        if (full || !host.fits(item, under.tag)) {
            release(haptic = false)
            if (refused !== under) {
                refused = under
                frame.refuse(cells, under)
                under.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            return false
        }
        refused = null
        if (under === target) return true
        if (under !== candidate) {
            if (target == null && candidate == null) {
                // As the launcher does when an icon starts making a folder: no reorder,
                // no drop outline, the page's items back where they were.
                setDragMode.invoke(workspace, DRAG_MODE_NONE)
                setCurrentDropLayout.invoke(workspace, null)
            }
            target?.let(::settle)
            target = null
            frame.hide()
            candidate = under
            layout = cells
            dwellWorkspace = workspace
            under.removeCallbacks(arm)
            under.postDelayed(arm, StackMotion.HOVER_DWELL_MS)
        }
        return true
    }

    /** From `onDragExit`, before the launcher's own: a drag that leaves without dropping lets the widget go. */
    fun exit(drag: Any) {
        if ((target != null || candidate != null) && !dragComplete.getBoolean(drag)) release(haptic = false)
    }

    /** From `onDrop`; true when the drop made a stack and the launcher's own drop must not run. */
    fun drop(workspace: Any, drag: Any): Boolean {
        val under = target ?: return false.also { release(haptic = false) }
        val dragged = dragInfo.get(workspace)?.let(cellOf::get) as? View ?: return false.also { release(haptic = false) }
        val launcher = host.launcher(under) ?: return false
        return runCatching {
            val dragView = dragViewOf.get(drag)
            detachContent.invoke(dragView, true)
            check(actions.stack(launcher, dragged, under)) { "the widgets cannot be stacked" }
            deferCleanup.setBoolean(drag, true)
            // Full size first: the drag view lands on the widget's own bounds, and the
            // widget is hidden under it until it does.
            under.animate().cancel()
            under.scaleX = 1f
            under.scaleY = 1f
            animateInto.invoke(dragLayerOf.invoke(launcher), dragView, under, -1, workspace)
            under.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            frame.hide()
            forget()
            stateManagerOf.get(launcher)?.let { goToState.invoke(it, normal) }
            true
        }.onFailure {
            logger.warn("Widget stacks: the drop could not make a stack; the launcher drops it instead", it)
            settle(under)
            frame.hide()
            forget()
        }.getOrDefault(false)
    }

    /** From `onDragEnd`: nothing stays shrunk or framed once a drag is over, however it ended. */
    fun end() {
        release(haptic = false)
        refused = null
        frame.hide()
    }

    private fun hold(cells: ViewGroup, under: View) {
        if (candidate !== under) return
        target = under
        frame.show(cells, under)
        under.animate().scaleX(HELD_SCALE).scaleY(HELD_SCALE)
            .setDuration(StackMotion.HOVER_MS).setInterpolator(StackMotion.ENTER).start()
        under.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
    }

    /** Lets go of the widget under the drag, if any; always false, for [over] to return. */
    private fun release(haptic: Boolean): Boolean {
        candidate?.removeCallbacks(arm)
        val held = target
        if (held != null) {
            settle(held)
            frame.hide()
            if (haptic) held.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
        }
        forget()
        return false
    }

    private fun forget() {
        candidate?.removeCallbacks(arm)
        candidate = null
        target = null
        layout = null
        dwellWorkspace = null
    }

    private fun settle(view: View) {
        view.animate().scaleX(1f).scaleY(1f)
            .setDuration(StackMotion.RELEASE_MS).setInterpolator(StackMotion.SETTLE).start()
    }

    /** The home screen widget other than [dragged] that holds the cell layout point (x, y). */
    private fun widgetAt(cells: ViewGroup, x: Float, y: Float, dragged: View): View? {
        val items = shortcutsAndWidgets.get(cells) as? ViewGroup ?: return null
        val px = x - items.left
        val py = y - items.top
        for (i in items.childCount - 1 downTo 0) {
            val child = items.getChildAt(i)
            if (child === dragged || child.visibility != View.VISIBLE || !host.isWidget(child.tag)) continue
            if (px >= child.left && px <= child.right && py >= child.top && py <= child.bottom) {
                return child
            }
        }
        return null
    }

    companion object {
        private const val DRAG_MODE_NONE = 0
        private const val HELD_SCALE = 0.92f

        fun install(context: FeatureContext, host: StackHost, actions: StackActions) {
            fun cls(name: String) = requireNotNull(context.findClass(name)) { "no $name" }
            val workspace = cls("com.android.launcher3.Workspace")
            val dragObject = cls("com.android.launcher3.DropTarget\$DragObject")
            val dragOptions = cls("com.android.launcher3.dragndrop.DragOptions")
            val dragView = cls("com.android.launcher3.dragndrop.DragView")
            val dragLayer = cls("com.android.launcher3.dragndrop.DragLayer")
            val cellLayout = cls("com.android.launcher3.CellLayout")
            val launcher = cls("com.android.launcher3.Launcher")
            val stateManager = cls("com.android.launcher3.statemanager.StateManager")
            val baseState = cls("com.android.launcher3.statemanager.BaseState")
            val launcherState = cls("com.android.launcher3.LauncherState")
            val int = Int::class.javaPrimitiveType!!

            fun method(type: Class<*>, name: String, vararg params: Class<*>): Method =
                Reflect.declaredMethod(type, name, *params).apply { isAccessible = true }

            val drop = StackDrop(
                host = host,
                actions = actions,
                logger = context.logger,
                dragTargetLayout = Reflect.declaredField(workspace, "mDragTargetLayout"),
                dragInfo = Reflect.declaredField(workspace, "mDragInfo"),
                cellOf = Reflect.declaredField(cls("com.android.launcher3.celllayout.CellInfo"), "cell"),
                shortcutsAndWidgets = Reflect.declaredField(cellLayout, "mShortcutsAndWidgets"),
                setDragMode = method(workspace, "setDragMode", int),
                setCurrentDropLayout = method(workspace, "setCurrentDropLayout", cellLayout),
                visualCenter = method(dragObject, "getVisualCenter", FloatArray::class.java),
                mapPoint = method(workspace, "mapPointFromDropLayout", cellLayout, FloatArray::class.java),
                dragComplete = Reflect.declaredField(dragObject, "dragComplete"),
                dragViewOf = Reflect.declaredField(dragObject, "dragView"),
                deferCleanup = Reflect.declaredField(dragObject, "deferDragViewCleanupPostAnimation"),
                detachContent = method(dragView, "detachContentView", Boolean::class.javaPrimitiveType!!),
                animateInto = method(dragLayer, "animateViewIntoPosition", dragView, View::class.java, int, workspace),
                dragLayerOf = requireNotNull(Reflect.method(launcher, "getDragLayer")),
                stateManagerOf = Reflect.declaredField(launcher, "mStateManager"),
                goToState = method(stateManager, "goToState", baseState),
                normal = launcherState.getField("NORMAL").get(null)!!,
            )

            context.xposed.hook(method(workspace, "onDragOver", dragObject)).intercept { chain ->
                if (!WidgetStacks.on) return@intercept chain.proceed()
                val consumed = runCatching { drop.over(chain.thisObject, chain.getArg(0)) }
                    .onFailure { context.logger.warn("Widget stacks: drag over could not be read", it) }
                    .getOrDefault(false)
                if (consumed) null else chain.proceed()
            }
            context.xposed.hook(method(workspace, "onDragExit", dragObject)).intercept { chain ->
                runCatching { drop.exit(chain.getArg(0)) }
                chain.proceed()
            }
            context.xposed.hook(method(workspace, "onDrop", dragObject, dragOptions)).intercept { chain ->
                if (drop.drop(chain.thisObject, chain.getArg(0))) null else chain.proceed()
            }
            context.xposed.hook(method(workspace, "onDragEnd")).intercept { chain ->
                runCatching { drop.end() }
                chain.proceed()
            }
        }
    }
}

/**
 * The frame around a widget a dragged widget will stack with: a rounded outline
 * a little outside the widget, with a faint fill, in the overlay of its page,
 * fading in as the widget is held over and out as it is let go.
 */
private class DropFrame : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()
    private var page: ViewGroup? = null
    private var strength = 0f
    private var radius = 0f
    private var fade: ValueAnimator? = null

    /** The widget will take the drop: an outline in the theme's primary colour. */
    fun show(page: ViewGroup, under: View) = frame(page, under, ExpressiveRole.PRIMARY.of(page.context), stay = true)

    /** The widget cannot take the drop: an outline in the error colour, gone a moment later. */
    fun refuse(page: ViewGroup, under: View) = frame(page, under, ExpressiveRole.ERROR.of(page.context), stay = false)

    private fun frame(page: ViewGroup, under: View, color: Int, stay: Boolean) {
        if (this.page !== page) {
            this.page?.overlay?.remove(this)
            page.overlay.add(this)
            this.page = page
        }
        val density = page.resources.displayMetrics.density
        val container = under.parent as? View ?: return
        val outset = OUTSET_DP * density
        box.set(
            container.left + under.left - outset, container.top + under.top - outset,
            container.left + under.right + outset, container.top + under.bottom + outset,
        )
        radius = runCatching { page.resources.getDimension(android.R.dimen.system_app_widget_background_radius) }.getOrDefault(0f) + outset
        stroke.strokeWidth = STROKE_DP * density
        setBounds(0, 0, page.width, page.height)
        this.color = color
        animateTo(1f)
        if (!stay) fade?.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (fade === animation) page.postDelayed({ if (this@DropFrame.color == color) hide() }, REFUSAL_MS)
            }
        })
    }

    private var color = 0

    fun hide() = animateTo(0f)

    private fun animateTo(to: Float) {
        fade?.cancel()
        if (page == null) return
        val from = strength
        val run = ValueAnimator.ofFloat(0f, 1f)
        run.duration = StackMotion.HOVER_MS
        run.interpolator = ExpressiveMotion.EMPHASIZED_DECELERATE
        run.addUpdateListener {
            strength = from + (to - from) * it.animatedFraction
            invalidateSelf()
        }
        fade = run
        run.start()
    }

    override fun draw(canvas: Canvas) {
        if (strength <= 0f) return
        fill.color = color
        fill.alpha = (FILL_ALPHA * strength).toInt()
        canvas.drawRoundRect(box, radius, radius, fill)
        stroke.color = color
        stroke.alpha = (STROKE_ALPHA * strength).toInt()
        canvas.drawRoundRect(box, radius, radius, stroke)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val OUTSET_DP = 4f
        const val STROKE_DP = 2f
        const val REFUSAL_MS = 600L
        const val FILL_ALPHA = 46
        const val STROKE_ALPHA = 220
    }
}
