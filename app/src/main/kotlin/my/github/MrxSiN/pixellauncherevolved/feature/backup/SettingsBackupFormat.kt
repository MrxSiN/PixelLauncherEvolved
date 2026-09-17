package my.github.MrxSiN.pixellauncherevolved.feature.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings

/**
 * A [SettingsSnapshot] as a JSON file a person can keep, read and move.
 *
 * ```
 * {
 *   "version": 1,
 *   "blur": true,
 *   "blurStrength": 0.5,
 *   "hideWebSearch": true,
 *   "overviewClearAll": true,
 *   "layoutMode": "taskbarOnly",
 *   ...
 * }
 * ```
 *
 * The names are this file's own rather than the stored keys, so a stored key
 * can be renamed without breaking a backup. A name the file leaves out takes
 * its default, because the file is the whole configuration; a name this version
 * does not know is ignored, so a file from a newer module of the same format
 * version still imports.
 */
object SettingsBackupFormat {

    const val VERSION: Int = 1

    /** The file a switch is written under. Every catalogue switch has one; a test holds that. */
    val SWITCHES: Map<String, BoolSetting> = linkedMapOf(
        "blur" to Settings.HOME_BLUR_WALLPAPER,
        "modesHomeScreens" to Settings.FOCUS_HOME_SCREENS,
        "doubleTapToSleep" to Settings.HOME_DOUBLE_TAP_TO_SLEEP,
        "searchBarOpensAppSearch" to Settings.HOME_SEARCH_OPENS_DRAWER,
        "hideWebSearch" to Settings.APP_DRAWER_SEARCH_HIDE_WEB,
        "hidePlayStore" to Settings.APP_DRAWER_SEARCH_HIDE_PLAY_STORE,
        "hideSearchInApps" to Settings.APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS,
        "overviewBubble" to Settings.OVERVIEW_BUBBLE_BUTTON,
        "overviewSplitScreen" to Settings.OVERVIEW_SPLIT_BUTTON,
        "overviewClearAll" to Settings.OVERVIEW_CLEAR_ALL_IN_ACTIONS,
        "hideScreenshot" to Settings.OVERVIEW_HIDE_SCREENSHOT,
        "hideSelect" to Settings.OVERVIEW_HIDE_SELECT,
        "hideTaskbarAppDrawerButton" to Settings.OVERVIEW_HIDE_TASKBAR_ALL_APPS,
    )

    private val LAYOUT_MODES = mapOf(
        LayoutMode.DEFAULT to "default",
        LayoutMode.OVERVIEW_ONLY to "overviewOnly",
        LayoutMode.TASKBAR_ONLY to "taskbarOnly",
        LayoutMode.FULL_TABLET to "fullTablet",
    )

    private const val KEY_VERSION = "version"
    private const val KEY_BLUR_STRENGTH = "blurStrength"
    private const val KEY_LAYOUT_MODE = "layoutMode"
    private const val KEY_HIDDEN_APPS = "hiddenApps"
    private const val KEY_WEB_SEARCH_APP = "webSearchApp"
    private const val PERCENT = 100.0

    fun write(snapshot: SettingsSnapshot): String = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        for ((name, setting) in SWITCHES) put(name, snapshot.switches[setting] ?: setting.default)
        put(KEY_BLUR_STRENGTH, snapshot.blurStrength / PERCENT)
        put(KEY_LAYOUT_MODE, LAYOUT_MODES.getValue(snapshot.layoutMode))
        put(KEY_HIDDEN_APPS, JSONArray(snapshot.hiddenApps.sorted()))
        put(KEY_WEB_SEARCH_APP, snapshot.webSearchApp ?: JSONObject.NULL)
    }.toString(INDENT)

    /** @throws BackupException when [text] is not a settings file this version can read. */
    fun read(text: String): SettingsSnapshot {
        val json = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, error)
        }
        val version = json.optInt(KEY_VERSION, -1)
        if (version < 1) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (version > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)

        val defaults = SettingsSnapshot.DEFAULTS
        val strength = Settings.HOME_BLUR_STRENGTH
        return SettingsSnapshot(
            switches = SWITCHES.entries.associate { (name, setting) -> setting to json.optBoolean(name, setting.default) },
            blurStrength = if (json.has(KEY_BLUR_STRENGTH)) {
                (json.optDouble(KEY_BLUR_STRENGTH, strength.default / PERCENT) * PERCENT).roundToInt().coerceIn(strength.range)
            } else {
                defaults.blurStrength
            },
            layoutMode = LAYOUT_MODES.entries.firstOrNull { it.value == json.optString(KEY_LAYOUT_MODE) }?.key
                ?: defaults.layoutMode,
            hiddenApps = json.optJSONArray(KEY_HIDDEN_APPS)
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
                ?.toSet()
                ?: defaults.hiddenApps,
            webSearchApp = json.optString(KEY_WEB_SEARCH_APP).takeIf { !json.isNull(KEY_WEB_SEARCH_APP) && it.isNotBlank() },
        )
    }

    private const val INDENT = 2
}

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION }
}
