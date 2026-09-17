package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * The apps on this device that open a website link, and the link that searches
 * the web for a set of words.
 *
 * A link rather than a query, because handing an app a query is not a search:
 * an app given one may do what it likes with it, and Chrome opens its own
 * search box with the words filled in and waits to be asked a second time. A
 * link is opened, once, by every app that takes links.
 *
 * The candidates are whatever answers `ACTION_VIEW` on a web scheme, so the
 * picker offers what the device actually has rather than what this module has
 * heard of. The Google app is left out of that list because it is already the
 * first choice, the one that means leaving the launcher alone.
 */
class WebSearchApps(private val packageManager: PackageManager) {

    /**
     * One row per app, named as the app is named.
     *
     * An activity's own label describes the errand rather than the app, and an
     * app may offer more than one. The app's own name is what a person is
     * choosing between.
     */
    fun installed(): List<WebSearchApp> = runCatching {
        packageManager.queryIntentActivities(PROBE, PackageManager.MATCH_ALL).map { resolved ->
            WebSearchApp(
                packageName = resolved.activityInfo.packageName,
                label = resolved.activityInfo.applicationInfo.loadLabel(packageManager),
            )
        }
    }.getOrDefault(emptyList())
        .filterNot { it.packageName == GOOGLE_APP }
        .distinctBy { it.packageName }
        .sortedBy { it.label.toString().lowercase() }

    /** Null once the chosen app is gone, which is what makes the choice fall back. */
    fun labelOf(packageName: String): CharSequence? =
        installed().firstOrNull { it.packageName == packageName }?.label

    /**
     * The search for [query], opened in [packageName], or null when that app
     * can no longer open a link.
     *
     * The screen that opens it is resolved here rather than remembered, so an
     * app that moves its entry point between versions keeps working. The null
     * matters too: an app uninstalled after it was chosen must leave the
     * launcher's own answer working rather than open nothing.
     */
    fun searchIn(packageName: String, query: CharSequence): Intent? {
        val intent = Intent(Intent.ACTION_VIEW, search(query))
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val opener = runCatching { packageManager.resolveActivity(intent, PackageManager.MATCH_ALL) }
            .getOrNull()
            ?.activityInfo
            ?: return null

        return intent.setComponent(ComponentName(opener.packageName, opener.name))
    }

    /**
     * Google's own results page.
     *
     * The suggestion being opened is Google's, so its results are what the
     * launcher would have shown; the choice made here is which app shows them.
     */
    private fun search(query: CharSequence): Uri = Uri.parse(SEARCH)
        .buildUpon()
        .appendQueryParameter("q", query.toString())
        .build()

    companion object {
        /** The launcher's own answer: already the first choice, so never also one of the others. */
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

        private const val SEARCH = "https://www.google.com/search"

        /**
         * A link with a scheme and nothing else, asked without the narrowing
         * the platform applies to web links by default.
         *
         * Both halves are needed. A real address answers with whoever verified
         * that address, and the ordinary answer is narrowed to the app already
         * holding the browser role — either way the browsers a person could
         * pick between never appear.
         */
        private val PROBE: Intent = Intent(Intent.ACTION_VIEW, Uri.parse("http:"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
    }
}
