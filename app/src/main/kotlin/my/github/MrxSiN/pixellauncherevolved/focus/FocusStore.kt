package my.github.MrxSiN.pixellauncherevolved.focus

import android.content.SharedPreferences

/**
 * Which pages belong to which mode, and which mode wins.
 *
 * Kept apart from [my.github.MrxSiN.pixellauncherevolved.catalog.Settings],
 * which is one key per switch. This is neither a switch nor one key: it is a
 * set of pages per mode and an order over the modes, and bending the catalog
 * into holding it would make every other setting harder to read.
 *
 * It lives in the same preference file, in the launcher's own data directory,
 * because the launcher is the only process that reads or writes it.
 */
interface FocusStore {

    /** Pages set aside for each mode, by mode id. Modes with none are absent. */
    fun assignments(): Map<String, Set<Int>>

    /** Mode ids from first choice to last. Modes not listed rank behind those that are. */
    fun priority(): List<String>

    /**
     * Gives [screens] to [modeId], replacing whatever it had.
     *
     * An empty set gives the pages back to the ordinary home screen. Assigning
     * puts the mode at the front of the order, because the mode someone just
     * set up is the one they are thinking about.
     */
    fun assign(modeId: String, screens: Set<Int>)

    /** Replaces the order outright. */
    fun reorder(modeIds: List<String>)

    /**
     * Pages a deleted Mode had, by its old id. They are back on the ordinary home screen;
     * this only remembers them, so they can be given to a Mode again ([keepOrphan]).
     */
    fun orphans(): Map<String, Orphan>

    /** Keeps [orphan] under [modeId], or forgets it when it has no pages left. */
    fun keepOrphan(modeId: String, orphan: Orphan)

    /** The name [modeId] had when it was last seen with pages, to name them once it is gone. */
    fun nameOf(modeId: String): String?

    fun rememberName(modeId: String, name: String)
}

/** The pages a deleted Mode had, and its name if it was ever seen. */
data class Orphan(val name: String?, val screens: Set<Int>)

/**
 * Follows pages that were given new ids, so each Mode keeps the pages it had.
 *
 * Assignments are stored by screen id, and putting the home screen's pages in a
 * new order gives the moved pages new ids. The Mode order is left as it was.
 */
fun FocusStore.renumber(mapping: Map<Int, Int>) {
    if (mapping.isEmpty()) return
    val order = priority()
    for ((mode, owned) in assignments()) {
        val renumbered = owned.map { mapping[it] ?: it }.toSet()
        if (renumbered != owned) assign(mode, renumbered)
    }
    for ((mode, orphan) in orphans()) {
        val renumbered = orphan.screens.map { mapping[it] ?: it }.toSet()
        if (renumbered != orphan.screens) keepOrphan(mode, orphan.copy(screens = renumbered))
    }
    reorder(order)
}

/** Takes pages the launcher removed out of what every deleted Mode left behind. */
fun FocusStore.forgetOrphanPages(gone: Set<Int>) {
    for ((mode, orphan) in orphans()) {
        val left = orphan.screens - gone
        if (left != orphan.screens) keepOrphan(mode, orphan.copy(screens = left))
    }
}

/**
 * Gives back the pages of every Mode not in [existing], and drops it from the order.
 *
 * Pages are kept by a Mode's id, and a Mode deleted in Settings takes its id
 * with it — one made again under the same name gets a new one. Left behind, its
 * pages stayed set aside for a Mode that can never come on: hidden from the
 * ordinary home screen and offered to no other Mode. A read that failed looks
 * like no Modes at all, so it forgets nothing.
 *
 * What a deleted Mode had is kept as an [Orphan] with the name it last had, so its
 * pages can be given to the Mode made in its place. Each read also notes the name
 * of every Mode with pages, which is the only time a name is to be had.
 */
fun FocusStore.forgetModesMissingFrom(snapshot: FocusSnapshot) {
    if (!snapshot.isReadable) return
    val assignments = assignments()
    for (mode in snapshot.modes) {
        if (mode.id in assignments && nameOf(mode.id) != mode.name) rememberName(mode.id, mode.name)
    }
    val existing = snapshot.modes.mapTo(HashSet(), FocusMode::id)
    val gone = assignments.keys - existing
    val order = priority()
    val kept = order.filter { it in existing }
    if (gone.isEmpty() && kept == order) return
    gone.forEach {
        keepOrphan(it, Orphan(nameOf(it), assignments.getValue(it)))
        assign(it, emptySet())
    }
    reorder(kept)
}

/** [FocusStore] over the launcher's own preference file. */
class SharedPreferencesFocusStore(
    private val preferences: SharedPreferences,
) : FocusStore {

    override fun assignments(): Map<String, Set<Int>> = preferences.all.keys
        .filter { it.startsWith(PAGES) }
        .associate { key -> key.removePrefix(PAGES) to screens(key) }
        .filterValues { it.isNotEmpty() }

    override fun priority(): List<String> = read(PRIORITY).filter { it.isNotBlank() }

    override fun assign(modeId: String, screens: Set<Int>) {
        val editor = preferences.edit()
        val key = PAGES + modeId

        if (screens.isEmpty()) editor.remove(key) else editor.putString(key, screens.sorted().join())
        editor.putString(PRIORITY, (listOf(modeId) + priority().filterNot { it == modeId }).join())
        editor.apply()
    }

    override fun reorder(modeIds: List<String>) {
        preferences.edit().putString(PRIORITY, modeIds.join()).apply()
    }

    override fun orphans(): Map<String, Orphan> = preferences.all
        .filter { (key, value) -> key.startsWith(ORPHAN) && value is String }
        .map { (key, value) ->
            val text = value as String
            key.removePrefix(ORPHAN) to Orphan(
                name = text.substringAfter(NAME_SEPARATOR, "").ifEmpty { null },
                screens = text.substringBefore(NAME_SEPARATOR).split(SEPARATOR).mapNotNull { it.trim().toIntOrNull() }.toSet(),
            )
        }
        .filter { it.second.screens.isNotEmpty() }
        .toMap()

    /** Stored as `screens|name`: the screens first, because a name can hold any character. */
    override fun keepOrphan(modeId: String, orphan: Orphan) {
        val editor = preferences.edit().remove(NAME + modeId)
        if (orphan.screens.isEmpty()) {
            editor.remove(ORPHAN + modeId)
        } else {
            editor.putString(ORPHAN + modeId, orphan.screens.sorted().join() + NAME_SEPARATOR + orphan.name.orEmpty())
        }
        editor.apply()
    }

    override fun nameOf(modeId: String): String? = preferences.getString(NAME + modeId, null)

    override fun rememberName(modeId: String, name: String) {
        preferences.edit().putString(NAME + modeId, name).apply()
    }

    private fun screens(key: String): Set<Int> =
        read(key).mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun read(key: String): List<String> =
        preferences.getString(key, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    private fun <T> List<T>.join(): String = joinToString(SEPARATOR)

    private companion object {
        const val PAGES = "focus_pages_"
        const val PRIORITY = "focus_priority"
        const val ORPHAN = "focus_orphan_"
        const val NAME = "focus_name_"
        const val NAME_SEPARATOR = "|"
        const val SEPARATOR = ","
    }
}
