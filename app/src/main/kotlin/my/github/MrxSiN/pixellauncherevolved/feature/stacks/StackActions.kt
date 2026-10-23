package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides

/**
 * What a stack's menu and editor do to the widgets in it.
 *
 * Every change goes through the launcher's own paths: widgets move with its
 * model writer and are deleted with its own delete (`ItemDeleteHandler.removeItem`,
 * which releases the widget id and offers its undo), so nothing here writes the
 * database or the widget host directly. The stored stacks are updated before a
 * delete, so the delete's own hook ([onDeleted]) never mistakes a widget the
 * person removed on purpose for one to replace.
 *
 * ```
 * Launcher.getActivityComponent().getItemDeleteHandler()
 *   ItemDeleteHandler.removeItem(ItemInfo, boolean deleteFromDb, String reason)
 *   ItemDeleteHandler.completeDelete(ItemInfo, String)       the delete is final (its undo has gone)
 * WorkspaceLongPressOptions.openWidgetPicker(Context)        the launcher's own widget picker
 * Launcher.completeAddAppWidget(int appWidgetId, ItemInfo, AppWidgetHostView, ...)   a picked widget is on a page
 * Workspace.getWidgetForAppWidgetId(int), getScreenIdForPageIndex(int); CellLayout.mCountX, mCountY
 * ```
 */
internal class StackActions(
    private val host: StackHost,
    private val swipe: StackSwipe,
    private val logger: Logger,
    private val classLoader: ClassLoader,
) {

    /** The stack a widget picked in the launcher's picker joins, by its widget on show, until [pendingUntil]. */
    private var pendingTarget = 0
    private var pendingUntil = 0L

    /** Moves [widget] into [target]'s stack, making one when [target] is on its own; it takes [target]'s size. */
    fun stack(launcher: Any, widget: View, target: View): Boolean {
        val item = widget.tag
        val id = host.id.getInt(item)
        val targetId = host.id.getInt(target.tag)
        val existing = WidgetStacks.graph.stackOf(targetId)
        if (existing?.isFull == true || WidgetStacks.graph.stackOf(id) != null || !host.fits(item, target.tag)) return false
        val next = existing?.plus(id) ?: StackGraph.Stack(intArrayOf(targetId, id), 0)
        val page = maxOf(host.maxScreen(launcher), WidgetStacks.hiddenScreens.lastOrNull() ?: 0) + 1
        val width = host.spanX.getInt(target.tag)
        val height = host.spanY.getInt(target.tag)

        host.closeFloating(launcher)
        (widget.parent?.parent as? ViewGroup)?.removeView(widget)
        widget.visibility = View.VISIBLE
        host.move(launcher, item, page, 0, 0, width, height)
        host.resized(widget, width, height)
        swipe.keep(id, widget)
        WidgetStacks.store(
            WidgetStacks.graph.replace(existing, next),
            WidgetStacks.hiddenScreens + page,
            if (existing == null) WidgetStacks.anchors + targetId else WidgetStacks.anchors,
        )
        swipe.stacked(target)
        return true
    }

    /** Opens the launcher's own widget picker; the widget picked there joins [target]'s stack. */
    fun pickInto(launcher: Any, target: View) {
        pendingTarget = host.id.getInt(target.tag)
        pendingUntil = SystemClock.uptimeMillis() + PICK_WINDOW_MS
        val options = Class.forName(PICKER_OWNER, false, classLoader)
        Reflect.declaredMethod(options, "openWidgetPicker", android.content.Context::class.java)
            .apply { isAccessible = true }
            .invoke(null, launcher)
    }

    /**
     * A widget has just been added to a page. When the picker was opened for a stack
     * moments ago, it joins that stack; false when there was none, or it could not.
     */
    fun onWidgetAdded(launcher: Any, appWidgetId: Int): Boolean {
        val target = pendingTarget
        if (target == 0 || SystemClock.uptimeMillis() > pendingUntil) return false
        pendingTarget = 0
        val workspace = host.workspace(launcher) ?: return false
        val added = Reflect.method(workspace.javaClass, "getWidgetForAppWidgetId", Int::class.javaPrimitiveType!!)
            ?.invoke(workspace, appWidgetId) as? View ?: return false
        val into = viewOf(launcher, target) ?: return false
        if (!stack(launcher, added, into)) return false
        // With no room on a page the launcher made one for the widget and moved there; that page
        // is empty now. Hidden stack pages hold no view, so the strip cannot take them.
        Reflect.method(workspace.javaClass, "stripEmptyScreens")?.invoke(workspace)
        val page = (workspace as ViewGroup).indexOfChild(into.parent?.parent as? View)
        if (page >= 0) Reflect.method(workspace.javaClass, "snapToPage", Int::class.javaPrimitiveType!!)?.invoke(workspace, page)
        return true
    }

    /**
     * Takes member [id] out of its stack and puts it on the home screen: in the first
     * free cells of the stack's page, else of any page shown, else on its own page
     * (where it already is), which then shows like any other. Nothing is ever deleted.
     */
    fun extract(launcher: Any, anchor: View, id: Int) {
        val stack = WidgetStacks.graph.stackOf(id) ?: return
        if (id == stack.shownId) {
            val other = if (stack.shown + 1 < stack.members.size) stack.shown + 1 else stack.shown - 1
            if (!swipe.showNow(anchor, stack, other)) return
        }
        val current = WidgetStacks.graph.stackOf(id) ?: return
        place(launcher, anchor, id)
        swipe.drop(id)
        storeWithout(launcher, current, id)
        ModeOverrides.rebindWorkspace?.invoke()
    }

    /** Puts every widget waiting in every stack back on the home screen; how many were. */
    fun restoreAll(launcher: Any): Int {
        val workspace = host.workspace(launcher) ?: return 0
        var restored = 0
        for (stack in WidgetStacks.graph.stacks) {
            val anchor = viewOf(launcher, stack.shownId)
            for (id in stack.members) {
                if (id == stack.shownId) continue
                if (anchor != null) place(launcher, anchor, id) else firstPageCell(launcher, workspace, id)
                swipe.drop(id)
                restored++
            }
        }
        WidgetStacks.store(StackGraph.EMPTY, IntArray(0), IntArray(0))
        ModeOverrides.rebindWorkspace?.invoke()
        return restored
    }

    /** Deletes member [id] the way the launcher deletes a widget; the one on show hands its place on first. */
    fun delete(launcher: Any, anchor: View, id: Int) {
        val stack = WidgetStacks.graph.stackOf(id) ?: return
        if (id == stack.shownId) {
            val other = if (stack.shown + 1 < stack.members.size) stack.shown + 1 else stack.shown - 1
            if (!swipe.showNow(anchor, stack, other)) return
        }
        val current = WidgetStacks.graph.stackOf(id) ?: return
        val item = host.modelItem(launcher, id) ?: return
        storeWithout(launcher, current, id)
        swipe.drop(id)
        remove(launcher, item)
        if (current.members.size <= 2) ModeOverrides.rebindWorkspace?.invoke()
    }

    /** Deletes every widget in [anchor]'s stack, the way the launcher deletes widgets. */
    fun deleteStack(launcher: Any, anchor: View) {
        val stack = WidgetStacks.graph.stackOf(host.id.getInt(anchor.tag)) ?: return
        val items = stack.members.toList().mapNotNull { host.modelItem(launcher, it) }
        WidgetStacks.store(
            WidgetStacks.graph.replace(stack, null),
            WidgetStacks.hiddenScreens.filter { screen -> items.none { host.screenId.getInt(it) == screen } }.toIntArray(),
            WidgetStacks.anchors.filter { it != stack.shownId }.toIntArray(),
        )
        for (id in stack.members) swipe.drop(id)
        for (item in items) remove(launcher, item)
    }

    /**
     * The launcher has finished deleting [item] (its undo has gone). When it was a
     * stack's widget on show, the next widget in the stack takes its cells, so the
     * stack goes on rather than leaving a gap and its widgets on pages of their own.
     */
    fun onDeleted(launcher: Any, item: Any) {
        if (!host.isWidget(item)) return
        val id = host.id.getInt(item)
        val stack = WidgetStacks.graph.stackOf(id)?.takeIf { it.shownId == id } ?: return
        val nextIndex = if (stack.shown + 1 < stack.members.size) stack.shown + 1 else stack.shown - 1
        val next = host.modelItem(launcher, stack.members[nextIndex]) ?: return
        host.move(
            launcher, next, host.screenId.getInt(item), host.cellX.getInt(item), host.cellY.getInt(item),
            host.spanX.getInt(item), host.spanY.getInt(item),
        )
        swipe.drop(stack.members[nextIndex])
        val left = stack.without(id)
        WidgetStacks.store(
            WidgetStacks.graph.replace(stack, left?.showing(left.indexOf(stack.members[nextIndex]))),
            WidgetStacks.hiddenScreens.filter { it != host.screenId.getInt(item) }.toIntArray(),
            WidgetStacks.anchors.map { if (it == id) stack.members[nextIndex] else it }.toIntArray(),
        )
        ModeOverrides.rebindWorkspace?.invoke()
    }

    /** The widget view on a page for item [id], or null. */
    fun viewOf(launcher: Any, id: Int): View? {
        val workspace = host.workspace(launcher) ?: return null
        for (page in 0 until workspace.childCount) {
            val items = host.itemsOf(workspace.getChildAt(page)) ?: continue
            for (i in 0 until items.childCount) {
                val child = items.getChildAt(i)
                val tag = child.tag
                if (tag != null && host.id.declaringClass.isInstance(tag) && host.id.getInt(tag) == id) return child
            }
        }
        return null
    }

    private fun storeWithout(launcher: Any, stack: StackGraph.Stack, id: Int) {
        val left = stack.without(id)
        val freed = host.modelItem(launcher, id)?.let { host.screenId.getInt(it) }
        WidgetStacks.store(
            WidgetStacks.graph.replace(stack, left),
            WidgetStacks.hiddenScreens.filter { it != freed }.toIntArray(),
            if (left == null) WidgetStacks.anchors.filter { it != stack.shownId }.toIntArray() else WidgetStacks.anchors,
        )
    }

    private fun remove(launcher: Any, item: Any) {
        val component = Reflect.method(launcher.javaClass, "getActivityComponent")?.invoke(launcher) ?: error("no activity component")
        val handler = Reflect.method(component.javaClass, "getItemDeleteHandler")?.invoke(component) ?: error("no delete handler")
        val removeItem = Reflect.declared(handler.javaClass, "removeItem") { it.parameterTypes.size == 3 } ?: error("no removeItem")
        removeItem.invoke(handler, item, true, REASON)
    }

    /** Moves member [id] to the first free cells on [anchor]'s page, else on any page shown. */
    private fun place(launcher: Any, anchor: View, id: Int) {
        val workspace = host.workspace(launcher) ?: return
        val anchorScreen = host.screenId.getInt(anchor.tag)
        if (!placeOn(launcher, workspace, id, anchorScreen)) firstPageCell(launcher, workspace, id)
    }

    private fun firstPageCell(launcher: Any, workspace: ViewGroup, id: Int) {
        val screenOf = Reflect.method(workspace.javaClass, "getScreenIdForPageIndex", Int::class.javaPrimitiveType!!) ?: return
        for (page in 0 until workspace.childCount) {
            val screen = screenOf.invoke(workspace, page) as Int
            if (screen >= 0 && placeOn(launcher, workspace, id, screen)) return
        }
    }

    /** Moves member [id] into free cells of page [screen]; false when it does not fit there. */
    private fun placeOn(launcher: Any, workspace: ViewGroup, id: Int, screen: Int): Boolean {
        val item = host.modelItem(launcher, id) ?: return false
        val spanX = host.spanX.getInt(item)
        val spanY = host.spanY.getInt(item)
        val page = (0 until workspace.childCount).map { workspace.getChildAt(it) }.firstOrNull() ?: return false
        val columns = Reflect.field(page.javaClass, "mCountX")?.getInt(page) ?: return false
        val rows = Reflect.field(page.javaClass, "mCountY")?.getInt(page) ?: return false
        val taken = Array(columns) { BooleanArray(rows) }
        for (other in host.modelItems(launcher)) {
            if (host.container.getInt(other) != WidgetStacks.CONTAINER_DESKTOP || host.screenId.getInt(other) != screen) continue
            for (x in host.cellX.getInt(other) until minOf(columns, host.cellX.getInt(other) + host.spanX.getInt(other))) {
                for (y in host.cellY.getInt(other) until minOf(rows, host.cellY.getInt(other) + host.spanY.getInt(other))) {
                    if (x >= 0 && y >= 0) taken[x][y] = true
                }
            }
        }
        val cell = StackPlacement.firstFree(columns, rows, spanX, spanY) { x, y -> taken[x][y] } ?: return false
        host.move(launcher, item, screen, cell[0], cell[1], spanX, spanY)
        return true
    }

    private companion object {
        const val PICKER_OWNER = "com.android.launcher3.popup.WorkspaceLongPressOptions"
        const val PICK_WINDOW_MS = 10 * 60 * 1000L
        const val REASON = "pixel_launcher_evolved_widget_stack"
    }
}

/** Where a widget fits in a page's grid, as a pure function a test can hold. */
internal object StackPlacement {

    /** The first top-left cell, row by row, where [spanX] x [spanY] cells are all free; null when none. */
    inline fun firstFree(columns: Int, rows: Int, spanX: Int, spanY: Int, taken: (Int, Int) -> Boolean): IntArray? {
        for (y in 0..rows - spanY) {
            for (x in 0..columns - spanX) {
                var free = true
                for (dy in 0 until spanY) {
                    for (dx in 0 until spanX) if (taken(x + dx, y + dy)) free = false
                }
                if (free) return intArrayOf(x, y)
            }
        }
        return null
    }
}
