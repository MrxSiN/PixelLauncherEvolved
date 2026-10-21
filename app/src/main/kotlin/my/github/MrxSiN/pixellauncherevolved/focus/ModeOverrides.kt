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

    /** Packages the winning Mode puts at the top of the drawer ([DRAWER_APPS_KEY]), under [drawerTitle]. */
    @Volatile
    var drawerApps: Set<String> = emptySet()
        private set

    /** That Mode's name, as last read from Settings, or null when no Mode puts apps there. */
    @Volatile
    var drawerTitle: String? = null
        private set

    /** The winning Mode's wallpaper blur in percent ([BLUR_KEY]), 0 for none, or null to follow the everywhere setting. */
    @Volatile
    var blur: Int? = null
        private set

    @Volatile
    private var active: Map<String, Long> = emptyMap()

    /** Told on any thread when [modeId] or the drawer section changes, so the drawer can be rebuilt. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    /** Told on any thread when [blur] changes, so the wallpaper can be blurred again. */
    @Volatile
    var onBlurChanged: (() -> Unit)? = null

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

        val blurNow = winner(BLUR_KEY)?.let { (all[BLUR_KEY + it] as? String)?.toIntOrNull()?.coerceIn(0, 100) }
        if (blurNow != blur) {
            blur = blurNow
            onBlurChanged?.invoke()
        }

        val drawerMode = winner(DRAWER_APPS_KEY)
        val apps = drawerMode?.let { (all[DRAWER_APPS_KEY + it] as? String).orEmpty().split(',').filterTo(HashSet()) { p -> p.isNotBlank() } }
            ?: emptySet()
        val title = drawerMode?.let { SharedPreferencesFocusStore(preferences).nameOf(it) }
        val drawerChanged = apps != drawerApps || title != drawerTitle
        drawerApps = apps
        drawerTitle = title

        val next = winner(HIDDEN_APPS_KEY)
        if (next == modeId && !drawerChanged) return
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

    /** `focus_drawer_apps_<modeId>`: comma-separated packages that Mode shows at the top of All apps. */
    const val DRAWER_APPS_KEY = "focus_drawer_apps_"

    /** `focus_blur_<modeId>`: the wallpaper blur in percent while that Mode wins, as text; 0 for none. */
    const val BLUR_KEY = "focus_blur_"

    /** A Mode's icon pack value meaning the system's own icons. */
    const val SYSTEM_ICONS = ":system"
}
