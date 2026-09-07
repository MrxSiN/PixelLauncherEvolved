package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Carries settings written by earlier versions into the launcher's own store.
 *
 * Up to now the settings app wrote through the framework's shared preferences
 * and the launcher only read them. The settings now live beside the launcher,
 * so without this a user who had already chosen their tweaks would find them
 * all back at their defaults after updating.
 *
 * It runs once. The marker is written whether or not anything was copied, so a
 * later change made in Home settings is never overwritten by a stale value the
 * old store still holds.
 */
class SettingsMigration(private val logger: Logger) {

    /**
     * @param target the launcher-owned preferences, the new home of every setting
     * @param legacy the framework's shared preferences, or null when unreachable
     */
    fun apply(target: SharedPreferences, legacy: SharedPreferences?) {
        if (target.getBoolean(MARKER, false)) return

        val editor = target.edit().putBoolean(MARKER, true)
        var copied = 0

        for (entry in FeatureCatalog.entries) {
            val setting = entry.setting
            if (setting !is BoolSetting) continue
            if (legacy == null || !legacy.contains(setting.key)) continue

            editor.putBoolean(setting.key, legacy.getBoolean(setting.key, setting.default))
            copied++
        }

        editor.apply()
        logger.info("Settings moved into the launcher's own store; $copied carried over")
    }

    private companion object {
        /** Its presence, not its value, is what says the move already happened. */
        const val MARKER = "migrated_from_framework_preferences"
    }
}
