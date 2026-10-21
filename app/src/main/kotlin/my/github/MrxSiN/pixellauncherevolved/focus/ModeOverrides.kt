package my.github.MrxSiN.pixellauncherevolved.focus

import android.content.SharedPreferences

/**
 * What the Modes that are on change besides their pages: hidden apps, search
 * groups and the icon pack.
 *
 * Each kind is picked by the same rule as Mode pages ([FocusPlan.winner]): of
 * the Modes that are on and set that kind, the one that came on last. Worked
 * out only when the Modes are read or a Mode's settings are saved, never per
 * app or per frame; the hooks read the fields alone. Unknown Modes (no read
 * yet, or a failed one) leave every field at the everywhere setting: hiding is
 * not a security boundary, and showing an app is the safe mistake.
 */
object ModeOverrides {

    /** The Mode whose hidden apps ([HIDDEN_APPS_KEY]) apply, if any. */
    @Volatile
    var modeId: String? = null
        private set

    /**
     * Search groups the winning Mode hides on top of the everywhere switches,
     * as those switches' keys ([SEARCH_KEY]).
     */
    @Volatile
    var searchHidden: Set<String> = emptySet()
        private set

    /** The winning Mode's icon pack ([ICON_PACK_KEY]): a package, [SYSTEM_ICONS], or null to follow the everywhere choice. */
    @Volatile
    var iconPack: String? = null
        private set

    @Volatile
    private var active: Map<String, Long> = emptyMap()

    /** Told on any thread when [modeId] changes, so the drawer can be rebuilt. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    /** Told on any thread when [iconPack] changes, so icons can be redrawn. */
    @Volatile
    var onIconsChanged: (() -> Unit)? = null

    fun update(preferences: SharedPreferences, active: Map<String, Long>) {
        this.active = active
        recompute(preferences)
    }

    /** After a Mode's settings are saved: it may now change something, or nothing. */
    fun recompute(preferences: SharedPreferences) {
        val all = preferences.all
        val priority = SharedPreferencesFocusStore(preferences).priority()
        fun winner(prefix: String) = FocusPlan.winner(candidates(all, prefix), priority, active)

        searchHidden = winner(SEARCH_KEY)?.let { (all[SEARCH_KEY + it] as? String).orEmpty().split(',').filterTo(HashSet()) { k -> k.isNotBlank() } }
            ?: emptySet()

        val pack = winner(ICON_PACK_KEY)?.let { all[ICON_PACK_KEY + it] as? String }
        if (pack != iconPack) {
            iconPack = pack
            onIconsChanged?.invoke()
        }

        val next = winner(HIDDEN_APPS_KEY)
        if (next == modeId) return
        modeId = next
        onChanged?.invoke()
    }

    private fun candidates(all: Map<String, *>, prefix: String): Set<String> =
        all.mapNotNullTo(HashSet()) { (key, value) ->
            key.removePrefix(prefix).takeIf { it != key && (value as? String).orEmpty().isNotBlank() }
        }

    /** `focus_hidden_apps_<modeId>`: comma-separated packages that Mode hides. */
    const val HIDDEN_APPS_KEY = "focus_hidden_apps_"

    /** `focus_search_hide_<modeId>`: comma-separated keys of the search switches that Mode turns off. */
    const val SEARCH_KEY = "focus_search_hide_"

    /** `focus_icon_pack_<modeId>`: the icon pack's package, or [SYSTEM_ICONS]. */
    const val ICON_PACK_KEY = "focus_icon_pack_"

    /** A Mode's icon pack value meaning the system's own icons. */
    const val SYSTEM_ICONS = ":system"
}
