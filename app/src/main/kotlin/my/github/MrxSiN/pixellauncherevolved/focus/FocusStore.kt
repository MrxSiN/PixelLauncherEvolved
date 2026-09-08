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

    private fun screens(key: String): Set<Int> =
        read(key).mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun read(key: String): List<String> =
        preferences.getString(key, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    private fun <T> List<T>.join(): String = joinToString(SEPARATOR)

    private companion object {
        const val PAGES = "focus_pages_"
        const val PRIORITY = "focus_priority"
        const val SEPARATOR = ","
    }
}
