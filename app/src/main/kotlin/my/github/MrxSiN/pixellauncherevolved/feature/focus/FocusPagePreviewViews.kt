package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

import androidx.core.graphics.withClip

import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages

/** iOS-style miniature of one live launcher page. */
@SuppressLint("ViewConstructor")
internal class FocusPagePreviewView(
    context: Context,
    private val preview: FocusPagePreview,
    private val label: String,
    private val compact: Boolean = false,
) : View(context) {

    var checked: Boolean = false
        set(value) {
            field = value
            isSelected = value
            contentDescription = "$label, ${if (value) "selected" else "not selected"}"
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = themeColor(android.R.attr.colorAccent, Color.rgb(80, 105, 255))
    private val text = themeColor(android.R.attr.textColorPrimary, Color.WHITE)
    private val page = RectF()
    private val clip = Path()

    init {
        isClickable = !compact
        isFocusable = !compact
        checked = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wantedWidth = dp(if (compact) 38 else 148)
        val measuredWidth = resolveSize(wantedWidth, widthMeasureSpec)
        val wantedHeight = heightForWidth(context, preview, measuredWidth, compact)
        setMeasuredDimension(
            measuredWidth,
            resolveSize(wantedHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = dp(if (compact) 1 else 5).toFloat()
        val labelHeight = if (compact) 0f else dp(34).toFloat()
        page.set(padding, padding, width - padding, height - padding - labelHeight)
        val radius = dp(if (compact) 7 else 15).toFloat()

        paint.color = Color.argb(35, 0, 0, 0)
        canvas.drawRoundRect(page.left + dp(2), page.top + dp(3), page.right + dp(2), page.bottom + dp(3), radius, radius, paint)

        clip.reset()
        clip.addRoundRect(page, radius, radius, Path.Direction.CW)
        canvas.withClip(clip) {
            val snapshot = preview.snapshot
            if (snapshot != null) {
                paint.alpha = 255
                drawBitmap(snapshot, null, page, paint)
            } else {
                drawWallpaper(this)
                paint.color = Color.argb(32, 0, 0, 0)
                drawRect(page, paint)
                drawPageContents(this)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(if (checked) 4 else 1).toFloat()
        paint.color = if (checked) accent else Color.argb(90, 255, 255, 255)
        canvas.drawRoundRect(page, radius, radius, paint)
        paint.style = Paint.Style.FILL

        if (!compact) {
            if (checked) drawCheck(canvas)
            paint.color = text
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = sp(14)
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            canvas.drawText(label, width / 2f, height - dp(7).toFloat(), paint)
        }
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

        val scale = maxOf(
            page.width() / wallpaper.intrinsicWidth,
            page.height() / wallpaper.intrinsicHeight,
        )
        val drawWidth = wallpaper.intrinsicWidth * scale
        val drawHeight = wallpaper.intrinsicHeight * scale
        val left = page.centerX() - drawWidth / 2f
        val top = page.centerY() - drawHeight / 2f
        wallpaper.setBounds(
            left.roundToInt(),
            top.roundToInt(),
            (left + drawWidth).roundToInt(),
            (top + drawHeight).roundToInt(),
        )
        wallpaper.draw(canvas)
    }

    private fun drawPageContents(canvas: Canvas) {
        val top = page.top + page.height() * 0.11f
        val bottom = page.top + page.height() * 0.73f
        val cellWidth = page.width() / preview.columns.coerceAtLeast(1)
        val cellHeight = (bottom - top) / preview.rows.coerceAtLeast(1)

        for (item in preview.items) {
            val bounds = RectF(
                page.left + item.cellX * cellWidth,
                top + item.cellY * cellHeight,
                page.left + (item.cellX + item.spanX) * cellWidth,
                top + (item.cellY + item.spanY) * cellHeight,
            )
            when {
                item.isWidget -> drawWidget(canvas, bounds, item)
                item.folderIcons.isNotEmpty() -> drawFolder(canvas, bounds, item.folderIcons)
                else -> drawIcon(canvas, bounds, item.icon)
            }
        }

        val search = RectF(
            page.left + page.width() * 0.08f,
            page.top + page.height() * 0.78f,
            page.right - page.width() * 0.08f,
            page.top + page.height() * 0.86f,
        )
        paint.color = Color.argb(190, 235, 235, 240)
        canvas.drawRoundRect(search, search.height() / 2f, search.height() / 2f, paint)
        paint.color = Color.argb(150, 60, 65, 75)
        canvas.drawCircle(search.left + search.height() * 0.5f, search.centerY(), search.height() * 0.18f, paint)

        val hotseatY = page.top + page.height() * 0.92f
        val slots = maxOf(preview.hotseatIcons.size, 4)
        val slotWidth = page.width() / slots
        preview.hotseatIcons.forEachIndexed { index, icon ->
            val centerX = page.left + slotWidth * (index + 0.5f)
            val size = min(slotWidth, page.height() * 0.07f) * 0.7f
            drawDrawable(canvas, icon, centerX, hotseatY, size)
        }
    }

    private fun drawWidget(canvas: Canvas, bounds: RectF, item: FocusPreviewItem) {
        val inset = min(bounds.width(), bounds.height()) * 0.08f
        val widget = RectF(bounds).apply { inset(inset, inset) }
        paint.color = Color.argb(190, 238, 238, 244)
        canvas.drawRoundRect(widget, dp(5).toFloat(), dp(5).toFloat(), paint)
        val size = min(widget.width(), widget.height()) * 0.34f
        drawDrawable(canvas, item.icon, widget.centerX(), widget.centerY(), size)
    }

    private fun drawFolder(canvas: Canvas, bounds: RectF, icons: List<android.graphics.drawable.Drawable>) {
        val size = min(bounds.width(), bounds.height()) * 0.58f
        paint.color = Color.argb(185, 225, 225, 235)
        canvas.drawRoundRect(
            bounds.centerX() - size / 2f,
            bounds.centerY() - size / 2f,
            bounds.centerX() + size / 2f,
            bounds.centerY() + size / 2f,
            size * 0.28f,
            size * 0.28f,
            paint,
        )
        val mini = size * 0.3f
        icons.take(4).forEachIndexed { index, icon ->
            val x = bounds.centerX() + (if (index % 2 == 0) -1 else 1) * size * 0.2f
            val y = bounds.centerY() + (if (index < 2) -1 else 1) * size * 0.2f
            drawDrawable(canvas, icon, x, y, mini)
        }
    }

    private fun drawIcon(canvas: Canvas, bounds: RectF, icon: android.graphics.drawable.Drawable?) {
        val size = min(bounds.width(), bounds.height()) * 0.58f
        if (icon == null) {
            paint.color = Color.argb(210, 230, 230, 235)
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), size / 2f, paint)
        } else {
            drawDrawable(canvas, icon, bounds.centerX(), bounds.centerY(), size)
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

    private fun drawCheck(canvas: Canvas) {
        val radius = dp(13).toFloat()
        val centerX = page.right - radius - dp(8)
        val centerY = page.top + radius + dp(8)
        paint.color = accent
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.WHITE
        val check = Path().apply {
            moveTo(centerX - radius * 0.45f, centerY)
            lineTo(centerX - radius * 0.1f, centerY + radius * 0.34f)
            lineTo(centerX + radius * 0.5f, centerY - radius * 0.35f)
        }
        canvas.drawPath(check, paint)
        paint.style = Paint.Style.FILL
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            values.getColor(0, fallback)
        } finally {
            values.recycle()
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value.toFloat(),
        resources.displayMetrics,
    )

    companion object {
        fun heightForWidth(
            context: Context,
            preview: FocusPagePreview,
            widthPx: Int,
            compact: Boolean,
        ): Int {
            val density = context.resources.displayMetrics.density
            val padding = ((if (compact) 1 else 5) * density).roundToInt()
            val label = ((if (compact) 0 else 34) * density).roundToInt()
            val pageWidth = (widthPx - padding * 2).coerceAtLeast(1)
            val pageHeight = (pageWidth / preview.aspectRatio.coerceAtLeast(0.1f)).roundToInt()
            return pageHeight + padding * 2 + label
        }
    }
}

/** Redesigned Mode list with status and miniature assigned-page previews. */
internal class FocusModeAdapter(
    private val context: Context,
    private val modes: List<FocusMode>,
    private val assignments: Map<String, Set<Int>>,
    private val previews: Map<Int, FocusPagePreview>,
    private val pageLabel: (Int) -> String,
    private val summary: (Set<Int>?) -> String,
    private val activeLabel: String,
) : BaseAdapter() {

    override fun getCount(): Int = modes.size

    override fun getItem(position: Int): FocusMode = modes[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val mode = getItem(position)
        val owned = assignments[mode.id].orEmpty()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(108)
            setPadding(dp(20), dp(10), dp(16), dp(10))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = mode.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(themeTextColor(context))
                })
                addView(TextView(context).apply {
                    text = summary(owned)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    alpha = 0.72f
                    setTextColor(themeTextColor(context))
                })
                if (mode.isActive) {
                    addView(TextView(context).apply {
                        text = activeLabel
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(themeAccentColor(context))
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val ordered = owned.mapNotNull { screen ->
                FocusPages.numberOf(screen)?.let { number -> Triple(number, screen, previews[screen]) }
            }.sortedBy(Triple<Int, Int, FocusPagePreview?>::first)
            ordered.take(3).forEach { (number, _, preview) ->
                if (preview != null) {
                    addView(
                        FocusPagePreviewView(context, preview, pageLabel(number), compact = true),
                        LinearLayout.LayoutParams(
                            dp(38),
                            FocusPagePreviewView.heightForWidth(context, preview, dp(38), compact = true),
                        ).apply { marginEnd = dp(6) },
                    )
                }
            }

            addView(TextView(context).apply {
                text = "›"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                alpha = 0.55f
                setTextColor(themeTextColor(context))
            }, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun themeTextColor(context: Context): Int = themeColor(
        context,
        android.R.attr.textColorPrimary,
        Color.WHITE,
    )

    private fun themeAccentColor(context: Context): Int = themeColor(
        context,
        android.R.attr.colorAccent,
        Color.rgb(80, 105, 255),
    )

    private fun themeColor(context: Context, attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            values.getColor(0, fallback)
        } finally {
            values.recycle()
        }
    }
}

/** Two-column selectable page gallery. */
internal class FocusPageAdapter(
    private val context: Context,
    private val pages: List<Int>,
    private val previews: Map<Int, FocusPagePreview>,
    selected: Set<Int>,
    private val pageLabel: (Int) -> String,
) : BaseAdapter() {

    private val checked = BooleanArray(pages.size) { index -> pages[index] in selected }

    override fun getCount(): Int = pages.size

    override fun getItem(position: Int): Int = pages[position]

    override fun getItemId(position: Int): Long = getItem(position).toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val screen = getItem(position)
        val preview = requireNotNull(previews[screen])
        val density = context.resources.displayMetrics.density
        val view = FocusPagePreviewView(context, preview, pageLabel(position + 1)).apply {
            checked = this@FocusPageAdapter.checked[position]
            setOnClickListener {
                this@FocusPageAdapter.checked[position] = !this@FocusPageAdapter.checked[position]
                notifyDataSetChanged()
            }
        }
        view.layoutParams = AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            FocusPagePreviewView.heightForWidth(
                context,
                preview,
                (148 * density).roundToInt(),
                compact = false,
            ),
        )
        return view
    }

    fun selectedScreens(): Set<Int> = pages.filterIndexed { index, _ -> checked[index] }.toSet()
}
