package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * The Material 3 Expressive pieces the At a Glance screen is made of: motion, filter chips, a
 * connected button group and toggle tiles. Colours come from the system's dynamic palette
 * ([ExpressiveRole]), so they follow the wallpaper and dark theme like the rest of Settings.
 */
internal object GlanceMotion {
    /** Emphasized decelerate: things arriving. */
    val ENTER = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized: things moving across. */
    val MOVE = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** A spring-like settle with a small overshoot, for shapes and presses. */
    val SPRING = OvershootInterpolator(1.6f)

    const val SHORT = 200L
    const val MEDIUM = 350L
    const val LONG = 500L

    /** Shrinks [view] a little while it is held and springs it back on release. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(view: View, scale: Float = 0.95f) {
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touched.animate().scaleX(scale).scaleY(scale).setDuration(SHORT).setInterpolator(MOVE).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    touched.animate().scaleX(1f).scaleY(1f).setDuration(MEDIUM).setInterpolator(SPRING).start()
            }
            false
        }
    }

    /** Animates [from] to [to] (colours), calling [onColor] each frame. */
    fun color(from: Int, to: Int, duration: Long = MEDIUM, onColor: (Int) -> Unit): ValueAnimator =
        ValueAnimator.ofArgb(from, to).apply {
            this.duration = duration
            interpolator = MOVE
            addUpdateListener { onColor(it.animatedValue as Int) }
            start()
        }
}

internal fun Context.dpOf(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

/** A rounded fill with a ripple kept inside it; [fill] is changed in place to animate. */
internal class Surface(context: Context, color: Int, radius: Float, stroke: Int = 0) {
    val fill = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
        if (stroke != 0) setStroke(context.dpOf(1f), stroke)
    }
    val drawable: Drawable = RippleDrawable(
        ColorStateList.valueOf(ExpressiveRole.ON_SURFACE.of(context).withAlpha(0x1F)),
        fill,
        GradientDrawable().apply { cornerRadius = radius; setColor(0xFF000000.toInt()) },
    )
    var color: Int = color
        set(value) {
            field = value
            fill.setColor(value)
        }
}

/**
 * A filter chip: an outlined pill that fills and shows a tick when selected, animated both ways.
 */
internal class GlanceChip(context: Context, label: CharSequence, private val icon: Drawable? = null) : LinearLayout(context) {

    private val on = ExpressiveRole.SECONDARY_CONTAINER.of(context)
    private val onText = ExpressiveRole.ON_SECONDARY_CONTAINER.of(context)
    private val offText = ExpressiveRole.ON_SURFACE_VARIANT.of(context)
    private val surface = Surface(context, 0, context.dpOf(10f).toFloat(), ExpressiveRole.OUTLINE_VARIANT.of(context))
    private val tick = ImageView(context)
    private val text = TextView(context)

    var selectedChip = false
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dpOf(36f)
        setPadding(context.dpOf(12f), 0, context.dpOf(16f), 0)
        background = surface.drawable
        isClickable = true
        isFocusable = true
        tick.setImageDrawable(CheckDrawable(context))
        addView(tick, LayoutParams(context.dpOf(18f), context.dpOf(18f)).apply { marginEnd = context.dpOf(8f) })
        icon?.let { drawable ->
            addView(ImageView(context).apply { setImageDrawable(drawable.mutate()) }, LayoutParams(context.dpOf(18f), context.dpOf(18f)).apply { marginEnd = context.dpOf(8f) })
        }
        addView(text.apply {
            this.text = label
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
        })
        GlanceMotion.pressable(this, 0.94f)
        draw(false, animate = false)
    }

    fun label(value: CharSequence) {
        text.text = value
    }

    fun select(selected: Boolean, animate: Boolean = true) {
        if (selected == selectedChip && animate) return
        draw(selected, animate)
    }

    private fun draw(selected: Boolean, animate: Boolean) {
        selectedChip = selected
        val to = if (selected) on else 0
        val textColor = if (selected) onText else offText
        if (animate) GlanceMotion.color(surface.color, to) { surface.color = it } else surface.color = to
        surface.fill.setStroke(context.dpOf(1f), if (selected) to else ExpressiveRole.OUTLINE_VARIANT.of(context))
        text.setTextColor(textColor)
        (getChildAt(1) as? ImageView)?.takeIf { icon != null }?.imageTintList = ColorStateList.valueOf(textColor)
        tick.imageTintList = ColorStateList.valueOf(textColor)
        if (animate) {
            if (selected) {
                tick.visibility = VISIBLE
                tick.scaleX = 0f; tick.scaleY = 0f
                tick.animate().scaleX(1f).scaleY(1f).setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.SPRING).start()
            } else {
                tick.animate().scaleX(0f).scaleY(0f).setDuration(GlanceMotion.SHORT).withEndAction { if (!selectedChip) tick.visibility = GONE }.start()
            }
        } else {
            tick.visibility = if (selected) VISIBLE else GONE
        }
    }
}

/** A tick drawn as a path, so a chip needs no drawable resource of its own. */
private class CheckDrawable(context: Context) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = context.dpOf(2f).toFloat()
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val path = android.graphics.Path()
    private var tint = 0xFFFFFFFF.toInt()

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        path.reset()
        path.moveTo(b.left + b.width() * 0.2f, b.top + b.height() * 0.52f)
        path.lineTo(b.left + b.width() * 0.42f, b.top + b.height() * 0.72f)
        path.lineTo(b.left + b.width() * 0.8f, b.top + b.height() * 0.3f)
        paint.color = tint
        canvas.drawPath(path, paint)
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint?.defaultColor ?: this.tint
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/**
 * A connected button group with one choice: the chosen button fills with the primary colour and
 * becomes fully round, the others keep small inner corners, as Material 3 Expressive groups morph.
 */
internal class GlanceButtonGroup(context: Context, labels: List<CharSequence>, chosen: Int, private val onChoose: (Int) -> Unit) : LinearLayout(context) {

    private val buttons = labels.map { TextView(context) }
    private val surfaces = ArrayList<Surface>()
    private var current = chosen

    init {
        orientation = HORIZONTAL
        val full = context.dpOf(24f).toFloat()
        buttons.forEachIndexed { index, button ->
            val surface = Surface(context, 0, full)
            surfaces += surface
            button.apply {
                text = labels[index]
                gravity = Gravity.CENTER
                ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                background = surface.drawable
                minHeight = context.dpOf(48f)
                setPadding(context.dpOf(16f), 0, context.dpOf(16f), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { choose(index) }
                GlanceMotion.pressable(this, 0.96f)
            }
            addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = context.dpOf(2f) })
        }
        draw(animate = false)
    }

    private fun choose(index: Int) {
        if (index == current) return
        current = index
        draw(animate = true)
        onChoose(index)
    }

    private fun draw(animate: Boolean) {
        val full = context.dpOf(24f).toFloat()
        val inner = context.dpOf(8f).toFloat()
        buttons.forEachIndexed { index, button ->
            val chosen = index == current
            val surface = surfaces[index]
            val to = if (chosen) ExpressiveRole.PRIMARY.of(context) else ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context)
            button.setTextColor(if (chosen) ExpressiveRole.ON_PRIMARY.of(context) else ExpressiveRole.ON_SURFACE.of(context))
            val start = if (chosen || index == 0) full else inner
            val end = if (chosen || index == buttons.lastIndex) full else inner
            val corners = if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) floatArrayOf(end, end, start, start, start, start, end, end)
            else floatArrayOf(start, start, end, end, end, end, start, start)
            if (animate) {
                GlanceMotion.color(surface.color, to) { surface.color = it }
                val from = surface.fill.cornerRadii ?: corners
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = GlanceMotion.MEDIUM
                    interpolator = GlanceMotion.SPRING
                    addUpdateListener { a ->
                        val t = a.animatedFraction
                        surface.fill.cornerRadii = FloatArray(8) { i -> from[i] + (corners[i] - from[i]) * t }
                    }
                    start()
                }
            } else {
                surface.color = to
                surface.fill.cornerRadii = corners
            }
        }
    }
}

/**
 * A toggle tile, as Quick Settings draws one: an icon in a circle, a name and a state line. On, it
 * fills with the primary container colour and its icon circle with the primary colour; the change
 * is animated, and a press squeezes the tile. A small sliders button opens the tile's own settings.
 */
internal class GlanceTile(context: Context, icon: Drawable?, title: CharSequence, onSettings: () -> Unit) : FrameLayout(context) {

    private val radius = context.dpOf(28f).toFloat()
    private val surface = Surface(context, ExpressiveRole.SURFACE_BRIGHT.of(context), radius)
    private val badge = Surface(context, ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context), context.dpOf(20f).toFloat())
    private val image = ImageView(context)
    private val name = TextView(context)
    private val state = TextView(context)
    private val tune = ImageView(context)
    private var on = false

    init {
        background = surface.drawable
        minimumHeight = context.dpOf(120f)
        isClickable = true
        isFocusable = true
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dpOf(16f), context.dpOf(16f), context.dpOf(16f), context.dpOf(14f))
            addView(FrameLayout(context).apply {
                background = badge.drawable
                addView(image.apply { setImageDrawable(icon?.mutate()) }, LayoutParams(context.dpOf(22f), context.dpOf(22f), Gravity.CENTER))
            }, LinearLayout.LayoutParams(context.dpOf(40f), context.dpOf(40f)))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(name.apply {
                text = title
                ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dpOf(12f) })
            addView(state.apply {
                ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        tune.apply {
            background = Surface(context, 0, context.dpOf(20f).toFloat()).drawable
            setPadding(context.dpOf(10f), context.dpOf(10f), context.dpOf(10f), context.dpOf(10f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onSettings() }
        }
        addView(tune, LayoutParams(context.dpOf(44f), context.dpOf(44f), Gravity.TOP or Gravity.END).apply { topMargin = context.dpOf(6f); marginEnd = context.dpOf(6f) })
        GlanceMotion.pressable(this, 0.95f)
        draw(animate = false)
    }

    fun tuneIcon(drawable: Drawable?, description: CharSequence) {
        tune.setImageDrawable(drawable?.mutate())
        tune.contentDescription = description
        draw(animate = false)
    }

    fun set(on: Boolean, status: CharSequence, animate: Boolean) {
        val changed = on != this.on
        this.on = on
        state.text = status
        if (changed || !animate) draw(animate)
        contentDescription = "${name.text}, $status"
    }

    private fun draw(animate: Boolean) {
        val fill = if (on) ExpressiveRole.PRIMARY_CONTAINER.of(context) else ExpressiveRole.SURFACE_BRIGHT.of(context)
        val circle = if (on) ExpressiveRole.PRIMARY.of(context) else ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context)
        val glyph = if (on) ExpressiveRole.ON_PRIMARY.of(context) else ExpressiveRole.ON_SURFACE_VARIANT.of(context)
        val text = if (on) ExpressiveRole.ON_PRIMARY_CONTAINER.of(context) else ExpressiveRole.ON_SURFACE.of(context)
        if (animate) {
            GlanceMotion.color(surface.color, fill) { surface.color = it }
            GlanceMotion.color(badge.color, circle) { badge.color = it }
            // Shape morph: the circle squares off a little when on, the tile rounds further.
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = GlanceMotion.LONG
                interpolator = GlanceMotion.SPRING
                val tileFrom = surface.fill.cornerRadius
                val tileTo = if (on) radius * 1.3f else radius
                val badgeFrom = badge.fill.cornerRadius
                val badgeTo = context.dpOf(if (on) 14f else 20f).toFloat()
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    surface.fill.cornerRadius = tileFrom + (tileTo - tileFrom) * t
                    badge.fill.cornerRadius = badgeFrom + (badgeTo - badgeFrom) * t
                }
                start()
            }
            image.animate().rotationBy(if (on) 360f else -360f).setDuration(GlanceMotion.LONG).setInterpolator(GlanceMotion.MOVE).start()
        } else {
            surface.color = fill
            badge.color = circle
            surface.fill.cornerRadius = if (on) radius * 1.3f else radius
            badge.fill.cornerRadius = context.dpOf(if (on) 14f else 20f).toFloat()
        }
        image.imageTintList = ColorStateList.valueOf(glyph)
        tune.imageTintList = ColorStateList.valueOf(if (on) ExpressiveRole.ON_PRIMARY_CONTAINER.of(context) else ExpressiveRole.ON_SURFACE_VARIANT.of(context))
        name.setTextColor(text)
        state.setTextColor(if (on) text else ExpressiveRole.ON_SURFACE_VARIANT.of(context))
    }
}

/** Lays [views] out two to a row, each row as tall as its tallest tile. */
internal fun tileGrid(context: Context, views: List<View>): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    val gap = context.dpOf(8f)
    for (start in views.indices step 2) {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            for (i in start until minOf(start + 2, views.size)) {
                addView(views[i], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > start) marginStart = gap })
            }
            if (start + 1 >= views.size) addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = gap })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (start > 0) topMargin = gap })
    }
}
