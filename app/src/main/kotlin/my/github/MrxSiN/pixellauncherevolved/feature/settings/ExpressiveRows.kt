package my.github.MrxSiN.pixellauncherevolved.feature.settings

/**
 * Where one row sits in the run of rows it is drawn with.
 *
 * Android 17 settings draws a run of rows as one card: the first and last rows
 * carry the card's rounded ends, the rows between them are squared off, and a
 * run of one is rounded on both ends.
 */
enum class RowPlacement { SINGLE, TOP, MIDDLE, BOTTOM }

/**
 * Which placement a row has, given where it falls in its run.
 *
 * Kept as a function of two numbers, with no view and no preference in sight,
 * so the rule the cards are shaped by can be read and tested on its own.
 */
object ExpressiveGrouping {

    fun placementOf(index: Int, size: Int): RowPlacement = when {
        size <= 1 -> RowPlacement.SINGLE
        index == 0 -> RowPlacement.TOP
        index == size - 1 -> RowPlacement.BOTTOM
        else -> RowPlacement.MIDDLE
    }

    /**
     * One screen's rows split into the runs a card is drawn around.
     *
     * A heading starts a new card, which is what makes a settings screen read
     * as a few labelled cards rather than as one long one. Everything else
     * continues the run it lands in, whether it is a switch, a value, or a row
     * that opens another page.
     */
    fun <T> runs(rows: List<T>, isHeader: (T) -> Boolean): List<List<T>> {
        val runs = mutableListOf<List<T>>()
        var run = mutableListOf<T>()

        for (row in rows) {
            if (isHeader(row)) {
                if (run.isNotEmpty()) runs += run
                run = mutableListOf()
                continue
            }
            run += row
        }

        if (run.isNotEmpty()) runs += run

        return runs
    }
}

/**
 * Where a bound row sits, read off the screen it belongs to.
 *
 * A row is bound long after it is built, by an adapter that hands out recycled
 * views and says nothing about the rows above and below. The hierarchy does
 * know: a row carries the group it was added to, and that group holds its rows
 * in the order they are drawn.
 *
 * Only the rows of this module's own pages are placed, recognised by the key of
 * the page they were added to. Two kinds of row are deliberately left out.
 *
 * The launcher's own rows keep the look the launcher gives them: restyling
 * those would mean this module owning the look of a screen it does not ship,
 * and re-checking it against every launcher update.
 *
 * So does this module's own section in Home settings, which is a few rows among
 * the launcher's on a screen the launcher lays out. A card drawn there would be
 * this module announcing itself in the middle of someone else's list; inside
 * its own pages, where every row is this module's, there is nothing to clash
 * with.
 */
class ExpressiveRowPlacements(
    private val api: PreferenceApi,
    private val pageKeyPrefix: String,
) {

    fun placementOf(row: Any): RowPlacement? {
        if (!api.hasRowPlacement || api.isCategory(row)) return null

        // A row sits on the page itself or under a heading on it.
        val parent = api.parentOf(row) ?: return null
        val page = if (api.isCategory(parent)) api.parentOf(parent) else parent
        if (page == null || api.keyOf(page)?.startsWith(pageKeyPrefix) != true) return null

        val siblings = api.childrenOf(parent).filter { api.isVisible(it) }
        val run = ExpressiveGrouping.runs(siblings, api::isCategory)
            .firstOrNull { group -> group.any { it === row } }
            ?: return null

        return ExpressiveGrouping.placementOf(run.indexOfFirst { it === row }, run.size)
    }
}
