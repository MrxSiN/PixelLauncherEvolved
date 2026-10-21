package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides

/**
 * Which apps the app drawer leaves out.
 *
 * Not a switch, so it is not in
 * [my.github.MrxSiN.pixellauncherevolved.catalog.Settings], which is one key
 * per on/off setting. It lives in the same preference file, in the launcher's
 * own data directory, because the launcher is the only process that reads or
 * writes it.
 *
 * Apps are named by package rather than by component. An app with two entries
 * in the drawer is still one app to the person hiding it, and a package that
 * exists in both a personal and a work profile is the same app in both.
 */
interface HiddenAppsStore {

    fun hidden(): Set<String>

    fun hide(packageNames: Set<String>)
}

/** [HiddenAppsStore] over the launcher's own preference file. */
class SharedPreferencesHiddenAppsStore(
    private val preferences: SharedPreferences,
    private val key: String = KEY,
) : HiddenAppsStore {

    /**
     * The last stored text and what it parsed to.
     *
     * The drawer asks once per app on every rebuild, so the text is parsed only
     * when it differs from last time; the preference file hands back the same
     * string until it is written, which makes the usual check an identity one.
     */
    @Volatile
    private var parsed = Parsed("", emptySet())

    override fun hidden(): Set<String> {
        val raw = runCatching { preferences.getString(key, "") }.getOrNull().orEmpty()
        val last = parsed
        if (raw === last.raw || raw == last.raw) return last.names

        val names = raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
        parsed = Parsed(raw, names)
        return names
    }

    override fun hide(packageNames: Set<String>) {
        val editor = preferences.edit()
        if (packageNames.isEmpty()) {
            editor.remove(key)
        } else {
            editor.putString(key, packageNames.sorted().joinToString(SEPARATOR))
        }
        editor.apply()
    }

    private class Parsed(val raw: String, val names: Set<String>)

    companion object {
        private const val KEY = "app_drawer_hidden_apps"
        private const val SEPARATOR = ","

        /** Apps a Mode hides on top of [KEY] while it is the Mode that wins ([ModeOverrides]). */
        const val MODE_KEY = ModeOverrides.HIDDEN_APPS_KEY

        fun forMode(preferences: SharedPreferences, modeId: String) =
            SharedPreferencesHiddenAppsStore(preferences, MODE_KEY + modeId)
    }
}

/**
 * What the drawer and search leave out: the apps hidden everywhere, plus the
 * ones the winning Mode hides ([ModeOverrides]). One answer for every surface,
 * so the drawer and search never disagree.
 *
 * Asked once per app on every rebuild: both parts are cached sets handed back
 * by identity until written, so the union is built only when one changes.
 */
class EffectiveHiddenApps(private val preferences: SharedPreferences) : HiddenAppsStore {

    private val global = SharedPreferencesHiddenAppsStore(preferences)

    @Volatile
    private var mode: SharedPreferencesHiddenAppsStore? = null

    @Volatile
    private var union = Union(null, emptySet(), emptySet(), emptySet())

    override fun hidden(): Set<String> {
        val id = ModeOverrides.modeId
        val store = mode.takeIf { union.modeId == id }
            ?: id?.let { SharedPreferencesHiddenAppsStore.forMode(preferences, it) }.also { mode = it }
        val everywhere = global.hidden()
        val forMode = store?.hidden() ?: emptySet()
        val last = union
        if (last.modeId == id && everywhere === last.everywhere && forMode === last.forMode) return last.names

        val names = if (forMode.isEmpty()) everywhere else everywhere + forMode
        union = Union(id, everywhere, forMode, names)
        return names
    }

    /** Writes the everywhere list; a Mode's own is written through [SharedPreferencesHiddenAppsStore.forMode]. */
    override fun hide(packageNames: Set<String>) = global.hide(packageNames)

    private class Union(val modeId: String?, val everywhere: Set<String>, val forMode: Set<String>, val names: Set<String>)
}
