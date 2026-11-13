package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout

import kotlin.math.roundToInt

/**
 * Rows to put in order by dragging. Each row's view tagged [HANDLE] starts a drag: the row lifts
 * and follows the finger, the rows it passes slide out of its way with a tick, and letting go drops
 * it into the gap. [onMove] hears each finished move (from, to). TalkBack offers Move up and Move
 * down on every row instead.
 */
@SuppressLint("ViewConstructor")
internal class DragList(
    context: Context,
    rows: List<View>,
    private val upLabel: CharSequence,
    private val downLabel: CharSequence,
    private val onMove: (from: Int, to: Int) -> Unit,
) : LinearLayout(context) {

    private val items = rows.toMutableList()
    private val gap = context.dpOf(4f)
    private var dragging: View? = null
    private var from = -1
    private var to = -1
    private var downY = 0f
    private val upAction = AccessibilityNodeInfo.AccessibilityAction(View.generateViewId(), upLabel)
    private val downAction = AccessibilityNodeInfo.AccessibilityAction(View.generateViewId(), downLabel)

    init {
        orientation = VERTICAL
        val side = context.dpOf(16f)
        setPadding(side, 0, side, 0)
        clipChildren = false
        clipToPadding = false
        for (row in items) prepare(row)
        lay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepare(row: View) {
        row.findViewWithTag<View>(HANDLE)?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(row, event.rawY)
                MotionEvent.ACTION_MOVE -> move(event.rawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drop()
            }
            true
        }
        row.accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val at = items.indexOf(host)
                if (at > 0) info.addAction(upAction)
                if (at in 0 until items.lastIndex) info.addAction(downAction)
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val at = items.indexOf(host)
                val target = when (action) {
                    upAction.id -> at - 1
                    downAction.id -> at + 1
                    else -> return super.performAccessibilityAction(host, action, args)
                }
                if (at < 0 || target !in items.indices) return false
                commit(at, target)
                host.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                return true
            }
        }
    }

    private fun lay() {
        removeAllViews()
        items.forEachIndexed { index, row ->
            row.translationY = 0f
            addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (index > 0) topMargin = gap })
        }
    }

    private fun begin(row: View, y: Float) {
        if (dragging != null) return
        dragging = row
        from = items.indexOf(row)
        to = from
        downY = y
        parent?.requestDisallowInterceptTouchEvent(true)
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        row.animate().translationZ(context.dpOf(8f).toFloat()).scaleX(1.03f).scaleY(1.03f).setDuration(GlanceMotion.SHORT).setInterpolator(GlanceMotion.MOVE).start()
    }

    private fun move(y: Float) {
        val row = dragging ?: return
        val slot = (row.height + gap).toFloat().takeIf { it > 0f } ?: return
        val dy = (y - downY).coerceIn(-from * slot, (items.lastIndex - from) * slot)
        row.translationY = dy
        val next = (from + (dy / slot).roundToInt()).coerceIn(0, items.lastIndex)
        if (next == to) return
        to = next
        row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        items.forEachIndexed { index, other ->
            if (other === row) return@forEachIndexed
            val shift = when {
                from < to && index in from + 1..to -> -slot
                from > to && index in to until from -> slot
                else -> 0f
            }
            other.animate().translationY(shift).setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.MOVE).start()
        }
    }

    private fun drop() {
        val row = dragging ?: return
        val slot = (row.height + gap).toFloat()
        val start = from
        val end = to
        row.animate().translationY((end - start) * slot).translationZ(0f).scaleX(1f).scaleY(1f)
            .setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.SPRING)
            .withEndAction {
                dragging = null
                if (start != end) commit(start, end) else lay()
            }.start()
        row.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
    }

    private fun commit(start: Int, end: Int) {
        items.add(end, items.removeAt(start))
        for (row in items) row.animate().cancel()
        lay()
        onMove(start, end)
    }

    companion object {
        /** The tag of the view inside a row that starts a drag. */
        const val HANDLE = "drag_handle"
    }
}
