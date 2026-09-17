package my.github.MrxSiN.pixellauncherevolved.feature.pages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ScrollView

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * A grid of tiles arranged by touching and holding one, then dragging it.
 *
 * The tile lifts on a spring as it is picked up and follows the finger. As it
 * crosses another slot the tiles in between spring over to make room, and on
 * release it springs into the slot it was left over. The first [PageOrder.FIXED]
 * tiles stay where they are. Every move is also offered to accessibility
 * services as "move earlier" and "move later", since dragging is not something
 * everyone can do.
 *
 * @param onMoved told of each move, as the positions a tile left and took.
 */
@SuppressLint("ViewConstructor")
internal class PageReorderGrid(
    context: Context,
    private val labels: MoveLabels,
    private val onMoved: (from: Int, to: Int) -> Unit,
) : ViewGroup(context) {

    /** What the accessibility actions are called, read from this module's resources. */
    class MoveLabels(val earlier: CharSequence, val later: CharSequence)

    private val tiles = mutableListOf<View>()
    private val density = resources.displayMetrics.density
    private val columnGap = dp(COLUMN_GAP_DP)
    private val rowGap = dp(ROW_GAP_DP)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var cellWidth = 0
    private var cellHeight = 0

    private var pending: View? = null
    private var dragged: View? = null
    private var downX = 0f
    private var downY = 0f
    private var grabX = 0f
    private var grabY = 0f
    private val pickUp = Runnable { pending?.let(::lift) }

    fun setTiles(views: List<View>) {
        removeAllViews()
        tiles.clear()
        views.forEach { tile ->
            tiles += tile
            addView(tile)
            tile.accessibilityDelegate = MoveActions(tile)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellWidth = ((width - paddingLeft - paddingRight - columnGap * (COLUMNS - 1)) / COLUMNS).coerceAtLeast(0)
        cellHeight = 0
        for (tile in tiles) {
            tile.measure(
                MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            cellHeight = max(cellHeight, tile.measuredHeight)
        }
        val rows = ceil(tiles.size / COLUMNS.toFloat()).toInt()
        setMeasuredDimension(width, paddingTop + paddingBottom + rows * cellHeight + max(0, rows - 1) * rowGap)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = placeTiles()

    /** Puts every tile in its slot now, so a move can animate from where each tile was. */
    private fun placeTiles() {
        tiles.forEachIndexed { index, tile ->
            val slot = slot(index)
            tile.layout(slot.left, slot.top, slot.right, slot.top + tile.measuredHeight)
        }
    }

    private fun slot(index: Int) = Rect().apply {
        left = paddingLeft + (index % COLUMNS) * (cellWidth + columnGap)
        top = paddingTop + (index / COLUMNS) * (cellHeight + rowGap)
        right = left + cellWidth
        bottom = top + cellHeight
    }

    /** The slot under a point, never one of the fixed ones. */
    private fun slotAt(x: Float, y: Float): Int {
        val column = ((x - paddingLeft) / (cellWidth + columnGap)).toInt().coerceIn(0, COLUMNS - 1)
        val row = ((y - paddingTop) / (cellHeight + rowGap)).toInt().coerceAtLeast(0)
        return (row * COLUMNS + column).coerceIn(PageOrder.FIXED, tiles.lastIndex)
    }

    private fun tileAt(x: Float, y: Float): View? = tiles.indices
        .firstOrNull { slot(it).contains(x.roundToInt(), y.roundToInt()) }
        ?.let(tiles::get)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tile = tileAt(event.x, event.y)
                if (tile == null || tiles.indexOf(tile) < PageOrder.FIXED) return false
                pending = tile
                downX = event.x
                downY = event.y
                postDelayed(pickUp, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val tile = dragged
                if (tile == null) {
                    // Moving before the hold ends is a scroll, and the scroll view takes it.
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) cancelPickUp()
                } else {
                    follow(tile, event.x, event.y)
                    val from = tiles.indexOf(tile)
                    val to = slotAt(event.x, event.y)
                    if (to != from) move(from, to)
                    scrollNearEdge(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPickUp()
                drop()
            }
        }
        return true
    }

    private fun lift(tile: View) {
        pending = null
        dragged = tile
        parent?.requestDisallowInterceptTouchEvent(true)
        grabX = downX - tile.left
        grabY = downY - tile.top
        tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        tile.animate()
            .scaleX(LIFTED_SCALE)
            .scaleY(LIFTED_SCALE)
            .translationZ(LIFTED_ELEVATION_DP * density)
            .setInterpolator(ExpressiveMotion.spatialSpring(SPRING_MILLIS))
            .setDuration(SPRING_MILLIS)
            .start()
    }

    private fun follow(tile: View, x: Float, y: Float) {
        val slot = slot(tiles.indexOf(tile))
        tile.translationX = x - grabX - slot.left
        tile.translationY = y - grabY - slot.top
    }

    /**
     * Moves the tile at [from] to [to], and springs every tile that changes slot
     * from where it was to where it now belongs.
     */
    private fun move(from: Int, to: Int) {
        val before = tiles.associateWith { it.left to it.top }
        tiles.add(to, tiles.removeAt(from))
        placeTiles()
        for (tile in tiles) {
            if (tile === dragged) continue
            val (left, top) = before.getValue(tile)
            if (left == tile.left && top == tile.top) continue
            tile.translationX += (left - tile.left)
            tile.translationY += (top - tile.top)
            tile.animate()
                .translationX(0f)
                .translationY(0f)
                .setInterpolator(ExpressiveMotion.spatialSpring(SPRING_MILLIS))
                .setDuration(SPRING_MILLIS)
                .start()
        }
        dragged?.let { tile ->
            // Its slot moved under the finger, so its offset follows.
            val (left, top) = before.getValue(tile)
            tile.translationX += (left - tile.left)
            tile.translationY += (top - tile.top)
            tile.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
        onMoved(from, to)
    }

    private fun drop() {
        val tile = dragged ?: return
        dragged = null
        tile.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .translationZ(0f)
            .setInterpolator(ExpressiveMotion.spatialSpring(SPRING_MILLIS))
            .setDuration(SPRING_MILLIS)
            .start()
    }

    private fun cancelPickUp() {
        removeCallbacks(pickUp)
        pending = null
    }

    /** Scrolls the screen while a tile is held near its top or bottom edge. */
    private fun scrollNearEdge(event: MotionEvent) {
        val scroller = generateSequence(parent) { it.parent }.filterIsInstance<ScrollView>().firstOrNull() ?: return
        val frame = Rect().also { scroller.getGlobalVisibleRect(it) }
        val edge = EDGE_DP * density
        val step = (SCROLL_STEP_DP * density).roundToInt()
        when {
            event.rawY < frame.top + edge -> scroller.scrollBy(0, -step)
            event.rawY > frame.bottom - edge -> scroller.scrollBy(0, step)
        }
    }

    /** "Move earlier" and "Move later", for whoever cannot drag. */
    private inner class MoveActions(private val tile: View) : AccessibilityDelegate() {

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val index = tiles.indexOf(tile)
            if (index < PageOrder.FIXED) return
            if (index > PageOrder.FIXED) info.addAction(AccessibilityNodeInfo.AccessibilityAction(EARLIER, labels.earlier))
            if (index < tiles.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction(LATER, labels.later))
        }

        override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
            val index = tiles.indexOf(tile)
            val target = when (action) {
                EARLIER -> index - 1
                LATER -> index + 1
                else -> return super.performAccessibilityAction(host, action, args)
            }
            if (target < PageOrder.FIXED || target > tiles.lastIndex) return false
            move(index, target)
            return true
        }
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private companion object {
        const val COLUMNS = 3
        const val COLUMN_GAP_DP = 12f
        const val ROW_GAP_DP = 20f
        const val LIFTED_SCALE = 1.06f
        const val LIFTED_ELEVATION_DP = 8f
        const val SPRING_MILLIS = 450L
        const val EDGE_DP = 72f
        const val SCROLL_STEP_DP = 12f

        /** Custom action ids. This module's own resource ids, so none can be the platform's. */
        val EARLIER = R.id.ple_action_move_earlier
        val LATER = R.id.ple_action_move_later
    }
}
