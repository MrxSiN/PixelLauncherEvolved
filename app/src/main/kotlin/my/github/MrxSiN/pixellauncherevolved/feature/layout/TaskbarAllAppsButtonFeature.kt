package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
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
 * Timing is the whole difficulty, and the two halves of it want opposite
 * answers.
 *
 * Taking the pair out costs nothing to watch. They are laid out at the left end
 * of the row and the launcher fills the row from the right, so removing them
 * moves none of the icons that are left, and the launcher is not drawing the
 * taskbar yet at the moment it says where it is heading. That half happens at
 * once, which is why the button is never seen in Overview.
 *
 * Re-centring what is left is the opposite: it moves every icon, and doing that
 * while the hotseat is still morphing into the taskbar moves them while they are
 * being animated somewhere else, which reads as a jump rather than a movement.
 * So the row keeps the launcher's own width for the length of the transition and
 * closes up afterwards, over an animation of its own.
 *
 * The way out changes nothing at all. The pair comes back and the width returns
 * once the launcher has left Overview, by which time the taskbar has finished
 * morphing into the hotseat and is no longer the row on screen.
 */
class TaskbarAllAppsButtonFeature : ToggleFeature(Settings.OVERVIEW_HIDE_TASKBAR_ALL_APPS) {

    /** Visibility each view had before this feature first hid it. */
    private val originalVisibility = WeakHashMap<View, Int>()

    /** Counts transitions, so a deferred change knows it belongs to an old one. */
    private var transitions = 0

    override fun install(context: FeatureContext) {
        val taskbar = TaskbarRow(context) ?: return
        val width = TaskbarIconWidth(context)

        if (width == null) {
            context.logger.warn("The taskbar's icon width cannot be corrected; the row will sit off centre")
        } else {
            width.discount { view -> originalVisibility.containsKey(view) }
        }

        taskbar.onStateApplied { row, showingRecents, transition ->
            val transitionId = ++transitions
            val view = row.firstOrNull()?.parent as? ViewGroup

            if (showingRecents && isEnabled(context.settings)) {
                // At once: nothing that is left moves, and nothing is on screen
                // yet to see the button go.
                apply(row) { hide(it) }
                // Afterwards: closing the gap moves every icon, so it waits for
                // the morph to finish and then animates on its own.
                transition.whenSettled {
                    if (transitionId == transitions && view != null) width?.closeUp(view)
                }
            } else {
                // Once the launcher has left, where neither change is on screen.
                transition.whenSettled {
                    if (transitionId != transitions) return@whenSettled
                    if (view != null) width?.reopen(view)
                    apply(row) { restore(it) }
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
 * `TaskbarView.onLayout` centres its children against `getIconLayoutWidth()`,
 * and that width counts children rather than visible children. The launcher
 * discounts its own invisible ones — `getIconLayoutWidth` already subtracts the
 * pinned container's hidden entries — but the app drawer button and the divider
 * are direct children of the taskbar, so nothing discounts them. Hidden, they
 * still hold their slots, and everything left sits two slots right of centre.
 *
 * The width of one slot is the launcher's own: an icon plus its margin on each
 * side, which is exactly what `getIconLayoutWidth` adds per icon.
 */
private class TaskbarIconWidth private constructor(
    private val context: FeatureContext,
    private val iconLayoutWidth: Method,
    private val itemMargin: Field,
    private val iconTouchSize: Field,
) {

    /** How much of the freed width each row has given up, from 0 to 1. */
    private val closed = WeakHashMap<ViewGroup, Float>()
    private val running = WeakHashMap<ViewGroup, ValueAnimator>()

    /** @param isHidden whether this feature is the reason a child is not drawn */
    fun discount(isHidden: (View) -> Boolean) {
        context.xposed.hook(iconLayoutWidth).intercept { chain ->
            val width = chain.proceed() as Int
            val row = chain.thisObject as? ViewGroup

            if (row == null) {
                width
            } else {
                runCatching {
                    val hidden = (0 until row.childCount).count { isHidden(row.getChildAt(it)) }
                    val given = closed[row] ?: 0f
                    if (hidden == 0 || given == 0f) {
                        width
                    } else {
                        (width - (freedBy(hidden, row) * given).toInt()).coerceAtLeast(0)
                    }
                }.getOrDefault(width)
            }
        }
    }

    /** Slides what is left of the row into the middle. */
    fun closeUp(row: ViewGroup) = animate(row, 1f)

    /** Gives the width back, for a row that is about to hold the pair again. */
    fun reopen(row: ViewGroup) = animate(row, 0f)

    private fun animate(row: ViewGroup, target: Float) {
        val from = closed[row] ?: 0f
        running.remove(row)?.cancel()

        if (from == target) return

        // Nothing is on screen to animate for once the launcher has left, and a
        // row that is not laid out would never run the animation to its end.
        if (target == 0f || !row.isAttachedToWindow) {
            closed[row] = target
            row.requestLayout()
            return
        }

        running[row] = ValueAnimator.ofFloat(from, target).apply {
            duration = CLOSE_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                closed[row] = it.animatedValue as Float
                row.requestLayout()
            }
            start()
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

        /** Short enough to read as the row settling, not as a second transition. */
        const val CLOSE_MILLIS = 200L

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
