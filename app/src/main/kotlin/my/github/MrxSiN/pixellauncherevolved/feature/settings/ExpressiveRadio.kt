package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * A Material 3 radio button whose dot springs in when it is chosen.
 *
 * Only drawn: the row it sits in takes the tap and says what is checked, so the
 * whole row is one target rather than a row with a small control in its corner.
 */
class ExpressiveRadio(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val on = ExpressiveRole.PRIMARY.of(context)
    private val off = ExpressiveRole.ON_SURFACE_VARIANT.of(context)

    /** How far the dot has grown, 0 to 1, overshooting while the spring settles. */
    private var dot = 0f
    private var animator: ValueAnimator? = null

    var checked: Boolean = false
        private set

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setChecked(value: Boolean, animate: Boolean) {
        checked = value
        animator?.cancel()
        val target = if (value) 1f else 0f
        if (!animate) {
            dot = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(dot, target).apply {
            duration = SPRING_MILLIS
            interpolator = ExpressiveMotion.spatialSpring(SPRING_MILLIS)
            addUpdateListener {
                dot = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (SIZE_DP * density).roundToInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val stroke = RING_DP * density
        val color = if (checked) on else off

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = color
        canvas.drawCircle(centerX, centerY, min(width, height) / 2f - stroke / 2f, paint)

        if (dot <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = on
        canvas.drawCircle(centerX, centerY, DOT_DP * density * dot, paint)
    }

    private companion object {
        const val SIZE_DP = 20f
        const val RING_DP = 2f
        const val DOT_DP = 5f
        const val SPRING_MILLIS = 400L
    }
}
