package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * A full settings screen, drawn the way Android 17 QPR1's Settings draws one.
 *
 * Some settings are not a row of switches: a drag-to-arrange grid needs the
 * whole screen. The launcher's own settings pages are preference lists built
 * from classes this module reaches only by reflection, which cannot hold such a
 * view, so this is a screen of its own laid out as a Settings page is — a round
 * tonal back button beside the title, a description, then the content — on the
 * same surface colour.
 *
 * It moves as an Android 17 QPR1 Settings page does. It arrives on Material 3's
 * shared axis, sliding in from the end while it fades up on the emphasized
 * decelerate curve, and leaves the same way back on emphasized accelerate. A
 * back swipe is predictive: the page shrinks towards the edge being swiped from
 * and rounds its corners as the finger moves, springs back if the swipe is let
 * go, and finishes leaving if it is not.
 */
/** A shown page, to close from code: the Settings screen it fills, or its own window. */
fun interface PageHandle {
    fun dismiss()
}

class ExpressivePage(private val context: Context) {

    private var title: CharSequence = ""
    private var description: CharSequence? = null
    private var content: View? = null
    private var onClose: () -> Unit = {}
    private var backLabel: CharSequence? = null

    fun title(text: CharSequence) = apply { title = text }

    fun description(text: CharSequence) = apply { description = text }

    /** What a screen reader says for the back button. */
    fun backLabel(text: CharSequence) = apply { backLabel = text }

    fun content(view: View) = apply { content = view }

    /** Runs once, however the screen is left. */
    fun onClose(action: () -> Unit) = apply { onClose = action }

    /**
     * Shows the page: inside the Settings screen [PageHost] opened for it, as every Settings page is
     * shown, or, opened any other way, in a window of its own over [context].
     */
    fun show(): PageHandle {
        PageHost.isHosting(context)?.let { screen -> return fill(screen) }
        val dialog = showWindow()
        return PageHandle { dialog.dismiss() }
    }

    /**
     * Puts the page where the Settings screen's own rows would be, under its own title bar, and
     * saves through [onClose] as the screen closes. The rows' list stays in place, hidden, so the
     * screen keeps its own layout and insets; the page scrolls with the title bar as rows do.
     */
    private fun fill(screen: android.app.Activity): PageHandle {
        PageHost.filled(screen)
        screen.title = title
        val holder = screen.findViewById<ViewGroup>(android.R.id.list_container)
        val list = holder?.getChildAt(0)
        if (holder == null || list == null) {
            // Not the layout this was written against: the page still opens, in its own window.
            val dialog = showWindow()
            return PageHandle { dialog.dismiss() }
        }
        list.visibility = View.GONE
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            description?.let { text ->
                addView(TextView(context).apply {
                    this.text = text
                    ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(EDGE_DP)
                    marginEnd = dp(EDGE_DP)
                    topMargin = dp(8f)
                    bottomMargin = dp(24f)
                })
            }
            content?.let { addView(it) }
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            // The title bar collapses as the page scrolls, as it does over rows.
            isNestedScrollingEnabled = true
            // Top and bottom as the rows' list has them (insets); the sides are the page's own, as
            // every page here lays out its own margins, the same as in a window of its own.
            setPadding(0, list.paddingTop, 0, list.paddingBottom + dp(24f))
            addView(column)
        }
        holder.addView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        PageHost.onLeave(screen, onClose)
        return PageHandle { screen.finish() }
    }

    private fun showWindow(): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val scrim = ColorDrawable(ExpressiveRole.SURFACE_CONTAINER.of(context))
        val motion = PageMotion(dialog, scrim)
        val page = screen(motion)
        motion.page = page
        dialog.setContentView(page)
        dialog.window?.apply {
            setBackgroundDrawable(scrim)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDecorFitsSystemWindows(false)
            isNavigationBarContrastEnforced = false
            // The page animates itself; a window animation on top would play twice.
            setWindowAnimations(0)
            val light = if (isNight()) 0 else APPEARANCE_LIGHT_BARS
            insetsController?.setSystemBarsAppearance(light, APPEARANCE_LIGHT_BARS)
        }
        dialog.setOnDismissListener {
            motion.release()
            onClose()
        }
        dialog.show()
        dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, motion)
        motion.enter()
        return dialog
    }

    private fun screen(motion: PageMotion): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar(motion))
            description?.let { text ->
                addView(TextView(context).apply {
                    this.text = text
                    ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(EDGE_DP)
                    marginEnd = dp(EDGE_DP)
                    topMargin = dp(16f)
                    bottomMargin = dp(24f)
                })
            }
            content?.let { addView(it) }
        }
        // The status bar keeps the page's colour: content scrolling up passes under this strip, not
        // under the clock and icons. The navigation bar side stays see-through, as Settings draws it.
        val statusScrim = View(context).apply { background = ColorDrawable(ExpressiveRole.SURFACE_CONTAINER.of(context)) }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            background = ColorDrawable(ExpressiveRole.SURFACE_CONTAINER.of(context))
            addView(column)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                // A text field on the page brings the keyboard up over its lower part.
                val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard) + dp(24f))
                statusScrim.layoutParams = statusScrim.layoutParams.apply { height = bars.top }
                insets
            }
        }
        return FrameLayout(context).apply {
            addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(statusScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, android.view.Gravity.TOP))
        }
    }

    private fun topBar(motion: PageMotion) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16f), dp(8f), dp(EDGE_DP), dp(8f))
        minimumHeight = dp(64f)

        addView(FrameLayout(context).apply {
            background = ExpressiveShapes.pill(context, ExpressiveRole.SURFACE_CONTAINER_HIGH.of(context))
            contentDescription = backLabel
            isClickable = true
            isFocusable = true
            setOnClickListener { motion.leave() }
            addView(View(context).apply {
                background = BackArrow(ExpressiveRole.ON_SURFACE.of(context))
            }, FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(BACK_DP), dp(BACK_DP)))

        addView(TextView(context).apply {
            text = title
            ExpressiveType.TITLE_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16f) })
    }

    /**
     * The page's entrance, its exit and its answer to a back swipe.
     *
     * The window behind the page is the same surface colour as the page, so it
     * fades with the page rather than standing in place while the page moves.
     */
    private inner class PageMotion(
        private val dialog: Dialog,
        private val scrim: ColorDrawable,
    ) : OnBackAnimationCallback {

        lateinit var page: View
        private var animator: ValueAnimator? = null
        private var leaving = false

        /**
         * The screen this page opens over, moved as Settings moves the page under a sub-page: out to
         * the start as this one comes in, back in from the start as this one closes. Left alone over
         * the home screen (opened from a long press there), whose views are the launcher's own.
         */
        private val under: View? = context.activityOrNull()
            ?.takeUnless { it.intent?.hasCategory(android.content.Intent.CATEGORY_HOME) == true }
            ?.findViewById(android.R.id.content)

        /** Puts [under] back exactly as it was; also when the page goes some other way than [leave]. */
        fun release() {
            under?.apply {
                translationX = 0f
                alpha = 1f
            }
        }

        /** Where the page stands: [presence] on the shared axis, [back] how far a back swipe has taken it. */
        private var presence = 0f
        private var back = 0f
        private var backDirection = 1f

        fun enter() {
            page.clipToOutline = true
            page.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(BACK_CORNER_DP) * back)
            }
            draw()
            run(from = presence, to = 1f, ENTER_MS, ExpressiveMotion.EMPHASIZED_DECELERATE) { presence = it }
        }

        fun leave() {
            if (leaving) return
            leaving = true
            // As Settings closes a sub-page: quick off the mark and settled long before the end.
            run(from = presence, to = 0f, EXIT_MS, ExpressiveMotion.EMPHASIZED_DECELERATE, then = { release(); dialog.dismiss() }) {
                presence = it
            }
        }

        override fun onBackStarted(backEvent: BackEvent) {
            animator?.cancel()
            backDirection = if (backEvent.swipeEdge == BackEvent.EDGE_LEFT) 1f else -1f
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            back = backEvent.progress
            draw()
        }

        override fun onBackCancelled() {
            run(from = back, to = 0f, SETTLE_MS, ExpressiveMotion.spatialSpring(SETTLE_MS)) { back = it }
        }

        override fun onBackInvoked() = leave()

        private fun run(
            from: Float,
            to: Float,
            duration: Long,
            curve: TimeInterpolator,
            then: () -> Unit = {},
            update: (Float) -> Unit,
        ) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(from, to).apply {
                this.duration = duration
                interpolator = curve
                addUpdateListener {
                    update(it.animatedValue as Float)
                    draw()
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) then()
                    }
                })
                start()
            }
        }

        /**
         * One frame of the shared axis, as Pixel Launcher's own settings pages move. Presence 1 is the
         * page in place over a screen that has moved out to the start; 0 is the page gone towards the
         * end and the screen under it back in place. The page fades in the half nearest to it and
         * the screen under it in the other part, so the two never blend into a double image. A back
         * swipe shrinks the page towards the edge it started from and rounds its corners on top.
         */
        private fun draw() {
            val end = if (page.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
            val scale = 1f - (1f - BACK_SCALE) * back
            val shown = ((presence - PAGE_FADE_FROM) / (1f - PAGE_FADE_FROM)).coerceIn(0f, 1f)
            page.alpha = shown
            page.translationX = end * dp(SHARED_AXIS_DP) * (1f - presence) + backDirection * dp(BACK_SHIFT_DP) * back
            page.scaleX = scale
            page.scaleY = scale
            scrim.alpha = (shown * OPAQUE).roundToInt()
            under?.apply {
                alpha = ((UNDER_FADE_TO - presence) / UNDER_FADE_TO).coerceIn(0f, 1f)
                translationX = -end * dp(UNDER_AXIS_DP) * presence
            }
            page.invalidateOutline()
        }
    }

    private fun isNight(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        /**
         * Measured off Pixel Launcher's own sub-page close (Home settings, CP4A, 1x animation scales):
         * the page going travels about 50dp towards the end and is gone by the halfway mark; the page
         * underneath comes in from about 80dp towards the start; the movement is done in roughly a
         * third of the duration.
         */
        const val SHARED_AXIS_DP = 50f
        const val UNDER_AXIS_DP = 80f
        const val PAGE_FADE_FROM = 0.5f
        const val UNDER_FADE_TO = 0.6f
        const val ENTER_MS = 350L
        const val EXIT_MS = 350L
        const val SETTLE_MS = 350L

        /** Material 3 predictive back: 90% of full size, 8dp towards the swipe, 32dp corners. */
        const val BACK_SCALE = 0.9f
        const val BACK_SHIFT_DP = 8f
        const val BACK_CORNER_DP = 32f
        const val OPAQUE = 255f

        const val EDGE_DP = 24f
        const val BACK_DP = 48f
        const val APPEARANCE_LIGHT_BARS = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    }
}

/** The Material back arrow, drawn rather than borrowed from a resource the launcher's process cannot name. */
private class BackArrow(color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val size = bounds.width().toFloat()
        val unit = size / 24f
        paint.strokeWidth = 2f * unit
        path.reset()
        path.moveTo(bounds.left + 20f * unit, bounds.top + 12f * unit)
        path.lineTo(bounds.left + 5f * unit, bounds.top + 12f * unit)
        path.moveTo(bounds.left + 12f * unit, bounds.top + 5f * unit)
        path.lineTo(bounds.left + 5f * unit, bounds.top + 12f * unit)
        path.lineTo(bounds.left + 12f * unit, bounds.top + 19f * unit)
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
