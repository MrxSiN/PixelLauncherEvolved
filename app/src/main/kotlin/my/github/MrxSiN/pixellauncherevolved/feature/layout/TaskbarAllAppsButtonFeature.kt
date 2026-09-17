package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewGroup

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Takes the app drawer button out of the taskbar while Recents is open.
 *
 * Recents is where you go back to something you were already doing, and the
 * taskbar there is a list of those things. The app drawer button — and the
 * divider that only exists to separate it from them — belong to the other
 * errand, and this leaves the row holding nothing but apps.
 *
 * Only while Recents is open. Everywhere else the button is the launcher's
 * again, which is why the visibility it had is put back rather than assumed:
 * the launcher hides these itself in some states and that decision has to
 * survive.
 *
 * The way in hides the pair and centres what is left at once, before the
 * launcher draws the taskbar into Recents, so the row arrives where it rests.
 * Holding the old width through the transition and closing up afterwards was
 * tried first: the icons arrived right of centre and then slid across, which
 * read as the taskbar correcting itself.
 *
 * The way out puts the pair back once the launcher has left Overview, by which
 * time the taskbar has finished morphing into the hotseat and is no longer the
 * row on screen.
 */
class TaskbarAllAppsButtonFeature : ToggleFeature(Settings.OVERVIEW_HIDE_TASKBAR_ALL_APPS) {

    override val compatibility = CompatibilityFeature.TASKBAR_APP_DRAWER_BUTTON

    /** Visibility each view had before this feature first hid it. */
    private val originalVisibility = WeakHashMap<View, Int>()

    /** Counts transitions, so a deferred change knows it belongs to an old one. */
    private var transitions = 0

    override fun install(context: FeatureContext) {
        val taskbar = TaskbarRow(context) ?: return
        val width = TaskbarIconWidth(context)

        if (width == null) {
            context.logger.warn("The taskbar's icon width cannot be corrected; the row may sit off centre")
        } else {
            width.discount { view -> originalVisibility.containsKey(view) }
        }

        taskbar.onStateApplied { row, showingRecents, transition ->
            val transitionId = ++transitions
            val view = row.firstOrNull()?.parent as? ViewGroup

            if (showingRecents && isEnabled(context.settings)) {
                val hideAll = { apply(row) { hide(it) } }
                if (width == null || view == null) {
                    hideAll()
                } else {
                    width.whileLearning(view, row.any { it.visibility != View.GONE }, hideAll)
                }
            } else {
                // Once the launcher has left, where the change is not on screen.
                transition.whenSettled {
                    if (transitionId == transitions) apply(row) { restore(it) }
                }
            }
        }

        context.logger.info("Taskbar: app drawer button follows the Recents setting")
    }

    /** Changes the row, and asks for the one layout pass that settles it. */
    private fun apply(row: List<View>, change: (View) -> Unit) {
        for (view in row) change(view)
        row.firstOrNull()?.parent?.let { (it as View).requestLayout() }
    }

    private fun hide(view: View) {
        if (!originalVisibility.containsKey(view)) originalVisibility[view] = view.visibility
        view.visibility = View.GONE
    }

    private fun restore(view: View) {
        originalVisibility.remove(view)?.let { view.visibility = it }
    }
}

/**
 * The launcher's move into or out of a state, and when it has finished.
 *
 * `applyState` returns the animation that carries the taskbar through the
 * transition, or nothing when there is none to run. Either way this answers the
 * one question a caller has: when is it safe to change the row.
 */
private class Transition(private val animator: Animator?) {

    fun whenSettled(action: () -> Unit) {
        if (animator == null || !animator.isRunning) {
            action()
            return
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = action()
            },
        )
    }
}

/**
 * Keeps the icons centred once something in the row is hidden.
 *
 * `TaskbarView.onLayout` centres its children against `getIconLayoutWidth()`.
 * Whether that width already leaves hidden children out depends on the build:
 *
 * - On `CP2A.260805.005` it counts children rather than visible children. The
 *   pinned container's hidden entries are subtracted, but the app drawer button
 *   and the divider are direct children, so nothing discounts them. Hidden,
 *   they still hold their slots, and everything left sits two slots right of
 *   centre. The width of one slot is the launcher's own: an icon plus its
 *   margin on each side, which is exactly what `getIconLayoutWidth` adds per
 *   icon.
 * - On `CP3A.260905.009` `getTotalNumberOfIcons()` skips `GONE` children and
 *   `onLayout` skips laying them out, so the launcher centres the row itself.
 *   Subtracting the slots again shrank the row twice over and slid the whole
 *   taskbar off the left edge.
 *
 * Rather than name the build, the launcher's own width is read either side of
 * the first hide, and the slots are subtracted only where it did not drop.
 */
private class TaskbarIconWidth private constructor(
    private val context: FeatureContext,
    private val iconLayoutWidth: Method,
    private val itemMargin: Field,
    private val iconTouchSize: Field,
) {

    /** Whether the launcher discounts hidden children itself, or null until seen. */
    @Volatile
    private var launcherDiscounts: Boolean? = null

    /** Set while the launcher's own answer is read, which the hook passes through. */
    private var readingLauncher = false

    /** @param isHidden whether this feature is the reason a child is not drawn */
    fun discount(isHidden: (View) -> Boolean) {
        context.xposed.hook(iconLayoutWidth).intercept { chain ->
            val width = chain.proceed() as Int
            val row = chain.thisObject as? ViewGroup

            if (row == null || readingLauncher || launcherDiscounts != false) {
                width
            } else {
                runCatching {
                    val hidden = (0 until row.childCount).count { isHidden(row.getChildAt(it)) }
                    if (hidden == 0) width else (width - freedBy(hidden, row)).coerceAtLeast(0)
                }.getOrDefault(width)
            }
        }
    }

    /**
     * Runs [hide], learning from the first one that takes a visible view away
     * whether the launcher already gives the hidden width up by itself.
     *
     * A hide of views the launcher had already hidden changes nothing to
     * measure, so it teaches nothing and the next one is asked again.
     */
    fun whileLearning(row: ViewGroup, hidesVisible: Boolean, hide: () -> Unit) {
        if (launcherDiscounts != null || !hidesVisible) {
            hide()
            return
        }

        val before = launcherWidth(row)
        hide()
        val after = launcherWidth(row)
        if (before != null && after != null) launcherDiscounts = after < before
    }

    private fun launcherWidth(row: ViewGroup): Int? {
        readingLauncher = true
        return try {
            runCatching { iconLayoutWidth.invoke(row) as Int }.getOrNull()
        } finally {
            readingLauncher = false
        }
    }

    /**
     * The width the row no longer needs.
     *
     * A slot per hidden view, less one margin: the launcher lays the divider
     * flush against the icon that follows it, so the pair costs one margin less
     * than two whole slots. Measured on a Pixel 8 Pro, where a slot is 229px
     * and the row lands within a margin of where the launcher's own row does.
     */
    private fun freedBy(hidden: Int, row: ViewGroup): Int {
        val margin = itemMargin.getInt(row)
        return hidden * (margin * 2 + iconTouchSize.getInt(row)) - margin * 2
    }

    companion object {

        operator fun invoke(context: FeatureContext): TaskbarIconWidth? {
            val taskbarView = context.findClass("com.android.launcher3.taskbar.TaskbarView")
                ?: return null

            return TaskbarIconWidth(
                context = context,
                iconLayoutWidth = Reflect.method(taskbarView, "getIconLayoutWidth") ?: return null,
                itemMargin = Reflect.field(taskbarView, "mItemMarginLeftRight") ?: return null,
                iconTouchSize = Reflect.field(taskbarView, "mIconTouchSize") ?: return null,
            )
        }
    }
}

/**
 * The two views this feature touches, and when the launcher changes state.
 *
 * `TaskbarLauncherStateController.applyState` is called once per state change,
 * and `mLauncherState` says which state that is. Recents is recognised by
 * identity against the launcher's own state objects rather than by a flag,
 * because `isRecentsViewVisible` is also true while an app is in front.
 */
private class TaskbarRow private constructor(
    private val context: FeatureContext,
    private val applyState: java.lang.reflect.Method,
    private val controllersOf: java.lang.reflect.Field,
    private val launcherStateOf: java.lang.reflect.Field,
    private val viewControllerOf: java.lang.reflect.Field,
    private val taskbarViewOf: java.lang.reflect.Field,
    private val allAppsButtonOf: java.lang.reflect.Field,
    private val dividerOf: java.lang.reflect.Field,
    private val recentsStates: List<Any>,
) {

    fun onStateApplied(
        apply: (row: List<View>, showingRecents: Boolean, transition: Transition) -> Unit,
    ) {
        context.xposed.hook(applyState).intercept { chain ->
            val result = chain.proceed()

            runCatching {
                val taskbarView = taskbarViewOf.get(
                    viewControllerOf.get(controllersOf.get(chain.thisObject)),
                )
                val row = listOfNotNull(
                    allAppsButtonOf.get(taskbarView) as? View,
                    dividerOf.get(taskbarView) as? View,
                )
                val state = launcherStateOf.get(chain.thisObject)

                apply(row, recentsStates.any { it === state }, Transition(result as? Animator))
            }.onFailure {
                context.logger.warn("Unable to read the taskbar row for this state", it)
            }

            result
        }
    }

    companion object {

        /**
         * @return null when this launcher does not lay its taskbar out this way,
         *   which disables the tweak rather than the launcher.
         */
        operator fun invoke(context: FeatureContext): TaskbarRow? {
            val controller = context.findClass(
                "com.android.launcher3.taskbar.TaskbarLauncherStateController",
            ) ?: return null
            val controllers = context.findClass(
                "com.android.launcher3.taskbar.TaskbarControllers",
            ) ?: return null
            val viewController = context.findClass(
                "com.android.launcher3.taskbar.TaskbarViewController",
            ) ?: return null
            val taskbarView = context.findClass(
                "com.android.launcher3.taskbar.TaskbarView",
            ) ?: return null
            val launcherState = context.findClass("com.android.launcher3.LauncherState")
                ?: return null

            val recents = RECENTS_STATES.mapNotNull { name ->
                runCatching { Reflect.field(launcherState, name)?.get(null) }.getOrNull()
            }

            val applyState = controller.declaredMethods.firstOrNull {
                it.name == "applyState" && it.parameterTypes.size == 2
            } ?: return null

            return TaskbarRow(
                context = context,
                applyState = applyState,
                controllersOf = Reflect.field(controller, "mControllers") ?: return null,
                launcherStateOf = Reflect.field(controller, "mLauncherState") ?: return null,
                viewControllerOf = Reflect.field(controllers, "taskbarViewController")
                    ?: return null,
                taskbarViewOf = Reflect.field(viewController, "mTaskbarView") ?: return null,
                allAppsButtonOf = Reflect.field(taskbarView, "mAllAppsButtonContainer")
                    ?: return null,
                dividerOf = Reflect.field(taskbarView, "mTaskbarDividerContainer") ?: return null,
                recentsStates = recents.ifEmpty { return null },
            )
        }

        /** Every state that puts Recents on screen with the launcher in front. */
        val RECENTS_STATES = listOf("OVERVIEW", "OVERVIEW_MODAL_TASK", "OVERVIEW_SPLIT_SELECT")
    }
}
