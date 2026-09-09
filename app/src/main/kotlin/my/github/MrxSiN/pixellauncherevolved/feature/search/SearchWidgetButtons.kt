package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/**
 * The buttons the home screen search bar carries, told apart from the bar.
 *
 * The bar is an app widget, so everything inside it belongs to the Google app's
 * own `RemoteViews`. None of those views carry an id — read off a Pixel 8 Pro,
 * only the widget's root has one — so they cannot be asked for by name, and
 * their content descriptions are whatever language the phone is set to. What
 * does hold is their shape: the search field is the full width of the bar, and
 * every button is a narrow clickable view sitting inside it.
 *
 * Two tweaks ask about the same views for opposite reasons — one wants the taps
 * that are not on a button, the other wants a press that is — so the rule for
 * telling them apart is written once, here.
 */
internal object SearchWidgetButtons {

    /** Every narrow clickable view inside [widget], in the order laid out. */
    fun of(widget: ViewGroup): List<View> = widget.descendants()
        .filter { it.isClickable && it.width < widget.width * FULL_WIDTH }
        .toList()

    /** Whether a point in screen coordinates lands on one of them. */
    fun anyContains(widget: ViewGroup, x: Int, y: Int): Boolean =
        of(widget).any { it.containsOnScreen(x, y) }

    /** Whether one button contains a point given in screen coordinates. */
    fun View.containsOnScreen(x: Int, y: Int): Boolean {
        val bounds = Rect()
        return getGlobalVisibleRect(bounds) && bounds.contains(x, y)
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    /** Anything narrower than this much of the bar is a button, not the field. */
    private const val FULL_WIDTH = 0.8f
}
