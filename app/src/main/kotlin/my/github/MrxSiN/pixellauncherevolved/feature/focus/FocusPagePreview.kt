package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.os.OutcomeReceiver
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets

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
)

internal data class FocusPreviewItem(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val icon: Drawable?,
    val folderIcons: List<Drawable> = emptyList(),
    val isWidget: Boolean = false,
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
)

private object FocusPreviewModel {
    @Volatile var records: List<PreviewRecord> = emptyList()
    @Volatile var wallpaper: Drawable? = null
    @Volatile var snapshots: Map<Int, Bitmap> = emptyMap()
    @Volatile var aspectRatio: Float = DEFAULT_ASPECT_RATIO

    private const val DEFAULT_ASPECT_RATIO = 9f / 20f
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
    private val targetComponent = Reflect.method(itemInfo, TARGET_COMPONENT)
    private val iconMethods = mutableMapOf<Class<*>, Method?>()
    private val iconFlags = if (themedIcons(itemInfo.classLoader ?: context.classLoader)) FLAG_THEMED else 0

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
        FocusPageSnapshotter.capture(activity)
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
        )
    }.getOrNull()

    private fun icon(item: Any, component: ComponentName?): Drawable? {
        val method = iconMethods.getOrPut(item.javaClass) {
            Reflect.method(
                item.javaClass,
                NEW_ICON,
                Context::class.java,
                Int::class.javaPrimitiveType!!,
            )
        }
        return runCatching { method?.invoke(item, context, iconFlags) as? Drawable }.getOrNull()
            ?: component?.let { activity ->
                runCatching { context.packageManager.getActivityIcon(activity) }.getOrNull()
            }
    }

    private fun themedIcons(classLoader: ClassLoader): Boolean = runCatching {
        val themes = Class.forName(THEMES, false, classLoader)
        Reflect.method(themes, IS_THEMED, Context::class.java)?.invoke(null, context) == true
    }.getOrDefault(false)

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
        const val TARGET_COMPONENT = "getTargetComponent"
        const val NEW_ICON = "newIcon"
        const val THEMES = "com.android.launcher3.util.Themes"
        const val IS_THEMED = "isThemedIconEnabled"
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

    fun capture(activity: Activity) = capture(activity, RETRY_LIMIT)

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

        wallpaperCaptured = true
        ActiveWallpaperCapture.capture(activity) { bitmap ->
            // The shade can be pulled down while the display is being sampled,
            // and the wallpaper is captured once, so a frame taken through
            // anything else is dropped and the capture is left to be retried.
            if (bitmap != null && isSettled(activity)) {
                FocusPreviewModel.wallpaper = BitmapDrawable(activity.resources, bitmap)
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
                    root.postDelayed({ capture(activity) }, RETRY_DELAY_MS)
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
        if (!activity.hasWindowFocus()) return false
        val workspace = workspace(activity) ?: return false
        val moving = runCatching {
            Reflect.method(workspace.javaClass, PAGE_IN_TRANSITION)?.invoke(workspace) as? Boolean
        }.getOrNull()
        val dragged = runCatching {
            Reflect.field(workspace.javaClass, BEING_DRAGGED)?.getBoolean(workspace)
        }.getOrNull()
        return moving != true && dragged != true
    }

    private fun workspace(activity: Activity): ViewGroup? =
        Reflect.field(activity.javaClass, WORKSPACE)?.get(activity) as? ViewGroup

    private fun capturePages(activity: Activity) {
        val root = activity.window.decorView
        val workspace = workspace(activity) ?: return
        if (root.width <= 0 || root.height <= 0 || workspace.childCount == 0) return

        val hotseat = Reflect.field(activity.javaClass, HOTSEAT)?.get(activity) as? View
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

                val renderNode = RenderNode("Focus page $screenId").apply {
                    setPosition(0, 0, targetWidth, targetHeight)
                }
                val canvas = renderNode.beginRecording(targetWidth, targetHeight)
                drawWallpaper(canvas, wallpaper, targetWidth, targetHeight)
                drawAtScreenPosition(canvas, page, rootLocation, scale, workspaceLocation)
                if (hotseat != null) drawAtScreenPosition(canvas, hotseat, rootLocation, scale)
                renderNode.endRecording()
                val bitmap = createHardwareBitmap(renderNode, targetWidth, targetHeight) ?: continue
                put(screenId, bitmap)
            }
        }
        if (captured.isNotEmpty()) FocusPreviewModel.snapshots = captured
        FocusPreviewModel.aspectRatio = root.width.toFloat() / root.height
    }

    private fun createHardwareBitmap(node: RenderNode, width: Int, height: Int): Bitmap? =
        runCatching {
            Reflect.method(
                HardwareRenderer::class.java,
                CREATE_HARDWARE_BITMAP,
                RenderNode::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(null, node, width, height) as? Bitmap
        }.getOrNull()

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
    private const val HOTSEAT = "mHotseat"
    private const val SCREEN_FOR_PAGE = "getScreenIdForPageIndex"
    private const val PAGE_IN_TRANSITION = "isPageInTransition"
    private const val BEING_DRAGGED = "mIsBeingDragged"
    private const val RETRY_LIMIT = 3
    private const val RETRY_DELAY_MS = 400L
    private const val CREATE_HARDWARE_BITMAP = "createHardwareBitmap"
    private const val SNAPSHOT_WIDTH_PX = 320
}

/** Builds page previews from cached launcher model and view snapshots. */
internal class LauncherPagePreviewSource : FocusPagePreviewSource {

    override fun pages(screenIds: List<Int>): Map<Int, FocusPagePreview> {
        val records = FocusPreviewModel.records
        val children = records.groupBy(PreviewRecord::container)
        val desktop = records.filter { it.container == CONTAINER_DESKTOP }
        val hotseat = records
            .filter { it.container == CONTAINER_HOTSEAT }
            .sortedBy(PreviewRecord::screen)
            .mapNotNull(PreviewRecord::icon)
        val columns = maxOf(DEFAULT_COLUMNS, desktop.maxOfOrNull { it.cellX + it.spanX } ?: 0)
        val rows = maxOf(DEFAULT_ROWS, desktop.maxOfOrNull { it.cellY + it.spanY } ?: 0)
        val snapshots = FocusPreviewModel.snapshots
        val wallpaper = FocusPreviewModel.wallpaper
        val aspectRatio = FocusPreviewModel.aspectRatio

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
                    )
                },
                hotseatIcons = hotseat,
                wallpaper = wallpaper,
                snapshot = snapshots[screenId],
                aspectRatio = aspectRatio,
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
