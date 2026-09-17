package my.github.MrxSiN.pixellauncherevolved.feature.pages

/**
 * The arithmetic of putting home screen pages in a new order.
 *
 * The launcher has no list of pages to reorder. A page is a screen id that items
 * name, and the pages are shown in ascending id. So a new order is made by giving
 * each page the id of the position it moves to: the ids stay the same set, and
 * only which items carry which one changes.
 *
 * The first page never moves. It is where At a Glance sits, over rows the
 * launcher leaves empty for it, and a page moved there would have its top row
 * drawn underneath.
 */
object PageOrder {

    /**
     * [order] with the page at [from] moved to [to], never before the first page.
     */
    fun move(order: List<Int>, from: Int, to: Int): List<Int> {
        if (from == to || from < FIXED || from !in order.indices) return order
        val target = to.coerceIn(FIXED, order.lastIndex)
        return order.toMutableList().apply { add(target, removeAt(from)) }
    }

    /**
     * The new id of every page whose id changes, for [wanted] out of [current].
     *
     * @param current the pages as the launcher shows them, in ascending id.
     * @param wanted the same pages in the order they should be shown.
     */
    fun renumbering(current: List<Int>, wanted: List<Int>): Map<Int, Int> {
        require(wanted.sorted() == current.sorted()) { "A new order must hold the same pages" }
        require(wanted.firstOrNull() == current.firstOrNull()) { "The first page cannot move" }

        val ids = current.sorted()
        return wanted.indices
            .filter { wanted[it] != ids[it] }
            .associate { wanted[it] to ids[it] }
    }

    /** Pages at the front that cannot be moved or moved in front of. */
    const val FIXED = 1
}
