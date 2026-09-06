package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

/**
 * Read/write access to the settings the hooked launcher process reads.
 *
 * The settings screen depends on this contract rather than on the framework
 * bridge behind it, so the screen can be exercised without a framework present.
 */
interface SettingsStore : SettingsSource {

    fun put(setting: BoolSetting, value: Boolean)

    fun put(setting: IntSetting, value: Int)
}

/**
 * [SettingsStore] over the preferences the framework shares with the module.
 *
 * These are the same preferences the launcher process reads, so a write here
 * reaches a running launcher without restarting it.
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
