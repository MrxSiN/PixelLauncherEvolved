package my.github.MrxSiN.pixellauncherevolved.focus

/**
 * The home screen's pages as the launcher would show them with no Mode on.
 *
 * Assigning a page is done by its number, and a number only means something
 * against a list. The list has to be the unfiltered one: while a Mode is on the
 * launcher is being shown a subset, so counting the pages on screen would
 * number them differently depending on when someone opened the settings.
 *
 * The unfiltered list passes through this module already — it is the argument
 * the filter is handed — so it is remembered there rather than read again from
 * the launcher's database.
 */
object FocusPages {

    /** Screen ids in the order the launcher lays them out. Empty until first bound. */
    @Volatile
    var order: List<Int> = emptyList()
        private set

    /** Replaces the catalogue from a complete, unfiltered model snapshot. */
    @Synchronized
    fun remember(screens: List<Int>) {
        order = persistent(screens)
    }

    /**
     * Adds screens delivered by an incremental launcher callback.
     *
     * `bindAddScreens` serves two different jobs: a full model bind gives it
     * every visible screen, while adding an app can give it only newly-created
     * screens. Treating the latter as a complete snapshot used to erase every
     * existing page from this catalogue.
     */
    @Synchronized
    fun include(screens: List<Int>): List<Int> {
        val additions = persistent(screens).filterNot(order.toHashSet()::contains)
        if (additions.isNotEmpty()) order = order + additions
        return order
    }

    /** The screen id shown as page [number], counting from one. */
    fun screenAt(number: Int): Int? = order.getOrNull(number - 1)

    /** The page number a screen is shown at, counting from one, or null. */
    fun numberOf(screen: Int): Int? =
        order.indexOf(screen).takeIf { it >= 0 }?.let { it + 1 }

    /** Launcher-owned negative ids are temporary or synthetic pages. */
    private fun persistent(screens: List<Int>): List<Int> = screens.filter { it >= 0 }.distinct()
}
