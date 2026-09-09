package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

/**
 * Read/write access to the settings every feature reads.
 *
 * The settings section depends on this contract rather than on the preference
 * file behind it, so what draws the switches never has to know where a value
 * ends up.
 */
interface SettingsStore : SettingsSource {

    fun put(setting: BoolSetting, value: Boolean)

    fun put(setting: IntSetting, value: Int)
}

/**
 * [SettingsStore] over a preference file.
 *
 * Writes land in the same process that reads them, so a switch takes effect on
 * the next thing that asks rather than on the next launcher start.
 */
class SharedPreferencesStore(
    private val preferences: SharedPreferences,
) : SettingsStore, SettingsSource by SharedPreferencesSettings(preferences) {

    override fun put(setting: BoolSetting, value: Boolean) {
        preferences.edit().putBoolean(setting.key, value).apply()
    }

    override fun put(setting: IntSetting, value: Int) {
        preferences.edit().putInt(setting.key, value.coerceIn(setting.range)).apply()
    }
}
