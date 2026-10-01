package my.github.MrxSiN.pixellauncherevolved.feature.dock

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup

import java.lang.reflect.Field

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Draws dock icons no wider than their cell.
 *
 * A `BubbleTextView` takes its icon size, `mIconSize`, from the Home screen's
 * profile once, in its constructor, and everything about the icon reads it
 * back: the drawable's bounds (`setIcon`), `getIconBounds`, and through that
 * the notification dot, the drag outline and the app-open animation. So a dock
 * icon is resized by writing `mIconSize` and setting its icon again, which
 * keeps all of those on one size, rather than by scaling the view.
 *
 * Checked where the launcher places every icon, `CellLayout.addViewToCellLayout`:
 * an icon going into the dock gets its cell's size, and an icon that was shrunk
 * and is going anywhere else (dragged to the Home screen or into a folder) gets
 * its own size back. A folder in the dock has no single icon size, so it is
 * scaled instead ([View.setScaleX]); its drop target is its cell, and [FolderCells]
 * makes its whole cell its tap target too.
 * Bind and drop only, never per frame.
 */
internal object DockIconSize {

    /** Least room between two dock icons. */
    const val GAP_DP = 8

    /** Each dock cell keeps a 48dp touch target, with half a dp for rounding. */
    const val MIN_CELL_DP = 47.5f

    fun gapPx(): Int = (GAP_DP * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    fun minCellPx(): Float = MIN_CELL_DP * android.content.res.Resources.getSystem().displayMetrics.density

    /** Icons this module shrank, with the size to give back (0 for a scaled folder). */
    private val shrunk = java.util.WeakHashMap<View, Int>()

    private var hotseatClass: Class<*>? = null
    private var bubble: Class<*>? = null
    private var folder: Class<*>? = null
    private var predicted: Class<*>? = null
    private var iconSizeOf: Field? = null
    private var iconOf: Field? = null
    private var normalizedOf: Field? = null
    private var setIcon: java.lang.reflect.Method? = null
    private var ringPaths: List<java.lang.reflect.Method> = emptyList()

    fun install(context: FeatureContext) {
        val cellLayout = requireNotNull(context.findClass(CELL_LAYOUT))
        hotseatClass = requireNotNull(context.findClass(Dock.HOTSEAT))
        val bubble = requireNotNull(context.findClass(BUBBLE_TEXT_VIEW)).also { this.bubble = it }
        folder = context.findClass(FOLDER_ICON)
        predicted = context.findClass(PREDICTED_APP_ICON)
        iconSizeOf = Reflect.declaredField(bubble, "mIconSize")
        iconOf = Reflect.declaredField(bubble, "mIcon")
        setIcon = requireNotNull(Reflect.declared(bubble, "setIcon") { it.parameterTypes.size == 1 })
        predicted?.let { type ->
            normalizedOf = Reflect.field(type, "mNormalizedIconSize")
            ringPaths = listOfNotNull(
                Reflect.declared(type, "updateRingPath") { it.parameterTypes.isEmpty() },
                Reflect.declared(type, "updateShapePath") { it.parameterTypes.isEmpty() },
            ).onEach { it.isAccessible = true }
        }
        val add = requireNotNull(Reflect.declared(cellLayout, "addViewToCellLayout") { it.parameterTypes.size == 4 })

        context.xposed.hook(add).intercept { chain ->
            chain.proceed().also {
                val child = chain.args[0] as? View ?: return@also
                runCatching { fit(child, chain.thisObject as ViewGroup) }
                    .onFailure { context.logger.warn("Unable to size a dock icon", it) }
            }
        }
    }

    /**
     * Fits every icon now in [hotseat] to its current cells.
     *
     * Run after the dock's profile changes: suggested apps are kept and
     * re-bound in place (`PredictedAppIcon.applyFromWorkspaceItemWithAnimation`)
     * rather than added again, so they would keep the old size.
     */
    fun fitAll(hotseat: ViewGroup) {
        val icons = Dock.icons(hotseat) as? ViewGroup ?: return
        for (index in 0 until icons.childCount) fit(icons.getChildAt(index), hotseat)
    }

    /** Gives [child], just placed in [layout], the size that layout's cells call for. */
    private fun fit(child: View, layout: ViewGroup) {
        val isBubble = bubble?.isInstance(child) == true
        val isFolder = folder?.isInstance(child) == true
        if (!isBubble && !isFolder) return

        val own = shrunk[child]
        if (hotseatClass?.isInstance(layout) != true) {
            if (own == null) return
            shrunk.remove(child)
            if (isBubble) resize(child, own) else unscale(child)
            return
        }

        val cell = cellOf(layout) ?: return
        if (isBubble) {
            val natural = own ?: iconSizeOf!!.getInt(child)
            val size = minOf(natural, cell)
            if (size < natural) shrunk[child] = natural else shrunk.remove(child)
            resize(child, size)
        } else {
            val scale = minOf(1f, cell / iconPx(layout).coerceAtLeast(1f))
            if (scale < 1f) {
                shrunk[child] = 0
                (child.parent as? ViewGroup)?.let { icons ->
                    if (icons.touchDelegate !is FolderCells) icons.touchDelegate = FolderCells(icons)
                }
                // Scaled so its preview lands where the shrunk app icons sit:
                // their centre is half the cell's slack plus half the new size.
                folderPivot(layout, scale)?.let { (x, y) ->
                    child.pivotX = x
                    child.pivotY = y
                }
                child.scaleX = scale
                child.scaleY = scale
            } else if (own != null) {
                shrunk.remove(child)
                unscale(child)
            }
        }
    }

    /**
     * Gives a scaled dock folder its whole cell as its touch target.
     *
     * A view's scale shrinks the area its parent hit-tests, so a folder scaled
     * to 34dp would miss taps in the rest of its 48dp cell. Those taps reach no
     * child and fall to the container's own `onTouchEvent`, which asks this
     * delegate first; it hands the gesture to the scaled folder whose unscaled
     * bounds, its cell, hold the touch. Allocation-free per touch.
     */
    private class FolderCells(private val icons: ViewGroup) : TouchDelegate(Rect(), icons) {

        private var target: View? = null

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) target = folderAt(event.x.toInt(), event.y.toInt())
            val folder = target ?: return false
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) target = null
            // Within the folder's own (unscaled) frame, so its tap and long press fire.
            event.setLocation(folder.width / 2f, folder.height / 2f)
            return folder.dispatchTouchEvent(event)
        }

        private fun folderAt(x: Int, y: Int): View? {
            for (index in 0 until icons.childCount) {
                val child = icons.getChildAt(index)
                if (shrunk[child] == 0 && x >= child.left && x < child.right && y >= child.top && y < child.bottom) return child
            }
            return null
        }
    }

    private fun unscale(child: View) {
        child.resetPivot()
        child.scaleX = 1f
        child.scaleY = 1f
    }

    private fun resize(icon: View, size: Int) {
        val sizeOf = iconSizeOf ?: return
        val old = sizeOf.getInt(icon)
        if (old == size || old <= 0) return
        sizeOf.setInt(icon, size)
        // A suggested app draws its ring from a size taken in its constructor.
        if (predicted?.isInstance(icon) == true) {
            normalizedOf?.let { it.setInt(icon, it.getInt(icon) * size / old) }
            for (path in ringPaths) path.invoke(icon)
        }
        iconOf?.get(icon)?.let { setIcon?.invoke(icon, it) }
        icon.requestLayout()
        icon.invalidate()
    }

    /** The width of one dock cell, from the dock's profile. */
    private fun cellOf(hotseat: ViewGroup): Int? {
        val dock = dockProfile(hotseat) ?: return null
        val width = Reflect.field(dock.javaClass, "widthPx")?.getInt(dock) ?: return null
        val gap = Reflect.field(dock.javaClass, "borderSpace")?.getInt(dock) ?: return null
        val count = Reflect.field(dock.javaClass, "numShownIcons")?.getInt(dock) ?: return null
        return DockIcons.cellWidth(width, gap, count)
    }

    /**
     * The pivot that scales a dock folder by [scale] onto the app icons' centre.
     *
     * An app icon sits `(cellHeight - icon) / 2` from its cell's top; a folder's
     * preview sits `FolderProfile.folderIconOffsetYPx` lower than that. Scaling
     * `c` about `p` gives `p + s * (c - p)`, so the pivot for a target `t` is
     * `(t - s * c) / (1 - s)`.
     */
    private fun folderPivot(hotseat: ViewGroup, scale: Float): Pair<Float, Float>? {
        if (scale >= 1f) return null
        val profile = profileOf(hotseat) ?: return null
        val dock = dockProfile(hotseat) ?: return null
        val folders = Reflect.field(profile.javaClass, "folderProfile")?.get(profile) ?: return null
        val height = Reflect.field(dock.javaClass, "cellHeightPx")?.getInt(dock) ?: return null
        val offset = Reflect.field(folders.javaClass, "folderIconOffsetYPx")?.getInt(folders) ?: 0
        val cell = cellOf(hotseat) ?: return null
        val icon = iconPx(hotseat)
        val slack = (height - icon) / 2f
        val natural = slack + icon / 2f + offset
        val target = slack + icon * scale / 2f
        return cell / 2f to (target - scale * natural) / (1f - scale)
    }

    /** The Home screen's icon size, `WorkspaceProfile.iconSizePx`. */
    private fun iconPx(hotseat: ViewGroup): Float {
        val profile = profileOf(hotseat) ?: return 0f
        val workspace = Reflect.field(profile.javaClass, "workspaceProfile")?.get(profile) ?: return 0f
        return (Reflect.field(workspace.javaClass, "iconSizePx")?.getInt(workspace) ?: 0).toFloat()
    }

    private fun dockProfile(hotseat: ViewGroup): Any? {
        val profile = profileOf(hotseat) ?: return null
        return Reflect.field(profile.javaClass, "hotseatProfile")?.get(profile)
    }

    private fun profileOf(hotseat: ViewGroup): Any? {
        val activity = Reflect.field(hotseat.javaClass, "mActivity")?.get(hotseat) ?: return null
        return Reflect.method(activity.javaClass, "getDeviceProfile")?.invoke(activity)
    }

    private const val CELL_LAYOUT = "com.android.launcher3.CellLayout"
    private const val BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
    private const val FOLDER_ICON = "com.android.launcher3.folder.FolderIcon"
    private const val PREDICTED_APP_ICON = "com.android.launcher3.views.PredictedAppIcon"
}
