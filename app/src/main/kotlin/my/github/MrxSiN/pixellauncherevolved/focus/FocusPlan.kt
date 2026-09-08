package my.github.MrxSiN.pixellauncherevolved.focus

/**
 * Which home screens to show, given what is on and what is assigned.
 *
 * All of the deciding is here, with no Android in it, because this is the part
 * that has to be right: everything else either reads a value or draws what this
 * returns.
 */
object FocusPlan {

    /**
     * The screens the workspace should hold.
     *
     * With no mode on, that is every screen except the ones set aside for a
     * mode — a page meant for Bedtime has no business on the ordinary home
     * screen. With a mode on, it is that mode's pages and nothing else.
     *
     * @param screens every screen the launcher would otherwise show, in order.
     * @param assignments screens set aside for each mode, by mode id.
     * @param priority mode ids from first choice to last, deciding which one
     * wins when two are on at once.
     * @param active the ids of the modes that are on now.
     */
    fun screens(
        screens: List<Int>,
        assignments: Map<String, Set<Int>>,
        priority: List<String>,
        active: Set<String>,
    ): List<Int> {
        val spokenFor = assignments.values.flatten().toSet()
        val ordinary = screens.filterNot { it in spokenFor }

        val chosen = winner(assignments, priority, active) ?: return ordinary

        // A mode whose pages have all been deleted since it was set up would
        // otherwise leave a launcher with no pages at all, which is not a home
        // screen. The ordinary pages stand in until its own are put back.
        val wanted = assignments.getValue(chosen)
        return screens.filter { it in wanted }.ifEmpty { ordinary }
    }

    /**
     * The mode whose pages win, or null when the home screen is the ordinary one.
     *
     * A mode with no pages of its own is not a winner: it is on, but it has
     * nothing to show, so it does not get to hide anything either. That also
     * means turning on a mode nobody has set up changes nothing, which is what
     * someone who has not set it up would expect.
     */
    fun winner(
        assignments: Map<String, Set<Int>>,
        priority: List<String>,
        active: Set<String>,
    ): String? {
        // Ranked first, then anything active that was never ranked, so a mode
        // added since the order was last set still works. Ties break on the id,
        // so the same two modes always pick the same winner.
        return active
            .filter { assignments[it].orEmpty().isNotEmpty() }
            .minWithOrNull(compareBy({ rank(priority, it) }, { it }))
    }

    /** Where a mode sits in the order, with the unranked behind everyone ranked. */
    private fun rank(priority: List<String>, id: String): Int =
        priority.indexOf(id).takeIf { it >= 0 } ?: priority.size
}
