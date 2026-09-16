package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Sends the home screen search bar to the app drawer's search box.
 *
 * Earlier Pixel Launcher versions searched your apps from that bar. This one
 * hands the tap to the Google app instead, which is a different errand: the bar
 * sits on the home screen, among your apps, and the drawer's search is the one
 * that finds them.
 *
 * The bar is a widget, so the tap belongs to the Google app's own
 * `RemoteViews` and there is no listener here to replace. It is taken a step
 * earlier instead, at the launcher's widget host, which is the last view that
 * sees the touch before the widget's children do. Which host is a bar is
 * [SearchBarWidgets]' answer, not a type check: the launcher stopped giving the
 * bar a host class of its own in Android 17 `CP3A.260905.009`.
 *
 * Only the bar itself. The widget's own buttons — the logo, the microphone,
 * Lens — are smaller views inside it with actions of their own, and a tap that
 * lands on one of them is left alone.
 */
class HomeSearchBarFeature : ToggleFeature(Settings.HOME_SEARCH_OPENS_DRAWER) {

    override fun install(context: FeatureContext) {
        val host = context.findClass(WIDGET_HOST)
        val bars = SearchBarWidgets(context)
        val intercept = host?.let {
            Reflect.method(it, "onInterceptTouchEvent", MotionEvent::class.java)
        }
        val touch = host?.let { Reflect.method(it, "onTouchEvent", MotionEvent::class.java) }

        if (bars == null || intercept == null || touch == null) {
            context.logger.warn("The home screen search bar is not a widget this launcher hosts")
            return
        }

        val search = AppDrawerSearch(context) ?: return
        val longPress = LongPress(context, host)
        val taps = SearchBarTaps(bars, longPress) { widget -> search.open(widget) }

        context.xposed.hook(intercept).intercept { chain ->
            val widget = chain.thisObject as? ViewGroup
            val event = chain.getArg(0) as MotionEvent

            // The launcher's own answer still runs: it starts the long press
            // that moves the widget, and that has to keep working.
            val handled = chain.proceed() as Boolean
            handled || (isEnabled(context.settings) && taps.claims(widget, event))
        }

        context.xposed.hook(touch).intercept { chain ->
            val widget = chain.thisObject as? ViewGroup
            val event = chain.getArg(0) as MotionEvent

            // Read before the launcher handles the event: its own helper clears
            // the long press it performed as soon as the finger comes up.
            val gesture = taps.read(widget, event)

            chain.proceed().also {
                runCatching { taps.onTouch(widget, gesture) }
                    .onFailure { error -> context.logger.warn("Unable to open the app drawer search", error) }
            }
        }

        context.logger.info("Home: the search bar opens the app drawer's search")
    }

    private companion object {
        const val WIDGET_HOST = "com.android.launcher3.widget.LauncherAppWidgetHostView"
    }
}

/**
 * Which taps on the bar belong to this tweak.
 *
 * A tap is claimed on the way down, so the widget's children never see it, and
 * acted on when the finger comes up in the same place. A drag is not a tap —
 * the bar sits directly above the hotseat, where a swipe opens the app drawer
 * on its own — and neither is a press that has already become a long press,
 * which is how the widget is picked up and moved.
 */
private class SearchBarTaps(
    private val searchBars: SearchBarWidgets,
    private val longPress: LongPress,
    private val onTap: (ViewGroup) -> Unit,
) {

    private val claimed = WeakHashMap<ViewGroup, Down>()

    private data class Down(val x: Float, val y: Float)

    fun claims(widget: ViewGroup?, event: MotionEvent): Boolean {
        if (widget == null || !searchBars.holds(widget)) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            claimed.remove(widget)
            if (!hitsAButton(widget, event)) claimed[widget] = Down(event.rawX, event.rawY)
        }

        return claimed.containsKey(widget)
    }

    /**
     * What the launcher is about to be told, taken before it is told it.
     *
     * `CheckLongPressHelper` clears the long press it performed the moment the
     * finger comes up, so the one question that decides whether this was a tap
     * has to be asked before the launcher's own handler runs.
     */
    fun read(widget: ViewGroup?, event: MotionEvent): Gesture = Gesture(
        action = event.actionMasked,
        x = event.rawX,
        y = event.rawY,
        longPressed = widget != null && longPress.fired(widget),
    )

    data class Gesture(val action: Int, val x: Float, val y: Float, val longPressed: Boolean)

    fun onTouch(widget: ViewGroup?, gesture: Gesture) {
        if (widget == null) return
        val down = claimed[widget] ?: return

        when (gesture.action) {
            MotionEvent.ACTION_UP -> {
                claimed.remove(widget)
                // A press the launcher has already taken as a long one is not a
                // tap: it is how the widget is picked up and moved.
                if (withinSlop(widget, down, gesture) && !gesture.longPressed) onTap(widget)
            }

            MotionEvent.ACTION_CANCEL -> claimed.remove(widget)

            MotionEvent.ACTION_MOVE ->
                if (!withinSlop(widget, down, gesture)) claimed.remove(widget)
        }
    }

    private fun withinSlop(widget: View, down: Down, gesture: Gesture): Boolean {
        val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
        return kotlin.math.abs(gesture.x - down.x) <= slop &&
            kotlin.math.abs(gesture.y - down.y) <= slop
    }

    /**
     * Whether the touch landed on one of the widget's own buttons.
     *
     * The bar carries a clickable view of its own width, which is the search
     * field, and several narrower ones for the logo, the microphone and Lens.
     * Only the narrow ones are somebody else's tap.
     */
    private fun hitsAButton(widget: ViewGroup, event: MotionEvent): Boolean {
        val bounds = Rect()
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        return widget.descendants().any { child ->
            child.isClickable &&
                child.width < widget.width * FULL_WIDTH &&
                child.getGlobalVisibleRect(bounds) &&
                bounds.contains(x, y)
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private companion object {
        /** Anything narrower than this much of the bar is a button, not the field. */
        const val FULL_WIDTH = 0.8f
    }
}

/**
 * Whether the launcher has already taken the press as a long one.
 *
 * The widget host runs its own long press for picking the widget up and moving
 * it, and records the result where its `onInterceptTouchEvent` reads it back.
 * Asking the same field is what keeps a press that has become a drag from also
 * counting as a tap.
 */
private class LongPress(context: FeatureContext, host: Class<*>) {

    private val helperOf = Reflect.field(host, "mLongPressHelper")
    private val performed = helperOf?.type?.let { Reflect.field(it, "mHasPerformedLongPress") }

    init {
        if (performed == null) {
            context.logger.warn(
                "The widget long press cannot be read; a long press on the search bar will open " +
                    "the app drawer as a tap does",
            )
        }
    }

    fun fired(widget: View): Boolean = runCatching {
        performed?.getBoolean(helperOf?.get(widget)) == true
    }.getOrDefault(false)
}

/**
 * The app drawer, opened with its search box ready for typing.
 *
 * Every step is the launcher's own: the state it already moves to when the
 * drawer is opened, and the search box's own focus and keyboard calls, which
 * are what the drawer uses when a search begins any other way. The focus waits
 * for the transition to end, because the box is not on screen until then.
 */
private class AppDrawerSearch private constructor(
    private val logger: Logger,
    private val allApps: Any,
    private val stateManagerOf: Method,
    private val goToState: Method,
    private val appsViewOf: Method,
    private val searchUiManagerOf: Field,
    private val editTextOf: Method,
    private val requestFocus: Method,
    private val showKeyboard: Method,
) {

    fun open(widget: View) {
        val container = widget.context.launcherOrNull() ?: run {
            logger.warn("The search bar is not hosted by a launcher that has an app drawer")
            return
        }

        runCatching {
            val stateManager = stateManagerOf.invoke(container)
            goToState.invoke(
                stateManager,
                allApps,
                true,
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = focusSearch(container)
                },
            )
        }.onFailure { logger.warn("Unable to open the app drawer", it) }
    }

    private fun focusSearch(container: Any) {
        runCatching {
            val editText = editTextOf.invoke(searchUiManagerOf.get(appsViewOf.invoke(container)))
            requestFocus.invoke(editText)
            showKeyboard.invoke(editText)
        }.onFailure { logger.warn("Unable to focus the app drawer's search box", it) }
    }

    /**
     * The launcher behind a view's context.
     *
     * A widget's context is wrapped, so the activity is reached by unwrapping
     * rather than by casting what the view was handed.
     */
    private fun android.content.Context.launcherOrNull(): Any? {
        var current: android.content.Context? = this
        while (current != null) {
            if (stateManagerOf.declaringClass.isInstance(current)) return current
            current = (current as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    companion object {

        operator fun invoke(context: FeatureContext): AppDrawerSearch? {
            val stateful = context.findClass("com.android.launcher3.statemanager.StatefulContainer")
            val state = context.findClass("com.android.launcher3.LauncherState")
            val activity = context.findClass("com.android.launcher3.views.ActivityContext")
            val allAppsView = context.findClass("com.android.launcher3.allapps.ActivityAllAppsContainerView")
            val searchUi = context.findClass("com.android.launcher3.allapps.SearchUiManager")
            val editText = context.findClass("com.android.launcher3.ExtendedEditText")
            val baseState = context.findClass("com.android.launcher3.statemanager.BaseState")
            val manager = context.findClass("com.android.launcher3.statemanager.StateManager")

            val allApps = state?.let {
                runCatching { Reflect.field(it, "ALL_APPS")?.get(null) }.getOrNull()
            }
            val goToState = if (manager == null || baseState == null) {
                null
            } else {
                Reflect.method(
                    manager,
                    "goToState",
                    baseState,
                    Boolean::class.javaPrimitiveType!!,
                    Animator.AnimatorListener::class.java,
                )
            }

            if (stateful == null || allApps == null || goToState == null || activity == null ||
                allAppsView == null || searchUi == null || editText == null
            ) {
                context.logger.warn("The app drawer's search cannot be reached in this launcher")
                return null
            }

            return AppDrawerSearch(
                logger = context.logger,
                allApps = allApps,
                stateManagerOf = Reflect.method(stateful, "getStateManager") ?: return null,
                goToState = goToState,
                appsViewOf = Reflect.method(activity, "getAppsView") ?: return null,
                searchUiManagerOf = Reflect.field(allAppsView, "mSearchUiManager") ?: return null,
                editTextOf = Reflect.method(searchUi, "getEditText") ?: return null,
                requestFocus = Reflect.method(editText, "requestFocusExplicitly") ?: return null,
                showKeyboard = Reflect.method(editText, "showKeyboard") ?: return null,
            )
        }
    }
}
