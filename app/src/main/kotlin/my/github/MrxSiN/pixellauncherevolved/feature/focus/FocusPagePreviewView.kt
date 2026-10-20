package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View


import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole

/**
 * A miniature of one live launcher page.
 *
 * The launcher's own picture of the page over the blurred home wallpaper,
 * drawn from the model only where the launcher cannot draw it. How it is
 * framed depends on [look].
 */
@SuppressLint("ViewConstructor")
internal class FocusPagePreviewView(
    context: Context,
    private val preview: FocusPagePreview,
    private val look: Look = Look.SELECTABLE,
) : View(context) {

    enum class Look {
        /** The small page a Mode's row stacks, outlined to read apart from the next. */
        THUMBNAIL,

        /**
         * A page that can be chosen: a chosen page shrinks inside a primary
         * outline and its corners grow rounder, on a spring, with a filled check.
         */
        SELECTABLE,

        /** A page on its own, rounded like the rest and with nothing to choose. */
        TILE,
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val primary = ExpressiveRole.PRIMARY.of(context)
    private val onPrimary = ExpressiveRole.ON_PRIMARY.of(context)
    private val outline = ExpressiveRole.SURFACE_BRIGHT.of(context)
    private val page = RectF()
    private val clip = Path()
    private val check = Path()

    /** How selected the page is drawn, 0 to 1, overshooting while the spring settles. */
    private var selection = 0f
    private var animator: ValueAnimator? = null

    var checked: Boolean = false
        private set

    fun setChecked(value: Boolean, animate: Boolean) {
        checked = value
        animator?.cancel()
        val target = if (value) 1f else 0f
        if (!animate) {
            selection = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(selection, target).apply {
            duration = SELECTION_MILLIS
            interpolator = ExpressiveMotion.spatialSpring(SELECTION_MILLIS)
            addUpdateListener {
                selection = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = if (look == Look.THUMBNAIL) COMPACT_WIDTH_DP else 148f
        val measuredWidth = resolveSize(dp(wanted).roundToInt(), widthMeasureSpec)
        setMeasuredDimension(measuredWidth, resolveSize(heightForWidth(preview, measuredWidth), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (look) {
            Look.THUMBNAIL -> drawCompact(canvas)
            Look.SELECTABLE -> drawSelectable(canvas)
            Look.TILE -> {
                page.set(0f, 0f, width.toFloat(), height.toFloat())
                drawPage(canvas, dp(RADIUS_DP))
            }
        }
    }

    /** A thumbnail, outlined in the card's own fill so a stack of them reads as separate pages. */
    private fun drawCompact(canvas: Canvas) {
        val stroke = dp(COMPACT_OUTLINE_DP)
        page.set(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f)
        val radius = dp(COMPACT_RADIUS_DP)
        drawPage(canvas, radius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = outline
        canvas.drawRoundRect(page, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSelectable(canvas: Canvas) {
        val chosen = selection.coerceIn(0f, 1f)
        val inset = dp(SELECTED_INSET_DP) * selection
        val outerRadius = dp(RADIUS_DP) + dp(SELECTED_RADIUS_GROWTH_DP) * chosen
        page.set(inset, inset, width - inset, height - inset)
        // The page grows rounder as it shrinks, so its corners stay concentric
        // with the outline around it.
        val radius = (outerRadius - inset).coerceAtLeast(dp(RADIUS_DP))
        drawPage(canvas, radius)

        if (chosen > 0f) {
            val stroke = dp(OUTLINE_DP)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = primary
            paint.alpha = (255 * chosen).roundToInt()
            canvas.drawRoundRect(
                stroke / 2f,
                stroke / 2f,
                width - stroke / 2f,
                height - stroke / 2f,
                outerRadius,
                outerRadius,
                paint,
            )
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
        drawIndicator(canvas, chosen)
    }

    private fun drawPage(canvas: Canvas, radius: Float) {
        clip.reset()
        clip.addRoundRect(page, radius, radius, Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clip)
        drawWallpaper(canvas)
        val picture = FocusPageRenderer.picture(preview.screen)
        if (picture != null) {
            paint.color = Color.WHITE
            canvas.drawBitmap(picture, null, page, paint)
        } else if (FocusPageRenderer.state(preview.screen) != FocusPageRenderer.State.PENDING) {
            // Only where the launcher cannot draw the page. While its picture
            // is on the way the page shows its wallpaper alone, never a guess.
            paint.color = Color.argb(32, 0, 0, 0)
            canvas.drawRect(page, paint)
            drawPageContents(canvas)
        }
        canvas.restoreToCount(saved)
    }

    private val redraw = Runnable { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        FocusPageRenderer.addListener(redraw)
    }

    override fun onDetachedFromWindow() {
        FocusPageRenderer.removeListener(redraw)
        super.onDetachedFromWindow()
    }

    /**
     * The corner indicator: an empty ring over a scrim when not chosen, a filled
     * primary circle with a check when chosen, crossfading as the spring runs.
     */
    private fun drawIndicator(canvas: Canvas, chosen: Float) {
        val radius = dp(INDICATOR_RADIUS_DP)
        val centerX = page.right - radius - dp(INDICATOR_MARGIN_DP)
        val centerY = page.top + radius + dp(INDICATOR_MARGIN_DP)

        paint.color = Color.argb((SCRIM_ALPHA * (1f - chosen)).roundToInt(), 0, 0, 0)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(RING_DP)
        paint.color = Color.argb((255 * (1f - chosen)).roundToInt(), 255, 255, 255)
        canvas.drawCircle(centerX, centerY, radius - dp(RING_DP) / 2f, paint)
        paint.style = Paint.Style.FILL

        if (chosen <= 0f) return
        val grown = radius * selection.coerceAtLeast(0f)
        paint.color = primary
        canvas.drawCircle(centerX, centerY, grown, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = dp(CHECK_STROKE_DP)
        paint.color = onPrimary
        paint.alpha = (255 * chosen).roundToInt()
        check.reset()
        check.moveTo(centerX - grown * 0.42f, centerY + grown * 0.02f)
        check.lineTo(centerX - grown * 0.12f, centerY + grown * 0.32f)
        check.lineTo(centerX + grown * 0.44f, centerY - grown * 0.3f)
        canvas.drawPath(check, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawWallpaper(canvas: Canvas) {
        val wallpaper = preview.wallpaper
        if (wallpaper == null || wallpaper.intrinsicWidth <= 0 || wallpaper.intrinsicHeight <= 0) {
            paint.shader = LinearGradient(
                page.left,
                page.top,
                page.right,
                page.bottom,
                Color.rgb(55, 67, 116),
                Color.rgb(113, 72, 131),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(page, paint)
            paint.shader = null
            return
        }
        drawCover(canvas, wallpaper, page.left, page.top, page.width(), page.height())
    }

    /**
     * A page the launcher's preview could not draw, drawn from its model.
     *
     * ponytail: a guessed grid, no labels and no dock band; this only stands in
     * where the launcher's own preview is missing or failed.
     */
    private fun drawPageContents(canvas: Canvas) {
        val grid = RectF(page.left, page.top + page.height() * 0.11f, page.right, page.top + page.height() * 0.73f)
        val cellWidth = grid.width() / preview.columns.coerceAtLeast(1)
        val cellHeight = grid.height() / preview.rows.coerceAtLeast(1)
        val iconSize = min(cellWidth, cellHeight) * 0.58f

        for (item in preview.items) {
            val bounds = RectF(
                grid.left + item.cellX * cellWidth,
                grid.top + item.cellY * cellHeight,
                grid.left + (item.cellX + item.spanX) * cellWidth,
                grid.top + (item.cellY + item.spanY) * cellHeight,
            )
            if (item.isWidget) {
                drawWidget(canvas, bounds, item)
            } else if (item.folderIcons.isNotEmpty()) {
                drawFolder(canvas, bounds.centerX(), bounds.centerY(), iconSize, item.folderIcons)
            } else {
                drawDrawable(canvas, item.icon, bounds.centerX(), bounds.centerY(), iconSize)
            }
        }

        val band = RectF(page.left, page.top + page.height() * 0.87f, page.right, page.bottom - page.height() * 0.03f)
        val slots = maxOf(preview.hotseatIcons.size, 4)
        val slotWidth = band.width() / slots
        val size = min(slotWidth, page.height() * 0.07f) * 0.7f
        preview.hotseatIcons.forEachIndexed { index, icon ->
            drawDrawable(canvas, icon, band.left + slotWidth * (index + 0.5f), band.bottom - band.width() / slots / 2f, size)
        }
    }

    /**
     * A widget on a page drawn from the model.
     *
     * Its own preview picture fills its footprint, the way the widget fills its
     * cells, with the rounding a widget on the home screen has. There is no
     * panel behind it: a live widget draws its own background, and a dark box
     * behind a preview that brings its own was a second, wrong colour.
     */
    private fun drawWidget(canvas: Canvas, bounds: RectF, item: FocusPreviewItem) {
        val inset = min(bounds.width(), bounds.height()) * WIDGET_INSET
        val widget = RectF(bounds).apply { inset(inset, inset) }
        val corner = min(widget.width(), widget.height()) * WIDGET_CORNER

        val preview = item.widgetPreview
        if (preview != null && preview.intrinsicWidth > 0 && preview.intrinsicHeight > 0) {
            clip.reset()
            clip.addRoundRect(widget, corner, corner, Path.Direction.CW)
            val saved = canvas.save()
            canvas.clipPath(clip)
            // Fitted rather than cropped, so whatever the widget is
            // recognised by stays in the picture.
            val scale = min(
                widget.width() / preview.intrinsicWidth,
                widget.height() / preview.intrinsicHeight,
            )
            val drawWidth = preview.intrinsicWidth * scale
            val drawHeight = preview.intrinsicHeight * scale
            val left = widget.centerX() - drawWidth / 2f
            val top = widget.centerY() - drawHeight / 2f
            preview.setBounds(
                left.roundToInt(),
                top.roundToInt(),
                (left + drawWidth).roundToInt(),
                (top + drawHeight).roundToInt(),
            )
            preview.draw(canvas)
            canvas.restoreToCount(saved)
            return
        }

        val size = min(widget.width(), widget.height()) * 0.34f
        drawDrawable(canvas, item.icon, widget.centerX(), widget.centerY(), size)
    }

    private fun drawFolder(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        icons: List<android.graphics.drawable.Drawable>,
    ) {
        paint.color = Color.argb(120, 18, 18, 22)
        canvas.drawCircle(centerX, centerY, size / 2f, paint)
        val mini = size * 0.32f
        icons.take(4).forEachIndexed { index, icon ->
            val x = centerX + (if (index % 2 == 0) -1 else 1) * size * 0.19f
            val y = centerY + (if (index < 2) -1 else 1) * size * 0.19f
            drawDrawable(canvas, icon, x, y, mini)
        }
    }

    private fun drawDrawable(
        canvas: Canvas,
        drawable: android.graphics.drawable.Drawable?,
        centerX: Float,
        centerY: Float,
        size: Float,
    ) {
        if (drawable == null) return
        drawable.setBounds(
            (centerX - size / 2f).roundToInt(),
            (centerY - size / 2f).roundToInt(),
            (centerX + size / 2f).roundToInt(),
            (centerY + size / 2f).roundToInt(),
        )
        drawable.draw(canvas)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun dp(value: Float): Float = value * density

    companion object {
        /** A page keeps its own proportions at whatever width it is given. */
        fun heightForWidth(preview: FocusPagePreview, widthPx: Int): Int =
            (widthPx / preview.aspectRatio.coerceAtLeast(0.1f)).roundToInt()

        const val COMPACT_WIDTH_DP = 32f

        private const val RADIUS_DP = 16f
        private const val SELECTED_RADIUS_GROWTH_DP = 12f
        private const val SELECTED_INSET_DP = 7f
        private const val OUTLINE_DP = 3f
        private const val COMPACT_RADIUS_DP = 6f
        private const val COMPACT_OUTLINE_DP = 1.5f

        private const val INDICATOR_RADIUS_DP = 12f
        private const val INDICATOR_MARGIN_DP = 8f
        private const val RING_DP = 2f
        private const val CHECK_STROKE_DP = 2.5f
        private const val SCRIM_ALPHA = 70f

        private const val SELECTION_MILLIS = 500L

        /** A widget's own padding inside its cells, and its corner, as shares of its size. */
        private const val WIDGET_INSET = 0.04f
        private const val WIDGET_CORNER = 0.12f

    }
}
