package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.Context
import android.content.SharedPreferences

import java.io.File

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
 *
 * It sits in device protected storage. The launcher starts at boot, before the
 * first unlock, and credential encrypted storage refuses to open a preference
 * file until then; every tweak used to fail to install for the whole of that
 * launcher's life. Nothing stored here is personal: it is switches and package
 * names the launcher already shows before unlock.
 */
object LauncherSettings {

    /** Preference file name inside the launcher's data directory. */
    const val FILE: String = "pixel_launcher_evolved"

    fun preferences(context: Context): SharedPreferences =
        deviceStorage(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun store(context: Context): SettingsStore = SharedPreferencesStore(preferences(context))

    /**
     * Moves the file v0.0.9 and earlier kept in credential encrypted storage.
     *
     * Only callable once the user has unlocked. Answers whether a file was
     * actually moved, which is when anything already reading the settings is
     * reading a stale copy.
     */
    fun moveFromCredentialStorage(context: Context): Boolean {
        if (!File(context.dataDir, "shared_prefs/$FILE.xml").exists()) return false
        return deviceStorage(context).moveSharedPreferencesFrom(context, FILE)
    }

    private fun deviceStorage(context: Context): Context =
        if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
}
