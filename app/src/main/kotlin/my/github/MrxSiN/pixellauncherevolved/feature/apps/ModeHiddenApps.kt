package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.focus.FocusPlan
import my.github.MrxSiN.pixellauncherevolved.focus.SharedPreferencesFocusStore

/**
 * Which Mode's hidden apps apply now, if any.
 *
 * Picked by the same rule as Mode pages ([FocusPlan.winner]): of the Modes that
 * are on and hide at least one app, the one that came on last. Worked out only
 * when the Modes are read or a Mode's list is saved, never per app; the drawer
 * filter reads [modeId] alone. Unknown Modes (no read yet, or a failed one)
 * leave it null, so only the everywhere list applies: hiding is not a security
 * boundary, and showing an app is the safe mistake.
 */
object ModeHiddenApps {

    @Volatile
    var modeId: String? = null
        private set

    /**
     * Search groups the winning Mode hides on top of the everywhere switches,
     * as those switches' keys ([SEARCH_KEY]); picked by the same rule among
     * the Modes that hide any.
     */
    @Volatile
    var searchHidden: Set<String> = emptySet()
        private set

    @Volatile
    private var active: Map<String, Long> = emptyMap()

    /** Told on any thread when [modeId] changes, so the drawer can be rebuilt. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    fun update(preferences: SharedPreferences, active: Map<String, Long>) {
        this.active = active
        recompute(preferences)
    }

    /** After a Mode's list is saved: it may now hide something, or nothing. */
    fun recompute(preferences: SharedPreferences) {
        val all = preferences.all
        val priority = SharedPreferencesFocusStore(preferences).priority()
        val search = FocusPlan.winner(candidates(all, SEARCH_KEY), priority, active)
        searchHidden = search?.let { (all[SEARCH_KEY + it] as? String).orEmpty().split(',').filterTo(HashSet()) { k -> k.isNotBlank() } }
            ?: emptySet()

        val next = FocusPlan.winner(candidates(all, SharedPreferencesHiddenAppsStore.MODE_KEY), priority, active)
        if (next == modeId) return
        modeId = next
        onChanged?.invoke()
    }

    private fun candidates(all: Map<String, *>, prefix: String): Set<String> =
        all.mapNotNullTo(HashSet()) { (key, value) ->
            key.removePrefix(prefix).takeIf { it != key && (value as? String).orEmpty().isNotBlank() }
        }

    /** `focus_search_hide_<modeId>`: comma-separated keys of the search switches that Mode turns off. */
    const val SEARCH_KEY = "focus_search_hide_"
}
