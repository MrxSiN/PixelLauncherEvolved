package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Where this module's settings live.
 *
 * They are kept in the launcher's own data directory rather than in the
 * framework's shared preferences, because the settings screen is now a section
 * of the launcher's own Home settings and the framework only ever hands the
 * hooked process a read-only view of its store. One file, owned by the process
 * that both reads and writes it, keeps a change visible on the next frame and
 * needs no bridge at all.
 *
 * The file is this module's own rather than the launcher's `com.android.launcher3.prefs`,
 * so nothing here reaches the launcher's backup set or its own keys.
 */
object LauncherSettings {

    /** Preference file name inside the launcher's data directory. */
    const val FILE: String = "pixel_launcher_evolved"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun store(context: Context): SettingsStore = SharedPreferencesStore(preferences(context))
}
