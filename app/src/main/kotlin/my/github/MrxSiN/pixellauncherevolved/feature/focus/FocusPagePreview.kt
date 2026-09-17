package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.Activity
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.os.OutcomeReceiver
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.TextView

import java.lang.reflect.Method

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages

internal interface FocusPagePreviewSource {
    fun pages(screenIds: List<Int>): Map<Int, FocusPagePreview>
}

internal data class FocusPagePreview(
    val columns: Int,
    val rows: Int,
    val items: List<FocusPreviewItem>,
    val hotseatIcons: List<Drawable>,
    val wallpaper: Drawable?,
    val snapshot: Bitmap?,
    val aspectRatio: Float,
    /** Where the launcher lays a page out, once a real page has been seen. */
    val geometry: PageGeometry? = null,
    /** A picture of any page, to borrow the dock and search bar from: they are the same on every page. */
    val dock: Bitmap? = null,
)

/**
 * How the launcher lays out a page, measured off a real one.
 *
 * Every value is a fraction of the launcher window, width for sizes and the
 * matching side for rectangles, so it scales onto a miniature of any size. A
 * page drawn from the model with these lands its icons where the launcher puts
 * them, at the launcher's size, with labels the launcher's size, rather than on
 * a guessed grid with guessed proportions.
 */
internal data class PageGeometry(
    val grid: RectF,
    val iconSize: Float,
    val labelSize: Float,
    val labelGap: Float,
    val dock: RectF,
)

internal data class FocusPreviewItem(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val icon: Drawable?,
    val folderIcons: List<Drawable> = emptyList(),
    val isWidget: Boolean = false,
    val widgetPreview: Drawable? = null,
    val label: CharSequence? = null,
)

private data class PreviewRecord(
    val id: Int,
    val container: Int,
    val screen: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val itemType: Int,
    val icon: Drawable?,
    val widget: AppWidgetProviderInfo?,
    val title: CharSequence?,
)

private object FocusPreviewModel {
    @Volatile var records: List<PreviewRecord> = emptyList()
    @Volatile var wallpaper: Drawable? = null
    @Volatile var snapshots: Map<Int, Bitmap> = emptyMap()
    @Volatile var aspectRatio: Float = DEFAULT_ASPECT_RATIO

    /** The launcher window's size in pixels, which a widget is sized against. Zero until seen. */
    @Volatile var windowWidth: Int = 0

    @Volatile var windowHeight: Int = 0

    /** The workspace's own grid, or zero until a page has been seen. */
    @Volatile var columns: Int = 0

    @Volatile var rows: Int = 0

    /** Where the pictures are kept on disk, once the launcher has started recording. */
    @Volatile var store: FocusPageSnapshotStore? = null

    /** Null until a page with an icon on it has been laid out and measured. */
    @Volatile var geometry: PageGeometry? = null

    private const val DEFAULT_ASPECT_RATIO = 9f / 20f
}

/** The pictures of the home screen's pages, which follow a page when it is given a new id. */
internal object FocusPagePictures {

    fun renumber(mapping: Map<Int, Int>) {
        if (mapping.isEmpty()) return
        FocusPreviewModel.snapshots = FocusPreviewModel.snapshots.mapKeys { (screen, _) -> mapping[screen] ?: screen }
        FocusPreviewModel.store?.renumber(mapping)
    }
}

/** Copies preview-safe fields and launcher-themed icons before Focus filtering. */
internal class FocusPreviewRecorder(
    itemInfo: Class<*>,
    private val context: Context,
) {
    private val id = Reflect.field(itemInfo, ID)
    private val container = Reflect.field(itemInfo, CONTAINER)
    private val screen = Reflect.field(itemInfo, SCREEN)
    private val cellX = Reflect.field(itemInfo, CELL_X)
    private val cellY = Reflect.field(itemInfo, CELL_Y)
    private val spanX = Reflect.field(itemInfo, SPAN_X)
    private val spanY = Reflect.field(itemInfo, SPAN_Y)
    private val itemType = Reflect.field(itemInfo, ITEM_TYPE)
    private val title = Reflect.field(itemInfo, TITLE)
    private val targetComponent = Reflect.method(itemInfo, TARGET_COMPONENT)
    private val iconMethods = mutableMapOf<Class<*>, Method?>()
    private val widgetIds = mutableMapOf<Class<*>, java.lang.reflect.Field?>()


    /** Pages a Mode has taken off the workspace cannot be captured again. */
    private val snapshots = FocusPageSnapshotStore(context).also { store ->
        FocusPreviewModel.store = store
        // A page captured since the launcher started is the newer of the two.
        store.restore { stored, wallpaper ->
            FocusPreviewModel.snapshots = stored + FocusPreviewModel.snapshots
            if (FocusPreviewModel.wallpaper == null && wallpaper != null) {
                FocusPreviewModel.wallpaper = BitmapDrawable(context.resources, wallpaper)
            }
        }
    }

    val isUsable: Boolean = listOf(id, container, screen, cellX, cellY, spanX, spanY, itemType)
        .none { it == null }

    fun remember(items: SparseArray<*>) {
        if (!isUsable) return

        FocusPreviewModel.records = buildList {
            for (index in 0 until items.size()) {
                val item = items.valueAt(index) ?: continue
                record(item)?.let(::add)
            }
        }
        loadWallpaper()?.let { FocusPreviewModel.wallpaper = it }
    }

    /** Captures the already-laid-out launcher views, including widgets and themed icons. */
    fun capture(activity: Activity) {
        FocusPageSnapshotter.capture(activity, snapshots)
    }

    private fun record(item: Any): PreviewRecord? = runCatching {
        val component = targetComponent?.invoke(item) as? ComponentName
        PreviewRecord(
            id = id!!.getInt(item),
            container = container!!.getInt(item),
            screen = screen!!.getInt(item),
            cellX = cellX!!.getInt(item),
            cellY = cellY!!.getInt(item),
            spanX = spanX!!.getInt(item),
            spanY = spanY!!.getInt(item),
            itemType = itemType!!.getInt(item),
            icon = icon(item, component),
            widget = widgetInfo(item),
            title = runCatching { title?.get(item) as? CharSequence }.getOrNull(),
        )
    }.getOrNull()

    /**
     * The widget's provider, for a page that cannot be photographed.
     *
     * A page a Mode has taken off the workspace has no widget view to draw. Its
     * preview is drawn when a page preview is asked for rather than now, because
     * only then are its size and the theme in force known.
     */
    private fun widgetInfo(item: Any): AppWidgetProviderInfo? {
        if (itemType?.getInt(item) != ITEM_TYPE_WIDGET) return null
        val field = widgetIds.getOrPut(item.javaClass) { Reflect.field(item.javaClass, APP_WIDGET_ID) }
        val widgetId = runCatching { field?.getInt(item) }.getOrNull() ?: return null
        return runCatching { AppWidgetManager.getInstance(context)?.getAppWidgetInfo(widgetId) }.getOrNull()
    }

    private fun icon(item: Any, component: ComponentName?): Drawable? {
        val method = iconMethods.getOrPut(item.javaClass) {
            Reflect.method(
                item.javaClass,
                NEW_ICON,
                Context::class.java,
                Int::class.javaPrimitiveType!!,
            )
        }
        // Always ask for the themed icon. The launcher hands one back only when
        // it has one, which is exactly when themed icons are turned on, so this
        // needs no setting to read — and the setting this used to read,
        // Themes.isThemedIconEnabled, is not in the launcher any more, so every
        // icon came back in full colour while the home screen showed monochrome.
        return runCatching { method?.invoke(item, context, FLAG_THEMED) as? Drawable }.getOrNull()
            ?: component?.let { activity ->
                runCatching { context.packageManager.getActivityIcon(activity) }.getOrNull()
            }
    }

    @SuppressLint("MissingPermission")
    private fun loadWallpaper(): Drawable? {
        val manager = WallpaperManager.getInstance(context)
        if (runCatching { manager.wallpaperInfo != null }.getOrDefault(false)) return null
        return runCatching { manager.drawable }.getOrNull()
    }

    private companion object {
        const val ID = "id"
        const val CONTAINER = "container"
        const val SCREEN = "screenId"
        const val CELL_X = "cellX"
        const val CELL_Y = "cellY"
        const val SPAN_X = "spanX"
        const val SPAN_Y = "spanY"
        const val ITEM_TYPE = "itemType"
        const val TITLE = "title"
        const val TARGET_COMPONENT = "getTargetComponent"
        const val NEW_ICON = "newIcon"
        const val APP_WIDGET_ID = "appWidgetId"
        const val ITEM_TYPE_WIDGET = 4
        const val FLAG_THEMED = 1
    }
}

/** Captures the active live-wallpaper frame from the display compositor. */
private object ActiveWallpaperCapture {

    fun capture(activity: Activity, onCaptured: (Bitmap?) -> Unit) {
        val root = activity.window.decorView
        if (!root.isAttachedToWindow) {
            onCaptured(null)
            return
        }

        val content = root.findViewById<View>(android.R.id.content) ?: root
        val originalAlpha = content.alpha
        content.alpha = 0f
        root.postOnAnimation {
            // The frame that draws the launcher transparent still has to reach
            // the compositor before the display is sampled, so the capture is
            // asked for a few frames later rather than on the very next one.
            root.postDelayed({
                // The display is sampled after the request returns, so the
                // launcher has to stay hidden until the result arrives.
                // Restoring beside the request put the home screen itself in
                // the captured wallpaper, and every page preview then showed
                // that one screenshot underneath its own contents.
                val restore = once { content.alpha = originalAlpha }
                root.postDelayed(restore, RESTORE_TIMEOUT_MS)
                request(activity) { bitmap ->
                    restore.run()
                    onCaptured(cropSystemBars(root, bitmap))
                }
            }, HIDE_SETTLE_MS)
        }
    }

    /**
     * A restore that runs once, whichever of the result and the timeout is first.
     *
     * The launcher is invisible until this runs, so a capture that never calls
     * back has to be survivable rather than trusted.
     */
    private fun once(action: () -> Unit): Runnable {
        var done = false
        return Runnable {
            if (done) return@Runnable
            done = true
            action()
        }
    }

    private fun cropSystemBars(root: View, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val insets = root.rootWindowInsets?.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
        ) ?: return bitmap
        val contentHeight = bitmap.height - insets.top - insets.bottom
        if (contentHeight <= 0) return bitmap
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, insets.top, bitmap.width, contentHeight)
        }.getOrDefault(bitmap)
    }

    private fun request(activity: Activity, onCaptured: (Bitmap?) -> Unit) {
        runCatching {
            val screenCapture = Class.forName(SCREEN_CAPTURE)
            val paramsClass = Class.forName(SCREEN_CAPTURE_PARAMS)
            val builderClass = Class.forName(SCREEN_CAPTURE_BUILDER)
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
                .newInstance(activity.display?.displayId ?: 0)
            val params = Reflect.method(builderClass, BUILD)?.invoke(builder) ?: error("No capture params")
            val capture = Reflect.method(
                screenCapture,
                CAPTURE,
                paramsClass,
                java.util.concurrent.Executor::class.java,
                OutcomeReceiver::class.java,
            ) ?: error("No screen capture method")

            val receiver = object : OutcomeReceiver<Any, Throwable> {
                override fun onResult(result: Any) {
                    onCaptured(bitmap(result))
                }

                override fun onError(error: Throwable) {
                    onCaptured(null)
                }
            }
            capture.invoke(null, params, activity.mainExecutor, receiver)
        }.onFailure { onCaptured(null) }
    }

    private fun bitmap(result: Any): Bitmap? = runCatching {
        val buffer = Reflect.method(result.javaClass, GET_HARDWARE_BUFFER)
            ?.invoke(result) as? HardwareBuffer ?: return null
        val colorSpace = Reflect.method(result.javaClass, GET_COLOR_SPACE)
            ?.invoke(result) as? ColorSpace
        Bitmap.wrapHardwareBuffer(buffer, colorSpace)
    }.getOrNull()

    private const val SCREEN_CAPTURE = "android.window.ScreenCapture"
    private const val SCREEN_CAPTURE_PARAMS = "android.window.ScreenCapture\$ScreenCaptureParams"
    private const val SCREEN_CAPTURE_BUILDER =
        "android.window.ScreenCapture\$ScreenCaptureParams\$Builder"
    private const val BUILD = "build"
    private const val CAPTURE = "capture"
    private const val GET_HARDWARE_BUFFER = "getHardwareBuffer"
    private const val GET_COLOR_SPACE = "getColorSpace"
    private const val HIDE_SETTLE_MS = 150L
    private const val RESTORE_TIMEOUT_MS = 1_000L
}

/** Captures miniature pages from launcher views while the launcher is visible. */
private object FocusPageSnapshotter {
    private var wallpaperCaptured = false
    private var awaitingWindowFocus = false
    private var store: FocusPageSnapshotStore? = null

    /**
     * When the launcher last became the window in front, or zero.
     *
     * The wallpaper is read off the display, so whatever else the display is
     * showing is read with it. The notification shade takes the window focus
     * and is already waited out, but a heads-up notification does not take it,
     * and one arriving as the launcher came forward was captured and then used
     * as the wallpaper for the rest of the launcher's life. Notifications like
     * that show for a few seconds, so the wallpaper is read only once the
     * launcher has held focus for longer than one lasts.
     */
    private var focusedSince = 0L

    /** The pages the workspace held last, so a change of set can be noticed. */
    private var held: Set<Int> = emptySet()

    fun capture(activity: Activity, store: FocusPageSnapshotStore) {
        this.store = store
        capture(activity, RETRY_LIMIT)
    }

    /**
     * Snapshots the pages now, and the wallpaper behind them once.
     *
     * The pages are drawn from launcher views, so nothing in front of the
     * launcher can reach them and they are taken on every pass. The wallpaper is
     * read off the display, which is a different matter: see [isSettled].
     */
    private fun capture(activity: Activity, retriesLeft: Int) {
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return

        runCatching { capturePages(activity) }
        if (FocusPreviewModel.wallpaper != null || wallpaperCaptured) return

        if (!isSettled(activity)) {
            if (activity.hasWindowFocus()) {
                // A page change lasts a few hundred milliseconds, so waiting it
                // out captures on this visit rather than on the next one.
                if (retriesLeft > 0) {
                    activity.window.decorView.postDelayed(
                        { capture(activity, retriesLeft - 1) },
                        RETRY_DELAY_MS,
                    )
                }
            } else {
                awaitWindowFocus(activity)
            }
            return
        }

        // Waiting this out costs nothing on screen — the launcher is only
        // hidden for the capture itself — so it waits rather than spends a
        // retry, which would run out long before a notification does.
        val settle = remainingSettle()
        if (settle > 0) {
            activity.window.decorView.postDelayed({ capture(activity, retriesLeft) }, settle)
            return
        }

        wallpaperCaptured = true
        ActiveWallpaperCapture.capture(activity) { bitmap ->
            // The shade can be pulled down while the display is being sampled,
            // and the wallpaper is captured once, so a frame taken through
            // anything else is dropped and the capture is left to be retried.
            if (bitmap != null && isSettled(activity)) {
                FocusPreviewModel.wallpaper = BitmapDrawable(activity.resources, bitmap)
                store?.saveWallpaper(bitmap)
            } else {
                wallpaperCaptured = false
            }
            runCatching { capturePages(activity) }
        }
    }

    /**
     * Captures the wallpaper when whatever is in front of the launcher goes away.
     *
     * The launcher keeps running while the shade covers it, so no later resume
     * is guaranteed; without this the wallpaper would stay uncaptured until the
     * user left the launcher and came back.
     */
    private fun awaitWindowFocus(activity: Activity) {
        if (awaitingWindowFocus) return
        val root = activity.window.decorView
        val observer = root.viewTreeObserver.takeIf { it.isAlive } ?: return
        awaitingWindowFocus = true
        observer.addOnWindowFocusChangeListener(
            object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) return
                    root.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                    awaitingWindowFocus = false
                    root.postDelayed({ capture(activity, RETRY_LIMIT) }, RETRY_DELAY_MS)
                }
            },
        )
    }

    /**
     * Whether the launcher is the window in front and its pages have stopped.
     *
     * Whatever covers the launcher is captured with the wallpaper: the
     * notification shade takes the window focus, and a live wallpaper follows
     * the pages, so a frame taken part way through a page change belongs to no
     * page. Both moments are skipped rather than captured.
     */
    private fun isSettled(activity: Activity): Boolean {
        if (!activity.hasWindowFocus()) {
            focusedSince = 0L
            return false
        }
        if (focusedSince == 0L) focusedSince = SystemClock.uptimeMillis()
        val workspace = workspace(activity) ?: return false
        val moving = runCatching {
            Reflect.method(workspace.javaClass, PAGE_IN_TRANSITION)?.invoke(workspace) as? Boolean
        }.getOrNull()
        val dragged = runCatching {
            Reflect.field(workspace.javaClass, BEING_DRAGGED)?.getBoolean(workspace)
        }.getOrNull()
        return moving != true && dragged != true
    }

    /** How much longer the launcher must hold focus before the display is read. */
    private fun remainingSettle(): Long {
        if (focusedSince == 0L) return WALLPAPER_SETTLE_MS
        val held = SystemClock.uptimeMillis() - focusedSince
        return (WALLPAPER_SETTLE_MS - held).coerceAtLeast(0L)
    }

    /** Whether a page has anything on it yet, so a picture of it is worth taking. */
    private fun hasItems(page: View): Boolean = runCatching {
        val container = Reflect.field(page.javaClass, SHORTCUTS)?.get(page) as? ViewGroup
        (container?.childCount ?: 0) > 0
    }.getOrDefault(true)

    /**
     * The grid a page is laid out on, read from the page itself.
     *
     * A page with no snapshot is drawn cell by cell, so it needs the same grid
     * the launcher uses or its icons and widgets come out the wrong size. The
     * count is held in fields rather than behind getters on this launcher.
     */
    private fun rememberGrid(page: View?) {
        if (page == null) return
        runCatching {
            val x = Reflect.field(page.javaClass, COUNT_X)?.getInt(page) ?: return
            val y = Reflect.field(page.javaClass, COUNT_Y)?.getInt(page) ?: return
            if (x > 0 && y > 0) {
                FocusPreviewModel.columns = x
                FocusPreviewModel.rows = y
            }
        }
    }

    /**
     * Measures where the launcher lays a page out, off a page it has laid out.
     *
     * Taken in the same frame the snapshots are, and relative to the window the
     * way the snapshots are drawn, so a page drawn from these lines up with a
     * page photographed. The icon and label sizes are read off an icon itself,
     * which carries the size the launcher settled on for this grid and display.
     */
    private fun rememberGeometry(root: View, workspace: ViewGroup, hotseat: View?) {
        if (hotseat == null) return
        val width = root.width.toFloat()
        val height = root.height.toFloat()
        val rootLocation = IntArray(2).also(root::getLocationOnScreen)
        val workspaceLocation = IntArray(2).also(workspace::getLocationOnScreen)
        val hotseatLocation = IntArray(2).also(hotseat::getLocationOnScreen)

        for (index in 0 until workspace.childCount) {
            val page = workspace.getChildAt(index) ?: continue
            val items = Reflect.field(page.javaClass, SHORTCUTS)?.get(page) as? ViewGroup ?: continue
            val icon = (0 until items.childCount)
                .map(items::getChildAt)
                .filterIsInstance<TextView>()
                .firstOrNull { it.compoundDrawables[1] != null }
                ?: continue

            val left = workspaceLocation[0] - rootLocation[0] + items.left
            val top = workspaceLocation[1] - rootLocation[1] + items.top
            val dockLeft = hotseatLocation[0] - rootLocation[0]
            val dockTop = hotseatLocation[1] - rootLocation[1]
            FocusPreviewModel.geometry = PageGeometry(
                grid = RectF(
                    left / width,
                    top / height,
                    (left + items.width) / width,
                    (top + items.height) / height,
                ),
                iconSize = icon.compoundDrawables[1].bounds.width() / width,
                labelSize = icon.textSize / width,
                labelGap = icon.compoundDrawablePadding / width,
                dock = RectF(
                    dockLeft / width,
                    dockTop / height,
                    (dockLeft + hotseat.width) / width,
                    (dockTop + hotseat.height) / height,
                ),
            )
            return
        }
    }

    private fun workspace(activity: Activity): ViewGroup? =
        Reflect.field(activity.javaClass, WORKSPACE)?.get(activity) as? ViewGroup

    private fun capturePages(activity: Activity) {
        val root = activity.window.decorView
        val workspace = workspace(activity) ?: return
        if (root.width <= 0 || root.height <= 0 || workspace.childCount == 0) return

        val hotseat = Reflect.field(activity.javaClass, HOTSEAT)?.get(activity) as? View
        rememberGrid(workspace.getChildAt(0))
        runCatching { rememberGeometry(root, workspace, hotseat) }
        val screenAt = Reflect.method(
            workspace.javaClass,
            SCREEN_FOR_PAGE,
            Int::class.javaPrimitiveType!!,
        )
        val rootLocation = IntArray(2).also(root::getLocationOnScreen)
        val workspaceLocation = IntArray(2).also(workspace::getLocationOnScreen)
        val targetWidth = SNAPSHOT_WIDTH_PX
        val scale = targetWidth.toFloat() / root.width
        val targetHeight = (root.height * scale).roundToInt().coerceAtLeast(1)
        val wallpaper = FocusPreviewModel.wallpaper

        val captured = buildMap {
            for (index in 0 until workspace.childCount) {
                val page = workspace.getChildAt(index) ?: continue
                val screenId = runCatching { screenAt?.invoke(workspace, index) as? Int }.getOrNull()
                    ?: FocusPages.order.getOrNull(index)
                    ?: continue
                if (screenId < 0) continue

                // A page the launcher has not filled in yet is not a picture of
                // that page. Capturing it would replace a good snapshot, and
                // the wallpaper being in hand no longer says the workspace is
                // ready, because the wallpaper is now restored from disk before
                // the launcher has laid anything out.
                if (!hasItems(page)) continue

                val bitmap = HardwarePicture.record(targetWidth, targetHeight) { canvas ->
                    drawWallpaper(canvas, wallpaper, targetWidth, targetHeight)
                    drawAtScreenPosition(canvas, page, rootLocation, scale, workspaceLocation)
                    if (hotseat != null) drawAtScreenPosition(canvas, hotseat, rootLocation, scale)
                } ?: continue
                put(screenId, bitmap)
            }
        }
        if (captured.isNotEmpty()) {
            // Before the wallpaper is in hand a page is drawn on a flat fill.
            // That stands in for a page which has no snapshot at all, but it
            // must never replace one taken, or restored from disk, with the
            // wallpaper behind it — which is what emptied the previews of their
            // wallpaper on every launcher start.
            val fresh = if (wallpaper != null) {
                captured
            } else {
                captured.filterKeys { it !in FocusPreviewModel.snapshots }
            }
            val snapshots = if (fresh.isEmpty()) FocusPreviewModel.snapshots else kept(fresh)
            FocusPreviewModel.snapshots = snapshots

            // The set changes when an assignment is made or a Mode turns on or
            // off, which is exactly when a page stops being capturable. Writing
            // then, rather than on every visit home, keeps a page that can no
            // longer be photographed without re-encoding the ones that can.
            // Not before the wallpaper is in hand: the first capture of a
            // launcher start draws pages on a flat fill, and writing that down
            // would replace a good page with a blank one.
            if (captured.keys != held && wallpaper != null) {
                held = captured.keys
                store?.save(snapshots, FocusPages.order.toSet())
            }
        }
        FocusPreviewModel.aspectRatio = root.width.toFloat() / root.height
        FocusPreviewModel.windowWidth = root.width
        FocusPreviewModel.windowHeight = root.height
    }

    /**
     * Keeps the last snapshot of a page the workspace is not holding.
     *
     * A page given to a Mode is filtered out of the binding, so it has no view
     * to draw and no snapshot can be taken of it. Replacing the whole set on
     * every capture therefore erased exactly the pages the Focus pages dialog
     * is there to show, and each one fell back to being drawn from the model:
     * widgets as empty boxes, icons without their labels. It works in both
     * directions, because while a Mode is on it is the ordinary pages that are
     * filtered out.
     *
     * A page the launcher no longer has at all is dropped, so a deleted one
     * does not hold a bitmap for the life of the process. The catalogue is
     * empty only before the first binding, and nothing is dropped then.
     */
    private fun kept(captured: Map<Int, Bitmap>): Map<Int, Bitmap> {
        val known = FocusPages.order.toHashSet()
        val carried = FocusPreviewModel.snapshots.filterKeys {
            it !in captured && (known.isEmpty() || it in known)
        }
        return carried + captured
    }

    private fun drawAtScreenPosition(
        canvas: Canvas,
        view: View,
        rootLocation: IntArray,
        scale: Float,
        position: IntArray? = null,
    ) {
        val location = position ?: IntArray(2).also(view::getLocationOnScreen)
        val checkpoint = canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(
            (location[0] - rootLocation[0]).toFloat(),
            (location[1] - rootLocation[1]).toFloat(),
        )
        view.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    private fun drawWallpaper(
        canvas: Canvas,
        wallpaper: Drawable?,
        width: Int,
        height: Int,
    ) {
        if (wallpaper == null || wallpaper.intrinsicWidth <= 0 || wallpaper.intrinsicHeight <= 0) {
            canvas.drawColor(0xff17151d.toInt())
            return
        }
        val scale = maxOf(
            width.toFloat() / wallpaper.intrinsicWidth,
            height.toFloat() / wallpaper.intrinsicHeight,
        )
        val drawWidth = wallpaper.intrinsicWidth * scale
        val drawHeight = wallpaper.intrinsicHeight * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        wallpaper.setBounds(
            left.roundToInt(),
            top.roundToInt(),
            (left + drawWidth).roundToInt(),
            (top + drawHeight).roundToInt(),
        )
        wallpaper.draw(canvas)
    }

    private const val WORKSPACE = "mWorkspace"
    private const val SHORTCUTS = "mShortcutsAndWidgets"
    private const val COUNT_X = "mCountX"
    private const val COUNT_Y = "mCountY"
    private const val HOTSEAT = "mHotseat"
    private const val SCREEN_FOR_PAGE = "getScreenIdForPageIndex"
    private const val PAGE_IN_TRANSITION = "isPageInTransition"
    private const val BEING_DRAGGED = "mIsBeingDragged"
    private const val RETRY_LIMIT = 3
    private const val RETRY_DELAY_MS = 400L

    /** Longer than a heads-up notification stays on screen. */
    private const val WALLPAPER_SETTLE_MS = 6_000L
    private const val SNAPSHOT_WIDTH_PX = 320
}

/**
 * Builds page previews from cached launcher model and view snapshots.
 *
 * @param context the screen the previews are shown on, whose configuration a
 * widget preview is drawn in.
 */
internal class LauncherPagePreviewSource(context: Context) : FocusPagePreviewSource {

    private val widgets = WidgetPreviewRenderer(context)

    override fun pages(screenIds: List<Int>): Map<Int, FocusPagePreview> {
        val records = FocusPreviewModel.records
        val children = records.groupBy(PreviewRecord::container)
        val desktop = records.filter { it.container == CONTAINER_DESKTOP }
        val hotseat = records
            .filter { it.container == CONTAINER_HOTSEAT }
            .sortedBy(PreviewRecord::screen)
            .mapNotNull(PreviewRecord::icon)
        // The launcher's own grid when a page has been seen, and the largest
        // cell any item reaches otherwise, so nothing is ever drawn outside it.
        val columns = maxOf(
            FocusPreviewModel.columns.takeIf { it > 0 } ?: DEFAULT_COLUMNS,
            desktop.maxOfOrNull { it.cellX + it.spanX } ?: 0,
        )
        val rows = maxOf(
            FocusPreviewModel.rows.takeIf { it > 0 } ?: DEFAULT_ROWS,
            desktop.maxOfOrNull { it.cellY + it.spanY } ?: 0,
        )
        val snapshots = FocusPreviewModel.snapshots
        val wallpaper = FocusPreviewModel.wallpaper
        val aspectRatio = FocusPreviewModel.aspectRatio
        val geometry = FocusPreviewModel.geometry
        // A cell's size on the home screen, which a widget preview is drawn at.
        val cellWidth = (geometry?.grid?.width() ?: 1f) * FocusPreviewModel.windowWidth / columns
        val cellHeight = (geometry?.grid?.height() ?: 1f) * FocusPreviewModel.windowHeight / rows
        // The dock and its search bar are the same on every page, so any page
        // photographed lends them to a page that could not be.
        val dock = screenIds.firstNotNullOfOrNull(snapshots::get) ?: snapshots.values.firstOrNull()

        return screenIds.associateWith { screenId ->
            FocusPagePreview(
                columns = columns,
                rows = rows,
                items = desktop.filter { it.screen == screenId }.map { record ->
                    FocusPreviewItem(
                        cellX = record.cellX,
                        cellY = record.cellY,
                        spanX = record.spanX.coerceAtLeast(1),
                        spanY = record.spanY.coerceAtLeast(1),
                        icon = record.icon,
                        folderIcons = if (record.itemType == ITEM_TYPE_FOLDER) {
                            children[record.id].orEmpty().mapNotNull(PreviewRecord::icon).take(FOLDER_ICON_LIMIT)
                        } else {
                            emptyList()
                        },
                        isWidget = record.itemType == ITEM_TYPE_WIDGET,
                        widgetPreview = record.widget?.let { info ->
                            widgets.render(
                                info,
                                (cellWidth * record.spanX).roundToInt(),
                                (cellHeight * record.spanY).roundToInt(),
                            )
                        },
                        label = record.title,
                    )
                },
                hotseatIcons = hotseat,
                wallpaper = wallpaper,
                snapshot = snapshots[screenId],
                aspectRatio = aspectRatio,
                geometry = geometry,
                dock = dock,
            )
        }
    }

    private companion object {
        const val CONTAINER_DESKTOP = -100
        const val CONTAINER_HOTSEAT = -101
        const val ITEM_TYPE_FOLDER = 2
        const val ITEM_TYPE_WIDGET = 4
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val FOLDER_ICON_LIMIT = 4
    }
}
