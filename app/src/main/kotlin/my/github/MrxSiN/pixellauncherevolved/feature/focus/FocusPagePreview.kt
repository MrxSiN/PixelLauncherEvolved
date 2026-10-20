package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LayoutDirection
import android.util.SparseArray
import android.view.ViewGroup

import java.lang.reflect.Method

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPlan
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource
import my.github.MrxSiN.pixellauncherevolved.wallpaper.HomeBlurDepth
import my.github.MrxSiN.pixellauncherevolved.wallpaper.HomeBlurRadius

internal data class FocusPagePreview(
    val screen: Int,
    val columns: Int,
    val rows: Int,
    val items: List<FocusPreviewItem>,
    val hotseatIcons: List<Drawable>,
    /** The home wallpaper, cropped to the page and blurred as home blurs it. */
    val wallpaper: Drawable?,
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
    val widgetPreview: Drawable? = null,
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
)

private object FocusPreviewModel {
    /** The model's items, kept for the page drawn by hand when the launcher's preview cannot draw one. */
    @Volatile var records: List<PreviewRecord> = emptyList()

    /** The home wallpaper, sharp, cropped as the display crops it across every page. Main thread. */
    var wallpaper: Bitmap? = null

    /** Which wallpaper [wallpaper] is. Main thread. */
    var wallpaperKey: Int = NO_KEY

    @Volatile var aspectRatio: Float = DEFAULT_ASPECT_RATIO

    /** The launcher window's size in pixels, which a page is drawn against. Zero until seen. */
    @Volatile var windowWidth: Int = 0

    @Volatile var windowHeight: Int = 0

    /** The workspace's own grid, or zero until a page has been seen. */
    @Volatile var columns: Int = 0

    @Volatile var rows: Int = 0

    /** Blurred backdrops by where on the wallpaper a page sits, and what they were made from. Main thread. */
    val backdrops = HashMap<Float, Drawable>()
    var backdropFrom: Bitmap? = null
    var backdropRadius: Float = -1f
    var backdropWidth: Int = 0

    const val NO_KEY = 0
    private const val DEFAULT_ASPECT_RATIO = 9f / 20f
}

/** Copies preview-safe fields and launcher-themed icons before Focus filtering. */
internal class FocusPreviewRecorder(
    itemInfo: Class<*>,
    private val context: Context,
    logger: Logger,
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
    private val widgetIds = mutableMapOf<Class<*>, java.lang.reflect.Field?>()

    init {
        // Which wallpaper is set is known by its colours, the one thing about
        // it the launcher may read, and a new one is asked for when they change.
        val store = FocusWallpaperStore(context, logger)
        val load = {
            store.load { key, wallpaper ->
                FocusPreviewModel.wallpaper = wallpaper
                FocusPreviewModel.wallpaperKey = key
            }
        }
        load()
        runCatching {
            WallpaperManager.getInstance(context).addOnColorsChangedListener(
                { colors, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0 && keyOf(colors) != FocusPreviewModel.wallpaperKey) load()
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    val isUsable: Boolean = listOf(id, container, screen, cellX, cellY, spanX, spanY, itemType)
        .none { it == null }

    fun remember(items: SparseArray<*>) {
        // Every picture the launcher's preview drew is of the model this replaces.
        FocusPageRenderer.invalidate()
        if (!isUsable) return

        FocusPreviewModel.records = buildList {
            for (index in 0 until items.size()) {
                val item = items.valueAt(index) ?: continue
                record(item)?.let(::add)
            }
        }
    }

    /** Measures the launcher window and its grid. */
    fun measure(activity: Activity) {
        WindowMeasure.measure(activity)
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
        )
    }.getOrNull()

    /** The widget's provider, for a page drawn by hand. */
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
        // Always ask for the themed icon: the launcher hands one back only when
        // themed icons are on.
        return runCatching { method?.invoke(item, context, FLAG_THEMED) as? Drawable }.getOrNull()
            ?: component?.let { activity ->
                runCatching { context.packageManager.getActivityIcon(activity) }.getOrNull()
            }
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
        const val APP_WIDGET_ID = "appWidgetId"
        const val ITEM_TYPE_WIDGET = 4
        const val FLAG_THEMED = 1
    }
}

/** Which wallpaper is set, as far as the launcher can tell: its colours. Never [FocusPreviewModel.NO_KEY]. */
internal fun keyOf(colors: WallpaperColors?): Int = (colors?.hashCode() ?: 1).let { if (it == 0) 1 else it }

/**
 * Keeps the measurements of the launcher window and of its workspace grid,
 * which a page is drawn against.
 */
private object WindowMeasure {

    fun measure(activity: Activity) {
        val root = activity.window.decorView
        if (root.width > 0 && root.height > 0) {
            FocusPreviewModel.aspectRatio = root.width.toFloat() / root.height
            FocusPreviewModel.windowWidth = root.width
            FocusPreviewModel.windowHeight = root.height
        }
        // The count is held in fields rather than behind getters on this launcher.
        val workspace = Reflect.field(activity.javaClass, WORKSPACE)?.get(activity) as? ViewGroup ?: return
        val page = workspace.getChildAt(0) ?: return
        runCatching {
            val x = Reflect.field(page.javaClass, COUNT_X)?.getInt(page) ?: return
            val y = Reflect.field(page.javaClass, COUNT_Y)?.getInt(page) ?: return
            if (x > 0 && y > 0) {
                FocusPreviewModel.columns = x
                FocusPreviewModel.rows = y
            }
        }
    }

    private const val WORKSPACE = "mWorkspace"
    private const val COUNT_X = "mCountX"
    private const val COUNT_Y = "mCountY"
}

/** Draws [drawable] to cover a [width] by [height] canvas, centred. */
internal fun drawCover(canvas: Canvas, drawable: Drawable, left: Float, top: Float, width: Float, height: Float) {
    if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return
    val scale = maxOf(width / drawable.intrinsicWidth, height / drawable.intrinsicHeight)
    val drawWidth = drawable.intrinsicWidth * scale
    val drawHeight = drawable.intrinsicHeight * scale
    val x = left + (width - drawWidth) / 2f
    val y = top + (height - drawHeight) / 2f
    drawable.setBounds(x.roundToInt(), y.roundToInt(), (x + drawWidth).roundToInt(), (y + drawHeight).roundToInt())
    drawable.draw(canvas)
}

/**
 * Builds page previews: the launcher's own picture of each page
 * ([FocusPageRenderer]) over the home wallpaper, blurred as home blurs it.
 *
 * @param context the screen the previews are shown on.
 * @param settings where home's blur is read, when a preview is asked for.
 */
internal class FocusPagePreviewSource(private val context: Context, private val settings: SettingsSource) {

    private val widgets = WidgetPreviewRenderer(context)

    /** @param assignments the pages each Mode has, which decide where on the wallpaper a page sits. */
    fun pages(screenIds: List<Int>, assignments: Map<String, Set<Int>>): Map<Int, FocusPagePreview> {
        val aspectRatio = FocusPreviewModel.aspectRatio
        val width = RENDER_WIDTH_PX
        val height = (width / aspectRatio).roundToInt().coerceAtLeast(1)
        FocusPageRenderer.retain(FocusPages.order)
        // Drawn again on every opening, not only after a workspace bind: a
        // widget changes what it shows without the model changing. The last
        // picture stays up until its replacement is ready.
        FocusPageRenderer.invalidate()
        FocusPageRenderer.request(context, screenIds, width, height)

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
        val order = FocusPages.order
        val rtl = context.resources.configuration.layoutDirection == LayoutDirection.RTL
        val cellWidth = FocusPreviewModel.windowWidth.toFloat() / columns
        val cellHeight = FocusPreviewModel.windowHeight * GRID_SHARE / rows

        return screenIds.associateWith { screenId ->
            FocusPagePreview(
                screen = screenId,
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
                        // Drawn only where the launcher's preview cannot draw the page:
                        // inflating every widget's preview layout is not free.
                        widgetPreview = record.widget?.takeUnless { FocusPageRenderer.isAvailable }?.let { info ->
                            widgets.render(
                                info,
                                (cellWidth * record.spanX).roundToInt(),
                                (cellHeight * record.spanY).roundToInt(),
                            )
                        },
                    )
                },
                hotseatIcons = hotseat,
                wallpaper = FocusPlan.shownWith(screenId, order, assignments).let { shown ->
                    val offset = wallpaperOffset(shown.indexOf(screenId), shown.size)
                    // A right-to-left home starts at the wallpaper's right edge.
                    backdrop(width, height, if (rtl) 1f - offset else offset)
                },
                aspectRatio = aspectRatio,
            )
        }
    }

    /**
     * The part of the wallpaper behind the page at [offset], at the size pages
     * are drawn, zoomed and blurred as home rests it.
     *
     * The wallpaper is the display's crop across every page; home shows one
     * screen's width of it, from the leading edge at [offset] 0 to the
     * trailing edge at 1, zoomed in about its centre by
     * `config_wallpaperMaxScale` less the depth home rests at. Home blurs at a
     * radius in full-screen pixels ([HomeBlurRadius]), shrunk here with the
     * page. Pages at the same offset share one picture, kept until the
     * wallpaper, the radius or the size changes. Main thread.
     */
    private fun backdrop(width: Int, height: Int, offset: Float): Drawable? {
        val wallpaper = FocusPreviewModel.wallpaper ?: return null
        val blurOn = settings[Settings.HOME_BLUR_WALLPAPER]
        val strength = settings[Settings.HOME_BLUR_STRENGTH]
        val screen = FocusPreviewModel.windowWidth.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val full = runCatching { HomeBlurRadius.of(context, blurOn, strength) }.getOrDefault(0)
        val radius = full.toFloat() * width / screen.coerceAtLeast(1)
        if (FocusPreviewModel.backdropFrom !== wallpaper || FocusPreviewModel.backdropRadius != radius ||
            FocusPreviewModel.backdropWidth != width
        ) {
            FocusPreviewModel.backdrops.clear()
            FocusPreviewModel.backdropFrom = wallpaper
            FocusPreviewModel.backdropRadius = radius
            FocusPreviewModel.backdropWidth = width
        }
        FocusPreviewModel.backdrops[offset]?.let { return it }

        val depth = if (radius > 0f) HomeBlurDepth.depthFor(strength) else 0f
        val section = wallpaperSection(wallpaper.width, wallpaper.height, width.toFloat() / height, offset, zoom(depth))
        val source = Rect(section[0].roundToInt(), section[1].roundToInt(), section[2].roundToInt(), section[3].roundToInt())
        val inner = RenderNode("Pixel Launcher Evolved wallpaper").apply {
            setPosition(0, 0, width, height)
            if (radius > 0f) setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
        val canvas = inner.beginRecording(width, height)
        try {
            canvas.drawBitmap(wallpaper, source, Rect(0, 0, width, height), PAINT)
        } finally {
            inner.endRecording()
        }
        val bitmap = HardwarePicture.record(width, height) { it.drawRenderNode(inner) } ?: return null
        return BitmapDrawable(context.resources, bitmap).also { FocusPreviewModel.backdrops[offset] = it }
    }

    /** How far home's wallpaper is zoomed in at rest, pushed back by [depth]. */
    private fun zoom(depth: Float): Float {
        val system = Resources.getSystem()
        val id = system.getIdentifier(MAX_SCALE, "dimen", "android")
        val max = if (id == 0) 1f else runCatching { system.getFloat(id) }.getOrDefault(1f)
        return max + (1f - max) * depth
    }

    private companion object {
        const val CONTAINER_DESKTOP = -100
        const val CONTAINER_HOTSEAT = -101
        const val ITEM_TYPE_FOLDER = 2
        const val ITEM_TYPE_WIDGET = 4
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val FOLDER_ICON_LIMIT = 4

        /** The share of the window the workspace grid takes, for a page drawn by hand. */
        const val GRID_SHARE = 0.62f

        /**
         * The width the launcher draws a page at: sharper than the largest
         * tile shows it, small enough to draw quickly.
         */
        const val RENDER_WIDTH_PX = 540

        const val MAX_SCALE = "config_wallpaperMaxScale"
        val PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
    }
}

/**
 * Where on the wallpaper the page at [index] of [count] sits, from 0 at the
 * leading edge to 1 at the trailing one: the launcher's
 * `WallpaperOffsetInterpolator`, which spreads the wallpaper over at least
 * [MIN_PARALLAX_PAGES] pages, so a home screen of fewer pages never reaches
 * the far edge.
 */
internal fun wallpaperOffset(index: Int, count: Int): Float {
    if (index <= 0 || count <= 1) return 0f
    return (index.toFloat() / (maxOf(count, MIN_PARALLAX_PAGES) - 1)).coerceIn(0f, 1f)
}

/**
 * The rectangle of a [width] by [height] wallpaper crop that a screen of
 * [aspect] (width over height) shows at [offset], zoomed in by [zoom] about
 * the screen's centre, as left, top, right, bottom. A crop narrower than the
 * screen is shown centred.
 */
internal fun wallpaperSection(width: Int, height: Int, aspect: Float, offset: Float, zoom: Float): FloatArray {
    var visibleWidth = height * aspect
    var visibleHeight = height.toFloat()
    if (visibleWidth > width) {
        visibleWidth = width.toFloat()
        visibleHeight = width / aspect
    }
    val left = offset.coerceIn(0f, 1f) * (width - visibleWidth)
    val top = (height - visibleHeight) / 2f
    val shownWidth = visibleWidth / zoom.coerceAtLeast(1f)
    val shownHeight = visibleHeight / zoom.coerceAtLeast(1f)
    val x = left + (visibleWidth - shownWidth) / 2f
    val y = top + (visibleHeight - shownHeight) / 2f
    return floatArrayOf(x, y, x + shownWidth, y + shownHeight)
}

private const val MIN_PARALLAX_PAGES = 4
