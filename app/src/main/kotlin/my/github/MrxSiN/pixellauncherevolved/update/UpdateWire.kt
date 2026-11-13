package my.github.MrxSiN.pixellauncherevolved.update

import android.os.Bundle

/**
 * What the launcher and this module's app say to each other about updates, and the pure rules
 * behind it (tested without either).
 *
 * The newest release is read from GitHub's `releases/latest` page without following its redirect:
 * the `Location` it answers with ends in `/tag/<tag>`. One bodiless `HEAD`; no JSON, no API limit.
 */
object UpdateWire {

    const val METHOD_LATEST = "latest"

    /** The project's own releases; the redirect from here names the newest one. */
    const val LATEST_URL = "https://github.com/MrxSiN/PixelLauncherEvolved/releases/latest"

    private const val LOCATION = "location"
    private const val ERROR = "error"
    private const val TAG_PATH = "/tag/"

    fun authority(modulePackage: String): String = "$modulePackage.update"

    fun found(location: String): Bundle = Bundle(1).apply { putString(LOCATION, location) }

    fun failed(reason: String): Bundle = Bundle(1).apply { putString(ERROR, reason) }

    /** The release page the answer points at, or null. */
    fun locationOf(answer: Bundle?): String? = answer?.getString(LOCATION)

    /** Why the check failed, or null. */
    fun errorOf(answer: Bundle?): String? = answer?.getString(ERROR)

    /** The tag a release page names (`.../releases/tag/v0.1.8` is `v0.1.8`), or null for any other address. */
    fun tagOf(location: String?): String? {
        val at = location?.lastIndexOf(TAG_PATH) ?: return null
        if (at < 0) return null
        return location.substring(at + TAG_PATH.length).substringBefore('?').substringBefore('#').trimEnd('/').takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [tag] (`v1.2.3` or `1.2.3`) is a newer release than [current]. Numbers are compared
     * part by part; a missing part counts as 0, and anything after a `-` or `+` is ignored. Anything
     * unreadable is never newer, so a strange tag offers nothing.
     */
    fun isNewer(tag: String, current: String): Boolean {
        val latest = parts(tag) ?: return false
        val mine = parts(current) ?: return false
        for (i in 0 until maxOf(latest.size, mine.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = mine.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
