package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting

/**
 * [SettingsSource] over a preference file.
 *
 * A stored value of the wrong type is treated as absent rather than as a
 * crash: preferences outlive key renames and type changes.
 */
class SharedPreferencesSettings(
    private val preferences: SharedPreferences,
) : SettingsSource {

    override fun get(setting: BoolSetting): Boolean =
        runCatching { preferences.getBoolean(setting.key, setting.default) }
            .getOrDefault(setting.default)
}
