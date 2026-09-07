package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting

/**
 * Read/write access to the settings every feature reads.
 *
 * The settings section depends on this contract rather than on the preference
 * file behind it, so what draws the switches never has to know where a value
 * ends up.
 */
interface SettingsStore : SettingsSource {

    fun put(setting: BoolSetting, value: Boolean)
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
}
