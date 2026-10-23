package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.appwidget.AppWidgetHostView
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The launcher members widget stacks move widgets with, resolved once at install.
 *
 * ```
 * LauncherAppWidgetHostView.mActivityContext          the Launcher
 * Launcher.mWorkspace, mModel, mItemInflater, getModelWriter()
 * ItemInflater.inflateItem$default(ItemInflater, ItemInfo, ViewGroup, int)   (info, null, 6) as the a11y delegate binds
 * WorkspaceLayoutManager.addInScreen(View, int container, int screenId, int x, int y, int spanX, int spanY)
 * CellLayout.removeView(View), markCellsAsOccupiedForView(View)
 * ModelWriter.modifyItemInDatabase(ItemInfo, int, int, int, int, int, int)
 * LauncherModel.mBgDataModel.itemsIdMap.itemsIdMap   SparseArray<ItemInfo>, read under the BgDataModel lock
 * ```
 *
 * These are the calls the launcher's own drag and drop makes to move a widget
 * between pages, so a stack moves widgets exactly as a person dragging them would.
 */
internal class StackHost(
    private val activityContext: Field,
    private val inflate: Method,
    private val addInScreen: Method,
    val id: Field,
    val screenId: Field,
    val cellX: Field,
    val cellY: Field,
    val spanX: Field,
    val spanY: Field,
    val itemType: Field,
    val container: Field,
    private val minSpanX: Field,
    private val minSpanY: Field,
    private val updateSizeRanges: Method?,
) {

    fun launcher(view: View): Any? = activityContext.get(view)

    fun workspace(launcher: Any): ViewGroup? = workspaceOf[launcher] as? ViewGroup

    /** The item [itemId] as the launcher's model holds it, or null. */
    fun modelItem(launcher: Any, itemId: Int): Any? = withItems(launcher, null) { it.get(itemId) }

    /** A new view for [item], inflated the way the launcher binds one; null for a widget it would not show. */
    fun inflate(launcher: Any, item: Any): View? {
        val inflater = itemInflaterOf[launcher] ?: return null
        return inflate.invoke(null, inflater, item, null, INFLATE_DEFAULTS) as? View
    }

    /** Places [view] on [workspace] at the cells [item] names, without changing [item]. */
    fun addAt(workspace: ViewGroup, view: View, item: Any) {
        // addInScreen sets the cells on params it reuses but not this flag; left from a reorder
        // (a widget the launcher placed for the picker), the page lays the view out at the
        // reorder's cells, over other items, though its row names the stack's.
        val params = view.layoutParams
        if (params != null) useTmpCoords.of(params)?.setBoolean(params, false)
        addInScreen.invoke(
            workspace, view, WidgetStacks.CONTAINER_DESKTOP, screenId.getInt(item),
            cellX.getInt(item), cellY.getInt(item), spanX.getInt(item), spanY.getInt(item),
        )
    }

    /**
     * Closes the long-press resize frame and any other floating view, as the launcher
     * does before it moves a widget; a frame left open outlines cells the widget has left.
     */
    fun closeFloating(launcher: Any) {
        val close = closeAllOpenViews
            ?: Reflect.declared(Class.forName(FLOATING_VIEW, false, launcher.javaClass.classLoader), "closeAllOpenViews") {
                it.parameterTypes.size == 1
            }?.also { closeAllOpenViews = it }
        close?.invoke(null, launcher)
    }

    /** Writes [item] to a new place through the launcher's own model writer. */
    fun move(launcher: Any, item: Any, screen: Int, x: Int, y: Int, width: Int, height: Int) {
        val getWriter = modelWriterOf ?: Reflect.method(launcher.javaClass, "getModelWriter")?.also { modelWriterOf = it }
        val writer = getWriter?.let { Invoke.noArgs(it, launcher) } ?: error("no model writer")
        val modify = modifyItem
            ?: Reflect.declared(writer.javaClass, "modifyItemInDatabase") { it.parameterTypes.size == 7 }?.also { modifyItem = it }
            ?: error("no modifyItemInDatabase")
        modify.invoke(writer, item, WidgetStacks.CONTAINER_DESKTOP, screen, x, y, width, height)
    }

    /** The view holding a page's items (`CellLayout.mShortcutsAndWidgets`; its getter is inlined away on 907). */
    fun itemsOf(page: View): ViewGroup? = shortcutsAndWidgetsOf[page] as? ViewGroup

    /** A copy of every item in the launcher's model, taken under its lock. */
    fun modelItems(launcher: Any): List<Any> = withItems(launcher, emptyList()) { items ->
        val all = ArrayList<Any>(items.size())
        for (i in 0 until items.size()) items.valueAt(i)?.let(all::add)
        all
    }

    /** The largest page id any home screen item in the model is on. */
    fun maxScreen(launcher: Any): Int = withItems(launcher, 0) { items ->
        var max = 0
        for (i in 0 until items.size()) {
            val item = items.valueAt(i) ?: continue
            if (container.getInt(item) == WidgetStacks.CONTAINER_DESKTOP) max = maxOf(max, screenId.getInt(item))
        }
        max
    }

    /** [read] on the model's `itemsIdMap` under the model's lock; [missing] when a link is gone. */
    private inline fun <T> withItems(launcher: Any, missing: T, read: (SparseArray<*>) -> T): T {
        val model = modelOf[launcher] ?: return missing
        val data = dataModelOf[model] ?: return missing
        val map = idMapOf[data] ?: return missing
        val items = idArrayOf[map] as? SparseArray<*> ?: return missing
        return synchronized(data) { read(items) }
    }

    // Launcher members found on first use and kept: each owner's class is the same for the
    // whole process, and a lookup walks the class chain with an exception per miss.
    private val workspaceOf = FieldOf("mWorkspace")
    private val itemInflaterOf = FieldOf("mItemInflater")
    private val modelOf = FieldOf("mModel")
    private val dataModelOf = FieldOf("mBgDataModel")
    private val idMapOf = FieldOf("itemsIdMap")
    private val idArrayOf = FieldOf("itemsIdMap")
    private val shortcutsAndWidgetsOf = FieldOf("mShortcutsAndWidgets")
    private val useTmpCoords = FieldOf("useTmpCoords")
    private var modelWriterOf: Method? = null
    private var modifyItem: Method? = null
    private var closeAllOpenViews: Method? = null

    /** Field [name] of an owner's class, looked up the first time it is found and kept. */
    private class FieldOf(private val name: String) {
        private var field: Field? = null

        fun of(owner: Any): Field? = field ?: Reflect.field(owner.javaClass, name)?.also { field = it }

        operator fun get(owner: Any): Any? = of(owner)?.get(owner)
    }

    /** Whether widget [item] can take [target]'s size: not below the smallest its app allows. */
    fun fits(item: Any, target: Any): Boolean =
        minSpanX.getInt(item).coerceAtLeast(1) <= spanX.getInt(target) &&
            minSpanY.getInt(item).coerceAtLeast(1) <= spanY.getInt(target)

    /** Tells [view]'s app its widget is now [width] x [height] cells, as a resize does. */
    fun resized(view: View, width: Int, height: Int) {
        val hostView = view as? AppWidgetHostView ?: return
        updateSizeRanges?.invoke(null, hostView, width, height)
    }

    fun isWidget(item: Any?): Boolean {
        if (item == null || !itemType.declaringClass.isInstance(item)) return false
        val type = itemType.getInt(item)
        return (type == WidgetStacks.TYPE_WIDGET || type == WidgetStacks.TYPE_CUSTOM_WIDGET) &&
            container.getInt(item) == WidgetStacks.CONTAINER_DESKTOP
    }

    /** A widget's name as its app gives it. */
    fun label(view: View?): CharSequence? =
        (view as? AppWidgetHostView)?.appWidgetInfo?.loadLabel(view.context.packageManager)

    companion object {
        /** `inflateItem$default`'s mask: parent and flags both left to their defaults. */
        private const val INFLATE_DEFAULTS = 6
        private const val INFLATE = "inflateItem"
        private const val FLOATING_VIEW = "com.android.launcher3.AbstractFloatingView"

        fun resolve(classLoader: ClassLoader): StackHost {
            fun cls(name: String) = Class.forName(name, false, classLoader)
            val hostView = cls("com.android.launcher3.widget.LauncherAppWidgetHostView")
            val itemInfo = cls("com.android.launcher3.model.data.ItemInfo")
            val inflater = cls("com.android.launcher3.util.ItemInflater")
            val workspace = cls("com.android.launcher3.Workspace")
            val cellLayout = cls("com.android.launcher3.CellLayout")
            val int = Int::class.javaPrimitiveType!!
            return StackHost(
                activityContext = Reflect.declaredField(hostView, "mActivityContext"),
                inflate = requireNotNull(Reflect.declared(inflater, "$INFLATE\$default") { it.parameterTypes.size == 4 }),
                addInScreen = workspace.getMethod("addInScreen", View::class.java, int, int, int, int, int, int),
                id = Reflect.declaredField(itemInfo, "id"),
                screenId = Reflect.declaredField(itemInfo, "screenId"),
                cellX = Reflect.declaredField(itemInfo, "cellX"),
                cellY = Reflect.declaredField(itemInfo, "cellY"),
                spanX = Reflect.declaredField(itemInfo, "spanX"),
                spanY = Reflect.declaredField(itemInfo, "spanY"),
                itemType = Reflect.declaredField(itemInfo, "itemType"),
                container = Reflect.declaredField(itemInfo, "container"),
                minSpanX = Reflect.declaredField(itemInfo, "minSpanX"),
                minSpanY = Reflect.declaredField(itemInfo, "minSpanY"),
                updateSizeRanges = runCatching {
                    Reflect.declaredMethod(
                        cls("com.android.launcher3.widget.util.WidgetSizeHandler"),
                        "updateSizeRanges", AppWidgetHostView::class.java, int, int,
                    ).apply { isAccessible = true }
                }.getOrNull(),
            )
        }
    }
}
