package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.SeekBar

/**
 * Draws a `SeekBar` the way Material 3 Expressive draws a slider.
 *
 * The launcher's theme leaves `SeekBarPreference` with the platform's own thin
 * track and round knob, which reads as an older Android beside the switches
 * above and below it. Material 3 Expressive gives a slider a tall rounded
 * track, a handle that is a vertical bar rather than a circle, and a gap
 * holding the track off the handle on both sides.
 *
 * Only the drawing is replaced. The colours are the launcher's own, resolved
 * off the theme the row was inflated with, so the slider follows whatever
 * wallpaper colours are in force rather than carrying a palette of its own.
 * They are resolved through the framework's own attributes rather than by name,
 * because the launcher's resource names do not survive its build.
 */
object ExpressiveSlider {

    fun applyTo(seekBar: SeekBar) {
        val context = seekBar.context
        val track = context.dp(TRACK_HEIGHT_DP)
        val handleWidth = context.dp(HANDLE_WIDTH_DP)
        val handleHeight = context.dp(HANDLE_HEIGHT_DP)

        val active = context.themeColor(ACTIVE_ATTRS, fallback = Color.WHITE)
        val inactive = active.withAlpha(INACTIVE_ALPHA)

        val expressive = ExpressiveTrack(
            active = active,
            inactive = inactive,
            height = track,
            inset = context.dp(GAP_DP) + handleWidth / 2f,
        )
        seekBar.progressDrawable = expressive
        // The bar reports progress to a drawable as that drawable's level, and
        // it reports it when the progress moves. This one arrives after the row
        // has its value, so the first level is the one it has to be told.
        expressive.level = levelOf(seekBar)
        seekBar.thumb = handle(active, handleWidth, handleHeight)
        seekBar.splitTrack = false
        seekBar.thumbOffset = 0

        // The drawables carry their own colours; a tint left over from the
        // platform's style would repaint every one of them.
        seekBar.progressTintList = null
        seekBar.progressBackgroundTintList = null
        seekBar.thumbTintList = null

        // A bar is only as tall as the track it is told to hold, and the handle
        // stands taller than the track, so the view has to make room for both.
        seekBar.minHeight = track
        seekBar.maxHeight = track
        seekBar.minimumHeight = handleHeight
        seekBar.setPadding(handleWidth, seekBar.paddingTop, handleWidth, seekBar.paddingBottom)
    }

    /** Where the bar stands now, in the 0..10000 a drawable reads as its level. */
    private fun levelOf(seekBar: SeekBar): Int {
        val span = seekBar.max - seekBar.min
        if (span <= 0) return 0

        return (MAX_LEVEL * (seekBar.progress - seekBar.min) / span)
    }

    private fun handle(color: Int, width: Int, height: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = width / 2f
        setSize(width, height)
    }

    /**
     * The first of [attrs] the theme answers with a colour.
     *
     * These are framework attributes, so they are the same numbers in every
     * process and need no lookup by name.
     */
    private fun Context.themeColor(attrs: IntArray, fallback: Int): Int {
        val value = TypedValue()

        for (attr in attrs) {
            if (!theme.resolveAttribute(attr, value, true)) continue

            if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data
            }
            if (value.resourceId != 0) {
                runCatching { return resources.getColor(value.resourceId, theme) }
            }
        }

        return fallback
    }

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)

    /** Material 3 Expressive: a tall track, a handle that is a bar, and a gap. */
    private const val TRACK_HEIGHT_DP = 16f
    private const val HANDLE_WIDTH_DP = 4f
    private const val HANDLE_HEIGHT_DP = 44f
    private const val GAP_DP = 6f

    private const val INACTIVE_ALPHA = 0x3D
    private const val MAX_LEVEL = 10_000

    private val ACTIVE_ATTRS = intArrayOf(android.R.attr.colorAccent, android.R.attr.colorPrimary)
}

/**
 * The two halves of a slider's track, held off the handle on both sides.
 *
 * A `SeekBar` normally clips one drawable over another, which squares off the
 * end the clip falls on and leaves no room for a gap. This draws both halves
 * itself, so each keeps its rounded ends and neither touches the handle.
 *
 * The progress arrives as the drawable's own level, which is what a
 * `ProgressBar` sets on a progress drawable that is not a layer list.
 */
private class ExpressiveTrack(
    private val active: Int,
    private val inactive: Int,
    private val height: Int,
    private val inset: Float,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val radius = height / 2f
        val top = bounds.exactCenterY() - radius
        val bottom = bounds.exactCenterY() + radius
        val at = bounds.left + (bounds.width() * level / MAX_LEVEL)

        paint.color = active
        drawBar(canvas, bounds.left.toFloat(), at - inset, top, bottom, radius)

        paint.color = inactive
        drawBar(canvas, at + inset, bounds.right.toFloat(), top, bottom, radius)
    }

    private fun drawBar(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        if (right - left < MIN_BAR) return

        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    override fun getIntrinsicHeight(): Int = height

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Required by Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val MAX_LEVEL = 10_000f

        /** Below this a bar is a dot rather than a track, so it is left out. */
        const val MIN_BAR = 1f
    }
}
