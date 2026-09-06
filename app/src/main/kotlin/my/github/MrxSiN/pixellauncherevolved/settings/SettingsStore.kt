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

    /** Registers [listener] for changes made anywhere, and returns a way to stop. */
    fun observe(listener: () -> Unit): AutoCloseable
}

/**
 * [SettingsStore] over the preferences the framework shares with the module.
 *
 * These are the same preferences the launcher process reads, so a write here is
 * visible to a hook as soon as the launcher is restarted.
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

    override fun observe(listener: () -> Unit): AutoCloseable {
        val registered = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        preferences.registerOnSharedPreferenceChangeListener(registered)
        return AutoCloseable {
            preferences.unregisterOnSharedPreferenceChangeListener(registered)
        }
    }
}
