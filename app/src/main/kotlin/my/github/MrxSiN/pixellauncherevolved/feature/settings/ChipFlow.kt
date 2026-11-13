package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.view.ViewGroup

/** Lays its children out in rows, wrapping to a new row when one is full: a group of chips. */
internal class ChipFlow(context: Context, private val gap: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var row = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += row + gap
                row = 0
            }
            x += child.measuredWidth + gap
            row = maxOf(row, child.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(y + row + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var row = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += row + gap
                row = 0
            }
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) r - l - paddingRight - x - child.measuredWidth else paddingLeft + x
            child.layout(left, paddingTop + y, left + child.measuredWidth, paddingTop + y + child.measuredHeight)
            x += child.measuredWidth + gap
            row = maxOf(row, child.measuredHeight)
        }
    }
}
