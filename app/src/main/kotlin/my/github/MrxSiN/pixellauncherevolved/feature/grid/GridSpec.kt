package my.github.MrxSiN.pixellauncherevolved.feature.grid

/**
 * The numbers behind Grid & size: what each stored value means, which values a
 * screen can take, and the database a custom grid keeps its layout in.
 *
 * Kept free of Android and of the launcher, so every rule here is unit tested.
 * Stored values are logical (a column count, a size step), never pixels, so a
 * backup means the same on another phone and is only re-fitted there.
 */
object GridSpec {

    /** Stored 0: the launcher's own value. */
    const val SYSTEM = 0

    /** The fewest columns or rows offered; Google's own smallest phone grid is 3 × 3. */
    const val FEWEST = 3

    // ponytail: hard ceiling for imported or hand-edited values; settings only
    // offers what fits() measures on this screen.
    const val MOST = 10

    /** Icon size steps, as percentages of the launcher's own size. Stored as the percentage. */
    val ICON_SIZES = intArrayOf(85, 100, 115, 130)

    const val ICON_DEFAULT = 100

    /** Spacing steps, stored -1, 0 and 1: Compact, Default and Relaxed. */
    const val COMPACT = -1
    const val RELAXED = 1

    /**
     * How much a spacing step scales the launcher's own gap between cells.
     * [spread] then takes the change from the grid's outer padding, so a
     * Relaxed gap only grows as far as that padding lets it.
     */
    fun spacingFactor(step: Int): Float = when {
        step <= COMPACT -> 0.25f
        step >= RELAXED -> 3f
        else -> 1f
    }

    /** The share of the outer padding a Relaxed gap may take; the rest keeps the grid off the screen's edges. */
    const val PADDING_KEPT = 0.3f

    /**
     * The start padding, end padding and gap of one responsive spec once its
     * gap is scaled by [factor], all in the spec's own unit.
     *
     * The cells keep their size: what the [cells] - 1 gaps gain is taken from
     * the two paddings, in proportion to them, and what they lose is given
     * back to them. So the grid spreads out toward the edges or draws in to
     * the middle, which is what moves the icons apart; scaling the gap alone
     * shrinks the cells and barely moves them.
     */
    fun spread(start: Float, end: Float, gap: Float, cells: Int, factor: Float): FloatArray {
        val padding = start + end
        if (cells < 2 || factor == 1f || gap <= 0f) return floatArrayOf(start, end, gap)
        val change = minOf((cells - 1) * gap * (factor - 1f), padding * (1f - PADDING_KEPT))
        val startShare = if (padding > 0f) start / padding else 0.5f
        return floatArrayOf(start - change * startShare, end - change * (1f - startShare), gap + change / (cells - 1))
    }

    /** The scale an icon size step asks for. */
    fun iconScale(percent: Int): Float = nearestIconSize(percent) / 100f

    /** [percent] snapped to the closest offered icon size; an unknown value reads as the launcher's own. */
    fun nearestIconSize(percent: Int): Int {
        if (percent <= 0) return ICON_DEFAULT
        var best = ICON_DEFAULT
        for (size in ICON_SIZES) if (kotlin.math.abs(size - percent) < kotlin.math.abs(best - percent)) best = size
        return best
    }

    /** The count a stored value asks for: the launcher's [stock] for System or anything out of range. */
    fun count(stored: Int, stock: Int): Int = if (stored in FEWEST..MOST) stored else stock

    /** What to store for [count]: the launcher's own count is stored as System. */
    fun stored(count: Int, stock: Int): Int = if (count == stock) SYSTEM else count

    /**
     * How many cells of at least [minCell] px fit across [space] px with [gap]
     * px between them, never fewer than [FEWEST] or more than [MOST].
     */
    fun fits(space: Int, minCell: Float, gap: Float): Int {
        if (minCell <= 0f || space <= 0) return FEWEST
        return ((space + gap) / (minCell + gap)).toInt().coerceIn(FEWEST, MOST)
    }

    /** The counts to offer: [FEWEST] up to what [fits], always including the launcher's own [stock]. */
    fun choices(stock: Int, fits: Int): IntRange = FEWEST..maxOf(stock, fits)

    /** [stored] as this screen can take it: kept when offered, System otherwise. */
    fun fitted(stored: Int, stock: Int, fits: Int): Int =
        if (stored == SYSTEM || count(stored, stock) in choices(stock, fits)) stored(count(stored, stock), stock) else SYSTEM

    /**
     * The database a custom grid keeps its layout in.
     *
     * Google names its grids' databases `launcher_<columns>_by_<rows>.db`; this
     * module's carry `ple_` so they can never be one of Google's, and still start
     * `launcher` and end `.db`, which is what the launcher's backup agent copies.
     */
    fun dbFile(columns: Int, rows: Int): String = "$DB_PREFIX${columns}_by_$rows.db"

    /** Whether [name] is one of this module's grid databases. */
    fun isOwnDb(name: String?): Boolean = name != null && name.startsWith(DB_PREFIX) && name.endsWith(".db")

    const val DB_PREFIX = "launcher_ple_"

    /** One Home screen item as far as fitting it into a grid goes. */
    class Item(val title: CharSequence, val minSpanX: Int, val minSpanY: Int)

    /**
     * The items a [columns] by [rows] grid cannot hold at any size.
     *
     * The launcher's own migration (`GridSizeMigrationLogic.solveGridPlacement`)
     * shrinks a widget to its smallest span and drops one whose smallest span is
     * still wider or taller than the grid. Such a grid is refused instead.
     */
    fun misfits(items: List<Item>, columns: Int, rows: Int): List<Item> =
        items.filter { it.minSpanX > columns || it.minSpanY > rows }

    /**
     * What a grid migration keeps from the destination grid's memory and what
     * it places afresh, as the launcher's own `getItemsToBeAdded` and
     * `getItemsToBeRemoved` answer.
     *
     * A grid's database remembers the Home screen it had when that grid was
     * last used. An item remembered on the page it is on now keeps its
     * remembered cell and span, so going to a smaller grid and back restores
     * it as it was. An item remembered on another page is forgotten and placed
     * from the layout being left ([placeOnPage]), so no item ever changes
     * page. Items are matched as the launcher matches them ([E]'s `equals`).
     *
     * Answers the [src] items to add, then the [dest] items to remove.
     */
    fun <E : Any> remembered(src: List<E>, dest: List<E>, screenOf: (E) -> Int): Pair<List<E>, List<E>> {
        val pool = HashMap<E, MutableList<E>>()
        for (entry in dest) pool.getOrPut(entry) { ArrayList(1) } += entry
        val added = ArrayList<E>()
        for (entry in src) {
            val candidates = pool[entry]
            val index = candidates?.indexOfFirst { screenOf(it) == screenOf(entry) } ?: -1
            if (index >= 0) candidates!!.removeAt(index) else added += entry
        }
        return added to pool.values.flatten()
    }

    /** An item's page, cell and span before a migration, and the smallest span it can take. */
    class Cell(val screen: Int, val x: Int, val y: Int, val spanX: Int, val spanY: Int, val minSpanX: Int, val minSpanY: Int)

    /** Where a migration puts an item on the page being filled. */
    class Placement(val x: Int, val y: Int, val spanX: Int, val spanY: Int) {
        companion object {
            /** No span of the item fits the grid; the launcher's own migration drops it too. */
            val DROPPED = Placement(-1, -1, 0, 0)
        }
    }

    /**
     * Fills page [screen] of a [columns] by [rows] grid during a grid change,
     * the cells in [taken] (`[x][y]`, updated) already being used.
     *
     * The launcher's own migration packs every item into the first free cell
     * of the first page with room, so a bigger grid pulls icons from later
     * pages onto earlier ones. Here an item stays on its page: at its own cell
     * and span, else as much of its span as fits there, else the first free
     * cell of that page. What its page has no room for moves on to the pages after
     * the last one items came from, never onto another item's page.
     *
     * Answers, per item, its [Placement] on this page, [Placement.DROPPED], or
     * null for an item left to a later page.
     */
    fun placeOnPage(screen: Int, columns: Int, rows: Int, taken: Array<BooleanArray>, items: List<Cell>): Array<Placement?> {
        val result = arrayOfNulls<Placement>(items.size)
        for (index in items.indices) {
            val item = items[index]
            if (item.minSpanX > columns || item.minSpanY > rows) result[index] = Placement.DROPPED
        }
        // Every item where it was first, so none takes the cell of one placed after it.
        for (index in items.indices) {
            val item = items[index]
            if (result[index] != null || item.screen != screen) continue
            result[index] = claim(taken, item.x, item.y, item.spanX, item.spanY)
                ?: claim(taken, item.x, item.y, minOf(item.spanX, columns - item.x).coerceAtLeast(item.minSpanX), minOf(item.spanY, rows - item.y).coerceAtLeast(item.minSpanY))
                ?: claim(taken, item.x, item.y, item.minSpanX, item.minSpanY)
        }
        for (index in items.indices) if (result[index] == null && items[index].screen == screen) result[index] = firstFree(taken, items[index])
        // Past the last page items came from, the overflow of every page fills new ones.
        if (items.none { it.screen >= screen }) {
            for (index in items.indices) if (result[index] == null) result[index] = firstFree(taken, items[index])
        }
        return result
    }

    private fun firstFree(taken: Array<BooleanArray>, item: Cell): Placement? {
        val columns = taken.size
        val rows = taken.firstOrNull()?.size ?: 0
        val spanX = item.spanX.coerceAtMost(columns).coerceAtLeast(item.minSpanX)
        val spanY = item.spanY.coerceAtMost(rows).coerceAtLeast(item.minSpanY)
        for ((width, height) in arrayOf(spanX to spanY, item.minSpanX to item.minSpanY)) {
            for (y in 0 until rows) for (x in 0 until columns) claim(taken, x, y, width, height)?.let { return it }
        }
        return null
    }

    /** Takes the [spanX] by [spanY] cells at [x], [y] when they lie in the grid and are free. */
    private fun claim(taken: Array<BooleanArray>, x: Int, y: Int, spanX: Int, spanY: Int): Placement? {
        val columns = taken.size
        val rows = taken.firstOrNull()?.size ?: 0
        if (x < 0 || y < 0 || spanX < 1 || spanY < 1 || x + spanX > columns || y + spanY > rows) return null
        for (cx in x until x + spanX) for (cy in y until y + spanY) if (taken[cx][cy]) return null
        for (cx in x until x + spanX) for (cy in y until y + spanY) taken[cx][cy] = true
        return Placement(x, y, spanX, spanY)
    }
}
