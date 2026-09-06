package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewParent

import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/** Takes the Overview screen out of the way before another surface appears. */
interface OverviewCloser {

    /**
     * Restores whatever Overview was opened over, then runs [afterClosed].
     *
     * That is the app the user came from when Overview was entered from one,
     * and the home screen otherwise. Implementations must run the callback
     * exactly once, including when there is nothing to close.
     */
    fun close(taskView: View, afterClosed: Runnable)
}

/**
 * Puts Overview away through the launcher's own transitions.
 *
 * A bubble raised while Overview is still up ends up behind it, so Overview has
 * to settle first. What it settles onto should be whatever the user was looking
 * at before: the app Overview was opened from, or the home screen when it was
 * opened from home. Bubbling from Overview then reads as adding a bubble to the
 * session in progress rather than as leaving it.
 *
 * Both transitions are asked for by method name on the first ancestor of the
 * task card that answers to them, which keeps this free of launcher class names
 * and covers `LauncherRecentsView` as well as the fallback surfaces.
 */
class RecentsViewOverviewCloser(private val logger: Logger) : OverviewCloser {

    override fun close(taskView: View, afterClosed: Runnable) {
        val recentsView = findRecentsView(taskView)

        if (recentsView != null &&
            (returnToPreviousApp(recentsView, taskView, afterClosed) ||
                returnHome(recentsView, afterClosed))
        ) {
            return
        }

        // Nothing to put away, or every transition was refused: continue rather
        // than dropping the request.
        afterClosed.run()
    }

    /** The owner of the task cards is the first ancestor that tracks a running task. */
    private fun findRecentsView(taskView: View): ViewParent? {
        var parent = taskView.parent

        while (parent is View) {
            if (method(parent, GET_RUNNING_TASK_VIEW) != null) return parent
            parent = parent.parent
        }

        return null
    }

    /**
     * Relaunches the task Overview was opened from.
     *
     * Skipped when Overview was opened from home, and when the card being
     * bubbled is that same running task: restoring an app full screen only to
     * immediately bubble it would fight itself, so that case goes home instead.
     */
    private fun returnToPreviousApp(
        recentsView: ViewParent,
        taskView: View,
        afterClosed: Runnable,
    ): Boolean {
        val launchCallbacks = try {
            val runningTaskView = method(recentsView, GET_RUNNING_TASK_VIEW)?.invoke(recentsView)
            if (runningTaskView == null || runningTaskView === taskView) return false

            val launch = method(runningTaskView, LAUNCH_WITH_ANIMATION) ?: return false
            launch.invoke(runningTaskView)
        } catch (error: Throwable) {
            logger.warn("Unable to restore the previous app before opening a bubble", error)
            return false
        }

        // The relaunch has started. From here the bubble has to follow it, so
        // every remaining failure raises it immediately rather than starting
        // another transition.
        try {
            val add = launchCallbacks?.let { method(it, RUNNABLE_LIST_ADD, Runnable::class.java) }
            if (add != null) {
                // A completed launch list runs late additions immediately, so
                // the bubble is raised either way.
                add.invoke(launchCallbacks, afterClosed)
                return true
            }
        } catch (error: Throwable) {
            logger.warn("Unable to chain the bubble onto the app relaunch", error)
        }

        afterClosed.run()
        return true
    }

    private fun returnHome(recentsView: ViewParent, afterClosed: Runnable): Boolean = try {
        val startHome = method(recentsView, START_HOME, Runnable::class.java)
        if (startHome == null || !canStartHomeSafely(recentsView)) {
            logger.info("Overview declined a home transition; opening the bubble directly")
            false
        } else {
            startHome.invoke(recentsView, afterClosed)
            true
        }
    } catch (error: Throwable) {
        logger.warn("Unable to close Overview before opening a bubble", error)
        false
    }

    /** Absence of the guard is treated as permission, matching launcher defaults. */
    private fun canStartHomeSafely(recentsView: ViewParent): Boolean =
        method(recentsView, CAN_START_HOME_SAFELY)?.invoke(recentsView) as? Boolean ?: true

    private companion object {
        const val GET_RUNNING_TASK_VIEW = "getRunningTaskView"
        const val LAUNCH_WITH_ANIMATION = "launchWithAnimation"
        const val RUNNABLE_LIST_ADD = "add"
        const val START_HOME = "startHome"
        const val CAN_START_HOME_SAFELY = "canStartHomeSafely"

        fun method(target: Any, name: String, vararg parameterTypes: Class<*>): Method? =
            runCatching { target.javaClass.getMethod(name, *parameterTypes) }.getOrNull()
    }
}
