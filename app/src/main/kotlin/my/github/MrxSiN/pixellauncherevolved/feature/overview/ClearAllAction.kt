package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.content.Context
import android.view.View

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Dismisses every task, using the launcher's own clear-all.
 *
 * Shared by the two places this module offers the action from, so both dismiss
 * tasks exactly the way the launcher does rather than each inventing a way.
 *
 * The recents view is found from the calling view's context rather than by
 * walking up from it: neither the action row nor the task menu is a descendant
 * of the task list they act on.
 */
class ClearAllAction(
    private val classLoader: ClassLoader,
    private val logger: Logger,
) {

    fun run(source: View): Boolean = try {
        val recentsView = recentsView(source)
        val dismissAll = recentsView?.let {
            Reflect.method(it.javaClass, DISMISS_ALL_TASKS, View::class.java)
        }

        if (dismissAll == null) {
            logger.warn("Launcher exposes no clear-all action")
            false
        } else {
            dismissAll.invoke(recentsView, source)
            true
        }
    } catch (error: Throwable) {
        logger.warn("Unable to clear all tasks", error)
        false
    }

    private fun recentsView(source: View): Any? {
        val containerType = runCatching {
            Class.forName(CONTAINER_CLASS, false, classLoader)
        }.getOrNull() ?: return null

        val container = Reflect.method(containerType, CONTAINER_FROM_CONTEXT, Context::class.java)
            ?.invoke(null, source.context)
            ?: return null

        return Reflect.method(container.javaClass, GET_OVERVIEW_PANEL)?.invoke(container)
    }

    private companion object {
        const val CONTAINER_CLASS = "com.android.quickstep.views.RecentsViewContainer"
        const val CONTAINER_FROM_CONTEXT = "containerFromContext"
        const val GET_OVERVIEW_PANEL = "getOverviewPanel"
        const val DISMISS_ALL_TASKS = "dismissAllTasks"
    }
}
