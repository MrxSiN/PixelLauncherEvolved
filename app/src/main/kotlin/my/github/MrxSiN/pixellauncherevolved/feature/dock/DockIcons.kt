package my.github.MrxSiN.pixellauncherevolved.feature.dock

/**
 * The dock's numbers: which counts can be offered, what each one asks of the
 * launcher's grid, and where a displaced item can go.
 *
 * The launcher keeps one database slot per dock icon
 * (`InvariantDeviceProfile.numDatabaseHotseatIcons`, the grid's own count), and
 * deletes every pinned dock app whose slot is past that capacity when it next
 * loads (`LoaderCursor`: "position out of bounds"). The same holds for a Home
 * screen item below the grid's last row. So a count above the grid's raises
 * the capacity with it, a hidden dock adds a row, and anything a smaller dock
 * or a shown dock leaves out is either moved to the Home screen first or kept
 * inside a capacity that is not lowered ([capacity]'s `kept`).
 *
 * Removing or disabling the module drops both back to the grid's with no
 * chance to move anything; README says so.
 */
object DockIcons {

    /** The fewest icons offered. */
    const val FEWEST = 1

    // ponytail: hard ceiling for imported or hand-edited values; settings also
    // offers only what fits the width (fits()).
    const val MOST = 8

    /** The counts to offer on a grid whose own count is [stock] and whose dock fits [fits] icons. */
    fun choices(stock: Int, fits: Int): IntRange = FEWEST..maxOf(stock, minOf(fits, MOST))

    /** The count the dock shows for [chosen] (0 is System) on a grid whose own count is [stock]. */
    fun shown(chosen: Int, stock: Int): Int = if (chosen in FEWEST..MOST) chosen else stock

    /**
     * The database capacity the launcher needs: never below the grid's own, the
     * count shown, or [kept], the slots still holding apps a smaller count hid.
     */
    fun capacity(chosen: Int, stock: Int, kept: Int = 0): Int = maxOf(stock, shown(chosen, stock), kept)

    /** How many cells of at least [cell] px fit across [width] px with [gap] px between them. */
    fun fits(width: Int, cell: Float, gap: Int): Int = if (cell <= 0f) 0 else ((width + gap) / (cell + gap)).toInt()

    /** The width of each of [count] cells across [width] px with [gap] px between them. */
    fun cellWidth(width: Int, gap: Int, count: Int): Int = if (count <= 0) width else (width - gap * (count - 1)) / count

    /** What to store for [count]: the grid's own count is stored as System. */
    fun stored(count: Int, stock: Int): Int = if (count == stock) 0 else count

    /** Home screen rows while the dock is [hidden]: its icon row becomes one more. */
    fun rows(stockRows: Int, hidden: Boolean): Int = if (hidden) stockRows + 1 else stockRows

    /**
     * The first top-left cell, row by row, where a [spanX] by [spanY] item fits
     * in a [columns] by [rows] grid without touching an [occupied] cell.
     */
    fun firstFree(columns: Int, rows: Int, spanX: Int, spanY: Int, occupied: (Int, Int) -> Boolean): IntArray? {
        for (y in 0..rows - spanY) for (x in 0..columns - spanX) {
            var free = true
            for (dy in 0 until spanY) for (dx in 0 until spanX) if (occupied(x + dx, y + dy)) free = false
            if (free) return intArrayOf(x, y)
        }
        return null
    }
}
