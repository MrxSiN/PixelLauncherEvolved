package my.github.MrxSiN.pixellauncherevolved.safemode

import android.content.SharedPreferences

/**
 * What Safe Mode remembers: recent crashes, and what it switched off.
 *
 * Kept in the settings file, beside the switches it turns off.
 */
interface SafeModeStore {

    fun crashes(): List<Long>

    /** Written before the process dies, so it has to land synchronously. */
    fun recordCrash(time: Long)

    fun clearCrashes()

    /** The setting keys Safe Mode switched off and has not been answered about. */
    fun disabled(): List<String>

    fun setDisabled(keys: List<String>)
}

class SharedPreferencesSafeModeStore(private val preferences: SharedPreferences) : SafeModeStore {

    override fun crashes(): List<Long> = read(CRASHES).mapNotNull(String::toLongOrNull)

    override fun recordCrash(time: Long) {
        val kept = CrashLoop.recent(crashes(), time) + time
        // commit, not apply: the process is about to end, and apply writes later.
        preferences.edit().putString(CRASHES, kept.joinToString(SEPARATOR)).commit()
    }

    override fun clearCrashes() {
        preferences.edit().remove(CRASHES).commit()
    }

    override fun disabled(): List<String> = read(DISABLED)

    override fun setDisabled(keys: List<String>) {
        preferences.edit().apply {
            if (keys.isEmpty()) remove(DISABLED) else putString(DISABLED, keys.joinToString(SEPARATOR))
        }.commit()
    }

    private fun read(key: String): List<String> =
        preferences.getString(key, null).orEmpty().split(SEPARATOR).filter(String::isNotBlank)

    private companion object {
        const val CRASHES = "safe_mode_crashes"
        const val DISABLED = "safe_mode_disabled"
        const val SEPARATOR = ","
    }
}
