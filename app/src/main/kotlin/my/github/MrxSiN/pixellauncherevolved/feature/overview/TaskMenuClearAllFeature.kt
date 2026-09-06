package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Adds a Clear all entry to the menu behind a task card's app chip.
 *
 * The launcher's own menu already offers Clear, which dismisses that one task;
 * this adds the sweep. It pairs with hiding Clear all from the action row: the
 * action stays reachable without a button permanently occupying the bottom of
 * Overview.
 *
 * The row is built from the launcher's own `task_view_menu_option` layout, with
 * the launcher's own icon and label, so it is indistinguishable from the entries
 * above it and follows the theme. Building a real `SystemShortcut` would mean
 * subclassing a launcher class at runtime, which buys nothing here.
 */
class TaskMenuClearAllFeature : ToggleFeature(Settings.OVERVIEW_CLEAR_ALL_IN_MENU) {

    override fun install(context: FeatureContext) {
        val taskMenuView = context.findClass(TASK_MENU_VIEW_CLASS)
        if (taskMenuView == null) {
            context.logger.warn("Overview task menu is not available in this launcher")
            return
        }

        context.hookAfter(taskMenuView, "addMenuOptions") { menu, _ ->
            // Read here rather than at install time so the toggle reaches a
            // running launcher the next time the menu opens.
            if (context.settings[toggle]) addEntry(menu as ViewGroup, context.logger)
        }
    }

    private fun addEntry(menu: ViewGroup, logger: Logger) {
        val resources = LauncherResources(menu.context)

        val optionLayout = menu.findViewById<ViewGroup>(resources.id(OPTION_LAYOUT_ID))
        if (optionLayout == null) {
            logger.warn("Task menu has no option layout to extend")
            return
        }

        if (optionLayout.findViewWithTag<View>(VIEW_TAG) != null) return

        val rowLayout = resources.layout(OPTION_ROW_LAYOUT)
        if (rowLayout == 0) {
            logger.warn("Task menu option layout is missing")
            return
        }

        val row = LayoutInflater.from(menu.context).inflate(rowLayout, optionLayout, false)
        row.tag = VIEW_TAG

        // The launcher's own option rows carry their glyph as the icon view's
        // background rather than as an image, so this matches.
        row.findViewById<View>(resources.id(ICON_ID))?.background = resources.drawable(ICON)
        row.findViewById<TextView>(resources.id(TEXT_ID))?.text = resources.string(LABEL, "Clear all")

        row.setOnClickListener { clicked -> dismissAllTasks(menu, clicked, logger) }
        optionLayout.addView(row)
    }

    /**
     * Runs the launcher's own clear-all, then closes the menu.
     *
     * The recents view is reached through the menu's container rather than by
     * walking the view tree: the menu is a floating view and is not a descendant
     * of the task list it acts on. `dismissAllTasks` is private and declared on
     * a base class, so it is looked up through the hierarchy.
     */
    private fun dismissAllTasks(menu: ViewGroup, clicked: View, logger: Logger) {
        try {
            val container = Reflect.field(menu.javaClass, CONTAINER_FIELD)?.get(menu) ?: return
            val recentsView =
                Reflect.method(container.javaClass, GET_OVERVIEW_PANEL)?.invoke(container) ?: return

            val dismissAll =
                Reflect.method(recentsView.javaClass, DISMISS_ALL_TASKS, View::class.java)
            if (dismissAll == null) {
                logger.warn("Launcher exposes no clear-all action")
                return
            }

            Reflect.method(menu.javaClass, CLOSE, Boolean::class.javaPrimitiveType!!)
                ?.invoke(menu, true)

            dismissAll.invoke(recentsView, clicked)
        } catch (error: Throwable) {
            logger.warn("Unable to clear all tasks from the task menu", error)
        }
    }

    private companion object {
        const val TASK_MENU_VIEW_CLASS = "com.android.quickstep.views.TaskMenuView"

        const val CONTAINER_FIELD = "recentsViewContainer"
        const val GET_OVERVIEW_PANEL = "getOverviewPanel"
        const val DISMISS_ALL_TASKS = "dismissAllTasks"
        const val CLOSE = "close"

        const val OPTION_LAYOUT_ID = "menu_option_layout"
        const val OPTION_ROW_LAYOUT = "task_view_menu_option"
        const val ICON_ID = "icon"
        const val TEXT_ID = "text"
        const val ICON = "ic_remove_task_option"
        const val LABEL = "recents_clear_all"

        const val VIEW_TAG = "pixellauncherevolved:clear_all_option"
    }
}
