package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.TypedValue

import my.github.MrxSiN.pixellauncherevolved.feature.overview.LauncherResources

/**
 * The tick drawn on an app icon while apps are being chosen.
 *
 * It sits on the bottom-right corner of the icon, where a notification dot sits
 * on the opposite side, so the two never argue over the same pixels. Every
 * icon carries a mark while the choosing lasts: an empty ring on the ones not
 * taken, which is what says the drawer is asking a question, and a filled tick
 * on the ones that are.
 *
 * The colours are the launcher's own, so the mark follows the system theme
 * rather than introducing one.
 */
class AppIconCheckmark(context: Context) {

    private val launcherResources = LauncherResources(context)
    private val density = context.resources.displayMetrics

    private val primary = launcherResources.color(PRIMARY, DEFAULT_PRIMARY)
    private val onPrimary = launcherResources.color(ON_PRIMARY, Color.WHITE)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primary
    }
    // Not the theme's own colour: the ring is drawn on this module's dark disc
    // rather than on the launcher's surface, and a theme whose primary is pale
    // has a dark colour to go on it — which on a dark disc is nothing at all.
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        alpha = RING_ALPHA
        strokeWidth = pixels(RING_WIDTH_DP)
    }
    private val ringShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = RING_SHADOW_ALPHA
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = onPrimary
        strokeWidth = pixels(TICK_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val iconBounds = Rect()
    private val path = Path()

    /** Draws onto the icon's own canvas, after the icon itself. */
    fun draw(canvas: Canvas, icon: Rect, chosen: Boolean) {
        val radius = radiusFor(icon)
        val centreX = icon.right - radius
        val centreY = icon.bottom - radius

        if (!chosen) {
            // A ring alone disappears over a pale icon, so it is drawn on a
            // disc of its own rather than straight onto whatever is behind it.
            canvas.drawCircle(centreX, centreY, radius, ringShadow)
            canvas.drawCircle(centreX, centreY, radius - ring.strokeWidth / 2f, ring)
            return
        }

        canvas.drawCircle(centreX, centreY, radius, fill)

        val arm = radius * TICK_SCALE
        path.reset()
        path.moveTo(centreX - arm, centreY)
        path.lineTo(centreX - arm * TICK_ELBOW, centreY + arm * TICK_ELBOW)
        path.lineTo(centreX + arm, centreY - arm * TICK_RISE)
        canvas.drawPath(path, tick)
    }

    /** Kept in proportion to the icon, because icon size follows the grid. */
    private fun radiusFor(icon: Rect): Float =
        (icon.width() * RADIUS_FRACTION).coerceAtLeast(pixels(MIN_RADIUS_DP))

    fun bounds(): Rect = iconBounds

    private fun pixels(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, density)

    private companion object {
        const val PRIMARY = "materialColorPrimary"
        const val ON_PRIMARY = "materialColorOnPrimary"
        const val DEFAULT_PRIMARY = 0xFF6750A4.toInt()

        const val RADIUS_FRACTION = 0.17f
        const val MIN_RADIUS_DP = 9f
        const val RING_WIDTH_DP = 2f
        const val TICK_WIDTH_DP = 2f
        const val RING_ALPHA = 235
        const val RING_SHADOW_ALPHA = 110

        /** The tick inside the disc: an arm, its elbow, and how far the long arm rises. */
        const val TICK_SCALE = 0.52f
        const val TICK_ELBOW = 0.45f
        const val TICK_RISE = 0.75f
    }
}
