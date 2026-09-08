package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.content.SharedPreferences

/** An app that opens a website link, as the picker lists it. */
data class WebSearchApp(val packageName: String, val label: CharSequence)

/**
 * Which app opens a tapped Web Search result.
 *
 * Not a switch, so it is not in
 * [my.github.MrxSiN.pixellauncherevolved.catalog.Settings], which is one key
 * per on/off setting. It lives in the same preference file, in the launcher's
 * own data directory, because the launcher is the only process that reads or
 * writes it.
 *
 * An app rather than one of its screens: which screen opens a link is the
 * app's business and it may change between versions, so it is looked up again
 * every time rather than remembered here. Absent means the launcher's own
 * answer is left alone.
 */
interface WebSearchAppStore {

    fun chosen(): String?

    /** Null gives the choice back to the launcher. */
    fun choose(packageName: String?)
}

/** [WebSearchAppStore] over the launcher's own preference file. */
class SharedPreferencesWebSearchAppStore(
    private val preferences: SharedPreferences,
) : WebSearchAppStore {

    override fun chosen(): String? = runCatching { preferences.getString(KEY, null) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    override fun choose(packageName: String?) {
        val editor = preferences.edit()
        if (packageName == null) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, packageName)
        }
        editor.apply()
    }

    private companion object {
        const val KEY = "app_drawer_search_web_app"
    }
}
