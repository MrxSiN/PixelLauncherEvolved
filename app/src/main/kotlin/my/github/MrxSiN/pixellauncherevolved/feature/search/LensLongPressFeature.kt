package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.search.SearchWidgetButtons.containsOnScreen
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Opens the Lens camera from a long press on the search bar's Lens button.
 *
 * Tapping that button already opens Lens, but on whatever it was last showing.
 * A press and hold goes straight to the camera instead, which is the one thing
 * the button is reached for and the one thing the tap does not promise.
 *
 * Holding the button does something today: the launcher takes it as a long
 * press on the widget and offers to move it. That is what this replaces, and
 * only over the button — the rest of the bar still picks the widget up, which
 * is how a widget is moved at all.
 *
 * The tap is not taken away. Claiming the press means the widget's own children
 * never see it, so a press that turns out to be short is handed back to the
 * button it landed on.
 */
class LensLongPressFeature : ToggleFeature(Settings.HOME_LENS_LONG_PRESS) {

    override fun install(context: FeatureContext) {
        val host = context.findClass(WIDGET_HOST)
        val bar = context.findClass(SEARCH_WIDGET)
        val intercept = host?.let {
            Reflect.method(it, "onInterceptTouchEvent", MotionEvent::class.java)
        }
        val touch = host?.let { Reflect.method(it, "onTouchEvent", MotionEvent::class.java) }

        if (bar == null || intercept == null || touch == null) {
            context.logger.warn("The home screen search bar is not a widget this launcher hosts")
            return
        }

        val presses = LensPresses(
            searchWidget = bar,
            widgetLongPress = WidgetLongPress(context, host),
            lens = LensCamera(context.logger),
        )

        context.xposed.hook(intercept).intercept { chain ->
            val widget = chain.thisObject as? ViewGroup
            val event = chain.getArg(0) as MotionEvent

            // The launcher's own answer runs first and is never taken away: the
            // press this claims is only the one over the Lens button.
            val handled = chain.proceed() as Boolean
            handled || (isEnabled(context.settings) && presses.claims(widget, event))
        }

        context.xposed.hook(touch).intercept { chain ->
            val widget = chain.thisObject as? ViewGroup
            val event = chain.getArg(0) as MotionEvent
            val gesture = Gesture(event.actionMasked, event.rawX, event.rawY)

            chain.proceed().also {
                runCatching { presses.onTouch(widget, gesture) }
                    .onFailure { error -> context.logger.warn("Unable to open the Lens camera", error) }
            }
        }

        context.logger.info("Home: a long press on the search bar's Lens button opens the camera")
    }

    private companion object {
        const val WIDGET_HOST = "com.android.launcher3.widget.LauncherAppWidgetHostView"
        const val SEARCH_WIDGET = "com.android.launcher3.qsb.OseWidgetView"
    }
}

/** One touch as this tweak needs to see it, in screen coordinates. */
internal data class Gesture(val action: Int, val x: Float, val y: Float)

/**
 * Which presses on the Lens button belong to this tweak, and what they mean.
 *
 * The press is claimed on the way down so the widget's children never act on
 * it, and the launcher's own long press is called off for that gesture, because
 * two long presses on one finger would offer to move the widget and open the
 * camera at once. A timer of this tweak's own then decides: held, it opens the
 * camera; let go first, the button is clicked as though nothing had been
 * claimed; dragged, neither happens.
 */
private class LensPresses(
    private val searchWidget: Class<*>,
    private val widgetLongPress: WidgetLongPress,
    private val lens: LensCamera,
) {

    private val claimed = WeakHashMap<ViewGroup, Press>()

    private class Press(val button: View, val x: Float, val y: Float) {
        var opened: Boolean = false
        var timer: Runnable? = null
    }

    fun claims(widget: ViewGroup?, event: MotionEvent): Boolean {
        if (widget == null || !searchWidget.isInstance(widget)) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            release(widget)
            lensButton(widget, event)?.let { button -> hold(widget, button, event) }
        }

        return claimed.containsKey(widget)
    }

    fun onTouch(widget: ViewGroup?, gesture: Gesture) {
        if (widget == null) return
        val press = claimed[widget] ?: return

        when (gesture.action) {
            MotionEvent.ACTION_UP -> {
                release(widget)
                // Held long enough and the camera is already open; let go before
                // that and the button gets the tap it would have had.
                if (!press.opened && withinSlop(widget, press, gesture)) press.button.performClick()
            }

            MotionEvent.ACTION_CANCEL -> release(widget)

            MotionEvent.ACTION_MOVE -> if (!withinSlop(widget, press, gesture)) release(widget)
        }
    }

    private fun hold(widget: ViewGroup, button: View, event: MotionEvent) {
        val press = Press(button, event.rawX, event.rawY)
        claimed[widget] = press

        // Called off after the launcher has posted it, which is why this runs
        // once the hooked method has already proceeded.
        widgetLongPress.cancel(widget)

        val timer = Runnable {
            press.opened = true
            lens.open(widget)
        }
        press.timer = timer
        widget.postDelayed(timer, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun release(widget: ViewGroup) {
        claimed.remove(widget)?.timer?.let(widget::removeCallbacks)
    }

    /**
     * The Lens button, which is the last of the bar's own buttons.
     *
     * Read off a Pixel 8 Pro on Android 17, the bar carries the Google logo at
     * the left and three buttons at the right, of which Lens is the rightmost.
     * None of them has an id to ask for, and their content descriptions are in
     * whatever language the phone is set to, so the position is what is left to
     * recognise it by. A bar that is laid out the other way round, or one that
     * grows a button to the right of Lens, would pick the wrong one.
     */
    private fun lensButton(widget: ViewGroup, event: MotionEvent): View? {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val lensButton = SearchWidgetButtons.of(widget).maxByOrNull { it.centreOnScreen() }

        return lensButton?.takeIf { it.containsOnScreen(x, y) }
    }

    private fun View.centreOnScreen(): Int {
        val position = IntArray(2)
        getLocationOnScreen(position)
        return position[0] + width / 2
    }

    private fun withinSlop(widget: View, press: Press, gesture: Gesture): Boolean {
        val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
        return kotlin.math.abs(gesture.x - press.x) <= slop &&
            kotlin.math.abs(gesture.y - press.y) <= slop
    }
}

/**
 * The launcher's own long press on the widget, called off for one gesture.
 *
 * The widget host posts it on every touch down, and it is what offers to move
 * the widget. Over the Lens button this tweak answers the press instead, so the
 * launcher's is cancelled rather than left to fire alongside it.
 */
private class WidgetLongPress(context: FeatureContext, host: Class<*>) {

    private val helperOf = Reflect.field(host, "mLongPressHelper")
    private val cancel = helperOf?.type?.let { Reflect.method(it, "cancelLongPress") }

    init {
        if (cancel == null) {
            context.logger.warn(
                "The widget long press cannot be called off; holding the Lens button will offer to " +
                    "move the search bar as well as opening the camera",
            )
        }
    }

    fun cancel(widget: View) {
        runCatching { cancel?.invoke(helperOf?.get(widget)) }
    }
}

/**
 * The Lens camera, opened the one way the Google app accepts.
 *
 * `google://lens` is the Google app's own deep link, and it lands on an
 * activity that refuses two kinds of caller. It wants a calling package, which
 * rules out anything started from a shell, and it wants to be started for a
 * result — measured on Android 17, where starting it any other way logs
 * "Caller package cannot be empty. LensExportedActivity must be started for
 * result." and returns to the home screen without ever showing the camera.
 *
 * Both are satisfied by starting it from the launcher activity itself, which is
 * behind the widget's context, so that is what is unwrapped and asked.
 */
private class LensCamera(private val logger: Logger) {

    fun open(widget: View) {
        val activity = widget.context.activityOrNull() ?: run {
            logger.warn("The search bar is not hosted by an activity that can open Lens")
            return
        }

        val lens = Intent(Intent.ACTION_VIEW, Uri.parse(LENS_URI)).setPackage(GOOGLE_APP)

        runCatching { activity.startActivityForResult(lens, REQUEST_CODE) }
            .onFailure { logger.warn("The Lens camera could not be opened", it) }
    }

    /**
     * The activity behind a view's context.
     *
     * A widget's context is wrapped, so the activity is reached by unwrapping
     * rather than by casting what the view was handed.
     */
    private fun Context.activityOrNull(): Activity? {
        var current: Context? = this
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    private companion object {
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val LENS_URI = "google://lens"

        /**
         * High and particular, because the launcher answers its own results by
         * request code and a collision would hand it somebody else's.
         */
        const val REQUEST_CODE = 0x1E45
    }
}
