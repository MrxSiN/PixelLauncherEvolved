package my.github.MrxSiN.pixellauncherevolved.feature.dock

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup

import java.lang.ref.WeakReference
import java.lang.reflect.Field

import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The launcher's dock and the grid around it, as the launcher holds them.
 *
 * ```
 * Hotseat extends CellLayout
 *   public void setInsets(Rect)                      // attach and every profile change
 *   public boolean isValidDropTarget(DragObject)     // false while the icons are not VISIBLE
 *   Workspace mWorkspace
 * CellLayout
 *   ShortcutAndWidgetContainer mShortcutsAndWidgets  // the icons; the search bar is a sibling
 *   ActivityContext mActivity
 *   int mCountX, mCountY; boolean isOccupied(int, int)
 * Launcher.mModel -> LauncherModel.mBgDataModel -> BgDataModel.itemsIdMap
 *   -> WorkspaceData$MutableWorkspaceData.itemsIdMap (SparseArray<ItemInfo>)
 * ```
 *
 * The last dock the launcher laid out is held weakly, for the settings page and
 * for a live change; a recreated launcher replaces it on its first `setInsets`.
 * Every lookup here runs on a settings change or a profile change, never per frame.
 */
internal object Dock {

    const val HOTSEAT = "com.android.launcher3.Hotseat"
    const val CONTAINER_HOTSEAT = -101
    const val CONTAINER_DESKTOP = -100

    /** The grid's own dock capacity, recorded before it is raised; 0 until then. */
    @Volatile
    var stock: Int = 0

    /** The grid's own Home screen rows, recorded before a hidden dock adds one; 0 until then. */
    @Volatile
    var stockRows: Int = 0

    @Volatile
    private var last: WeakReference<ViewGroup>? = null

    /** The icon container of a hidden dock, which its alpha channels keep at 0; null while shown. */
    @Volatile
    @JvmField
    var hiddenIcons: View? = null

    fun hotseat(): ViewGroup? = last?.get()

    fun remember(hotseat: ViewGroup) {
        if (last?.get() !== hotseat) last = WeakReference(hotseat)
    }

    /** The view holding the dock's icons. */
    fun icons(hotseat: ViewGroup): View? = field(hotseat.javaClass, "mShortcutsAndWidgets")?.get(hotseat) as? View

    /** Whether this dock's profile has a taskbar, which mirrors the dock's apps over every app. */
    fun hasTaskbar(hotseat: ViewGroup): Boolean = profile(hotseat)?.let(::hasTaskbar) == true

    /** Whether a `DeviceProfile` has a taskbar. */
    fun hasTaskbar(profile: Any): Boolean {
        val properties = field(profile.javaClass, "deviceProperties")?.get(profile) ?: return false
        val taskbar = field(properties.javaClass, "taskbarConfiguration")?.get(properties) ?: return false
        return field(taskbar.javaClass, "isTaskbarPresent")?.getBoolean(taskbar) == true
    }

    /** The launcher's own dock count for this grid. */
    fun stockIcons(hotseat: ViewGroup): Int? {
        stock.takeIf { it > 0 }?.let { return it }
        val grid = grid(hotseat) ?: return null
        return field(grid.javaClass, "numDatabaseHotseatIcons")?.getInt(grid)
    }

    /** The Home screen rows the grid has now, the extra one included. */
    fun rows(hotseat: ViewGroup): Int? {
        val grid = grid(hotseat) ?: return null
        return field(grid.javaClass, "numRows")?.getInt(grid)
    }

    /** How many cells of at least [cell] px fit across this dock (`HotseatProfile.widthPx`) with [gap] px between them. */
    fun fits(hotseat: ViewGroup, cell: Float, gap: Int): Int? {
        val profile = profile(hotseat) ?: return null
        val dock = field(profile.javaClass, "hotseatProfile")?.get(profile) ?: return null
        val width = field(dock.javaClass, "widthPx")?.getInt(dock) ?: return null
        return DockIcons.fits(width, cell, gap)
    }

    /**
     * The pinned apps and folders at slot [count] or later, as their `ItemInfo`.
     *
     * Read off the bound icons, so a suggested app, which the launcher's
     * predictor places and removes itself, is never counted. Every slot the
     * dock shows is bound, so nothing a smaller count leaves out is missed.
     */
    fun pinnedFrom(hotseat: ViewGroup, count: Int): List<Any> {
        val icons = icons(hotseat) as? ViewGroup ?: return emptyList()
        val items = ArrayList<Pair<Int, Any>>()
        for (index in 0 until icons.childCount) {
            val item = icons.getChildAt(index).tag ?: continue
            if (int(item, "container") != CONTAINER_HOTSEAT) continue
            val rank = int(item, "screenId") ?: continue
            if (rank >= count) items += rank to item
        }
        items.sortBy { it.first }
        return items.map { it.second }
    }

    fun rankOf(item: Any): Int = int(item, "screenId") ?: 0

    /**
     * Home screen items, on every page including ones a Mode hides, that reach
     * below row [rows], read from the launcher's model.
     */
    fun homeItemsBelow(hotseat: ViewGroup, rows: Int): List<Any> {
        val below = ArrayList<Any>()
        forEachModelItem(hotseat) { item ->
            if (int(item, "container") != CONTAINER_DESKTOP) return@forEachModelItem
            val y = int(item, "cellY") ?: return@forEachModelItem
            val spanY = int(item, "spanY") ?: 1
            if (y + spanY > rows) below += item
        }
        return below
    }

    /**
     * Moves [items] onto the Home screen, above row [rows], through the
     * launcher's own `ModelWriter` (`Launcher.getModelWriter`).
     *
     * Each goes to the first free cells, page by page, that fit its span; what
     * does not fit on a page on show goes to a new page after every page the
     * model knows, Mode pages included, so nothing is ever left without a place.
     * Answers false, having moved nothing, only when the launcher cannot be reached.
     */
    fun moveToHome(hotseat: ViewGroup, items: List<Any>, rows: Int): Boolean {
        if (items.isEmpty()) return true
        val workspace = field(hotseat.javaClass, "mWorkspace")?.get(hotseat) as? ViewGroup ?: return false
        val launcher = field(hotseat.javaClass, "mActivity")?.get(hotseat) ?: return false
        val writer = Reflect.method(launcher.javaClass, "getModelWriter")?.invoke(launcher) ?: return false
        val modify = Reflect.declared(writer.javaClass, "modifyItemInDatabase") { it.parameterTypes.size == 7 } ?: return false
        val int = Int::class.javaPrimitiveType!!
        val screenIdOf = Reflect.method(workspace.javaClass, "getScreenIdForPageIndex", int) ?: return false

        val claimed = HashMap<Int, HashSet<Int>>()
        fun taken(screen: Int, x: Int, y: Int) = claimed[screen]?.contains(y * CLAIM_STRIDE + x) == true
        fun claim(screen: Int, x: Int, y: Int, spanX: Int, spanY: Int) {
            val cells = claimed.getOrPut(screen) { HashSet() }
            for (dy in 0 until spanY) for (dx in 0 until spanX) cells += (y + dy) * CLAIM_STRIDE + (x + dx)
        }

        var columns = 0
        var newScreen = -1
        for (item in items) {
            val spanX = int(item, "spanX")?.coerceAtLeast(1) ?: 1
            val spanY = int(item, "spanY")?.coerceAtLeast(1) ?: 1
            var placed: IntArray? = null

            for (page in 0 until workspace.childCount) {
                val layout = workspace.getChildAt(page)
                val occupied = Reflect.method(layout.javaClass, "isOccupied", int, int) ?: continue
                val pageColumns = field(layout.javaClass, "mCountX")?.getInt(layout) ?: continue
                val pageRows = minOf(field(layout.javaClass, "mCountY")?.getInt(layout) ?: continue, rows)
                val screen = screenIdOf.invoke(workspace, page) as Int
                if (screen < 0) continue
                columns = maxOf(columns, pageColumns)
                val cell = DockIcons.firstFree(pageColumns, pageRows, spanX, spanY) { x, y ->
                    taken(screen, x, y) || occupied.invoke(layout, x, y) == true
                } ?: continue
                placed = intArrayOf(screen, cell[0], cell[1])
                break
            }

            if (placed == null) {
                if (newScreen < 0) newScreen = maxScreen(hotseat) + 1
                var cell = DockIcons.firstFree(columns.coerceAtLeast(spanX), rows, spanX, spanY) { x, y -> taken(newScreen, x, y) }
                if (cell == null) {
                    newScreen++
                    cell = intArrayOf(0, 0)
                }
                placed = intArrayOf(newScreen, cell[0], cell[1])
            }

            claim(placed[0], placed[1], placed[2], spanX, spanY)
            modify.invoke(writer, item, CONTAINER_DESKTOP, placed[0], placed[1], placed[2], spanX, spanY)
        }
        return true
    }

    /**
     * Rebuilds the grid and every device profile from the stored settings, and
     * rebinds, the way a grid chosen in Wallpaper & style is applied
     * (`InvariantDeviceProfile.onConfigChanged`).
     */
    fun rebuildGrid(hotseat: ViewGroup) {
        val grid = grid(hotseat) ?: return
        Reflect.method(grid.javaClass, "onConfigChanged")?.invoke(grid)
    }

    /** The largest page id any Home screen item in the model is on. */
    private fun maxScreen(hotseat: ViewGroup): Int {
        var max = 0
        forEachModelItem(hotseat) { item ->
            if (int(item, "container") == CONTAINER_DESKTOP) max = maxOf(max, int(item, "screenId") ?: 0)
        }
        val workspace = field(hotseat.javaClass, "mWorkspace")?.get(hotseat) as? ViewGroup
        val screenIdOf = workspace?.let { Reflect.method(it.javaClass, "getScreenIdForPageIndex", Int::class.javaPrimitiveType!!) }
        if (workspace != null && screenIdOf != null) {
            for (page in 0 until workspace.childCount) max = maxOf(max, screenIdOf.invoke(workspace, page) as Int)
        }
        return max
    }

    /** Every item the launcher's model holds, read under the model's own lock. */
    private inline fun forEachModelItem(hotseat: ViewGroup, action: (Any) -> Unit) {
        val launcher = field(hotseat.javaClass, "mActivity")?.get(hotseat) ?: return
        val model = field(launcher.javaClass, "mModel")?.get(launcher) ?: return
        modelItems(model).forEach(action)
    }

    /** A copy of every item a `LauncherModel` holds, taken under the model's own lock; empty when unreachable. */
    fun modelItems(model: Any): List<Any> {
        val data = field(model.javaClass, "mBgDataModel")?.get(model) ?: return emptyList()
        val workspace = field(data.javaClass, "itemsIdMap")?.get(data) ?: return emptyList()
        val items = field(workspace.javaClass, "itemsIdMap")?.get(workspace) as? SparseArray<*> ?: return emptyList()
        return synchronized(data) { (0 until items.size()).mapNotNull { items.valueAt(it) } }
    }

    private fun grid(hotseat: ViewGroup): Any? {
        val profile = profile(hotseat) ?: return null
        return field(profile.javaClass, "inv")?.get(profile)
    }

    fun profile(hotseat: ViewGroup): Any? {
        val activity = field(hotseat.javaClass, "mActivity")?.get(hotseat) ?: return null
        return Reflect.method(activity.javaClass, "getDeviceProfile")?.invoke(activity)
    }

    fun titleOf(item: Any): CharSequence = field(item.javaClass, "title")?.get(item) as? CharSequence ?: ""

    private fun int(item: Any, name: String): Int? = field(item.javaClass, name)?.getInt(item)

    // ponytail: uncached lookups, every caller is a settings change or a profile change.
    private fun field(type: Class<*>, name: String): Field? = Reflect.field(type, name)

    /** Wider than any grid, so `y * stride + x` names one cell. */
    private const val CLAIM_STRIDE = 64
}
