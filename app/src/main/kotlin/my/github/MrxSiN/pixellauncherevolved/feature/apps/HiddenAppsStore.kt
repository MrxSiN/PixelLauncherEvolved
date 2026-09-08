package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.SharedPreferences

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
) : HiddenAppsStore {

    override fun hidden(): Set<String> = runCatching { preferences.getString(KEY, "") }
        .getOrNull()
        .orEmpty()
        .split(SEPARATOR)
        .filter { it.isNotBlank() }
        .toSet()

    override fun hide(packageNames: Set<String>) {
        val editor = preferences.edit()
        if (packageNames.isEmpty()) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, packageNames.sorted().joinToString(SEPARATOR))
        }
        editor.apply()
    }

    private companion object {
        const val KEY = "app_drawer_hidden_apps"
        const val SEPARATOR = ","
    }
}
