package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

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

    /** A stored number outside the setting's range is brought back into it. */
    override fun get(setting: IntSetting): Int =
        runCatching { preferences.getInt(setting.key, setting.default) }
            .getOrDefault(setting.default)
            .coerceIn(setting.range)
}
