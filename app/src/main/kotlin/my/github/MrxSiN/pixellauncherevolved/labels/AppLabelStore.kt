package my.github.MrxSiN.pixellauncherevolved.labels

import android.content.SharedPreferences

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.icons.ComponentHash

/**
 * One app's label, chosen by hand.
 *
 * Presentation only. The app is named by component and profile, the same
 * identity the launcher launches and stores it by, and [name] is never used to
 * find, launch, store or uninstall anything.
 *
 * @property name what the icon reads instead of the app's own label, or null
 * for the app's own label.
 */
data class AppLabel(
    val packageName: String,
    val className: String,
    val userId: Int,
    val name: String?,
    val hideHome: Boolean = false,
    val hideDrawer: Boolean = false,
) {
    /** Whether this entry changes anything; one that does not is not stored. */
    val isCustom: Boolean get() = name != null || hideHome || hideDrawer
}

/**
 * The labels as stored text, and as the arrays a bind looks up.
 *
 * Stored as JSON because a label is free text: any character a person can type,
 * separators included, survives it without an escaping scheme of this module's
 * own. Kept free of Android so it can be read and tested on a desk.
 *
 * ```
 * {"version":1,"apps":[{"user":0,"package":"com.example","class":"com.example.Main","name":"Video","hideHome":false,"hideDrawer":true}]}
 * ```
 *
 * Entries are written sorted, so the same set is always the same text.
 */
object AppLabelFormat {

    const val VERSION = 1

    /** Long enough for any real name; short enough that no label can be used to stall a layout. */
    const val MAX_CODE_POINTS = 64

    private const val KEY_VERSION = "version"
    private const val KEY_APPS = "apps"
    private const val KEY_USER = "user"
    private const val KEY_PACKAGE = "package"
    private const val KEY_CLASS = "class"
    private const val KEY_NAME = "name"
    private const val KEY_HIDE_HOME = "hideHome"
    private const val KEY_HIDE_DRAWER = "hideDrawer"

    /**
     * What a typed name is stored as, or null for the app's own label.
     *
     * Only what would break the icon is changed: line breaks and other control
     * characters become spaces, because a label is laid out on the icon's own
     * lines, and the ends are trimmed as the launcher trims app labels. Any
     * other character, emoji and right-to-left text included, is kept. Past
     * [MAX_CODE_POINTS] the rest is cut, on a code point, never inside one.
     */
    fun normalize(text: CharSequence?): String? {
        if (text == null) return null
        val cleaned = buildString(text.length) {
            for (char in text) append(if (Character.isISOControl(char)) ' ' else char)
        }.trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.codePointCount(0, cleaned.length) <= MAX_CODE_POINTS) return cleaned
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_CODE_POINTS)).trimEnd()
    }

    fun write(labels: Collection<AppLabel>): String = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_APPS, JSONArray().apply {
            for (label in labels.filter(AppLabel::isCustom).sortedWith(ORDER)) {
                put(JSONObject().apply {
                    put(KEY_USER, label.userId)
                    put(KEY_PACKAGE, label.packageName)
                    put(KEY_CLASS, label.className)
                    put(KEY_NAME, label.name ?: JSONObject.NULL)
                    put(KEY_HIDE_HOME, label.hideHome)
                    put(KEY_HIDE_DRAWER, label.hideDrawer)
                })
            }
        })
    }.toString()

    /**
     * Entries this version cannot read are skipped and the rest still load; a
     * file from a newer format reads as nothing rather than as something half
     * understood.
     */
    fun read(text: String): List<AppLabel> {
        if (text.isBlank()) return emptyList()
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return emptyList()
        }
        if (json.optInt(KEY_VERSION, -1) !in 1..VERSION) return emptyList()

        val apps = json.optJSONArray(KEY_APPS) ?: return emptyList()
        val read = ArrayList<AppLabel>(apps.length())
        for (at in 0 until apps.length()) {
            val entry = apps.optJSONObject(at) ?: continue
            val packageName = entry.optString(KEY_PACKAGE)
            val className = entry.optString(KEY_CLASS)
            if (packageName.isBlank() || className.isBlank() || !entry.has(KEY_USER)) continue

            val label = AppLabel(
                packageName = packageName,
                className = className,
                userId = entry.optInt(KEY_USER, 0),
                name = if (entry.isNull(KEY_NAME)) null else normalize(entry.optString(KEY_NAME)),
                hideHome = entry.optBoolean(KEY_HIDE_HOME, false),
                hideDrawer = entry.optBoolean(KEY_HIDE_DRAWER, false),
            )
            if (label.isCustom) read += label
        }
        return read
    }

    /**
     * The arrays a bind looks up.
     *
     * Two entries whose keys collide are both dropped: the app's own label on
     * both is better than one app wearing the other's name.
     */
    fun compile(labels: Collection<AppLabel>): AppLabels {
        val byKey = HashMap<Long, AppLabel?>()
        for (label in labels) {
            if (!label.isCustom) continue
            val key = ComponentHash.ofUser(label.packageName, label.className, label.userId)
            val earlier = byKey[key]
            byKey[key] = when {
                !byKey.containsKey(key) -> label
                earlier != null && earlier.sameApp(label) -> label
                else -> null
            }
        }
        val kept = byKey.entries.filter { it.value != null }.sortedBy { it.key }
        if (kept.isEmpty()) return AppLabels.NONE

        return AppLabels(
            LongArray(kept.size) { kept[it].key },
            Array(kept.size) { kept[it].value!!.name },
            IntArray(kept.size) { flagsOf(kept[it].value!!) },
            Array(kept.size) { kept[it].value!!.packageName },
            Array(kept.size) { kept[it].value!!.className },
            IntArray(kept.size) { kept[it].value!!.userId },
        )
    }

    private fun AppLabel.sameApp(other: AppLabel) =
        packageName == other.packageName && className == other.className && userId == other.userId

    private fun flagsOf(label: AppLabel): Int =
        (if (label.hideHome) AppLabels.HIDE_HOME else 0) or (if (label.hideDrawer) AppLabels.HIDE_DRAWER else 0)

    private val ORDER = compareBy<AppLabel>({ it.userId }, { it.packageName }, { it.className })
}

/**
 * Which apps wear a label of their own.
 *
 * Not a switch, so it is not in
 * [my.github.MrxSiN.pixellauncherevolved.catalog.Settings]. It lives in the same
 * preference file, in the launcher's own data directory: Home settings writes it
 * and the launcher reads it, and both are the launcher's process.
 */
class AppLabelStore(private val preferences: SharedPreferences) {

    fun labels(): List<AppLabel> = AppLabelFormat.read(raw())

    /** Replaces every label and makes the launcher's lookup match, in one step. */
    fun replace(labels: Collection<AppLabel>) {
        val editor = preferences.edit()
        if (labels.none(AppLabel::isCustom)) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, AppLabelFormat.write(labels))
        }
        editor.apply()
        publish()
    }

    /** One app's entry replaced, or removed when [label] changes nothing. */
    fun put(label: AppLabel) = replace(
        labels().filterNot { it.packageName == label.packageName && it.className == label.className && it.userId == label.userId } +
            listOfNotNull(label.takeIf(AppLabel::isCustom)),
    )

    fun clear() = replace(emptyList())

    /**
     * Compiles the stored text into [AppLabels.current].
     *
     * Parsed only when the text differs from what was last compiled; the
     * preference file hands back the same string until it is written.
     */
    fun publish(): AppLabels {
        AppLabels.setHomeOnly(runCatching { preferences.getBoolean(Settings.APP_LABELS_HOME_ONLY.key, false) }.getOrDefault(false))
        fun hides(setting: BoolSetting, surface: Int) =
            if (runCatching { preferences.getBoolean(setting.key, false) }.getOrDefault(false)) surface else 0
        val surfaces = hides(Settings.LABELS_HIDE_HOME, AppLabels.HIDE_HOME) or
            hides(Settings.LABELS_HIDE_DRAWER, AppLabels.HIDE_DRAWER) or
            hides(Settings.LABELS_HIDE_FOLDERS, AppLabels.HIDE_FOLDER)
        AppLabels.setHiddenSurfaces(surfaces)
        if (surfaces != 0) onFirstLabels?.invoke()
        val text = raw()
        val last = compiled
        if (last != null && (text === last.first || text == last.first)) return last.second
        val labels = AppLabelFormat.compile(AppLabelFormat.read(text))
        compiled = text to labels
        AppLabels.publish(labels)
        if (!labels.isEmpty) onFirstLabels?.invoke()
        return labels
    }

    private fun raw(): String = runCatching { preferences.getString(KEY, "") }.getOrNull().orEmpty()

    companion object {
        private const val KEY = "home_app_labels"

        @Volatile
        private var compiled: Pair<String, AppLabels>? = null

        /**
         * Run once, by whichever publish first finds a label, so the launcher's
         * hooks go in only when a label exists to apply.
         */
        @Volatile
        var onFirstLabels: (() -> Unit)? = null
    }
}
