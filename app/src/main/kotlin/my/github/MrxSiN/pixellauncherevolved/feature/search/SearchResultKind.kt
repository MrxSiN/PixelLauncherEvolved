package my.github.MrxSiN.pixellauncherevolved.feature.search

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * One app drawer search result, reduced to what deciding its fate needs.
 *
 * The launcher receives `android.app.search.SearchTarget` objects, which this
 * module cannot name at compile time. Reading the three answers that identify a
 * result and passing them on as plain values keeps the decision below free of
 * reflection, and testable without a launcher.
 */
data class SearchResult(
    val resultType: Int,
    val layoutType: String,
    val packageName: String,
)

/**
 * A part of the results a person can switch off.
 *
 * Each kind recognises its own results, so adding one to this module is adding
 * a constant here, a preference in [Settings] and a row in the catalogue.
 * Nothing else has to learn what the parts are.
 *
 * The results arrive already grouped: a heading, then the rows under it. Both
 * are matched, because a heading left behind by its rows is a title over
 * nothing.
 */
enum class SearchResultKind(val setting: BoolSetting) {

    /** Google's suggestions for what was typed, under a **Web Search** heading. */
    WEB_SEARCH(Settings.APP_DRAWER_SEARCH_HIDE_WEB) {
        override fun matches(result: SearchResult): Boolean =
            result.resultType has ResultTypes.WEB_SUGGEST
    },

    /** Apps to install matching what was typed, under a **Play Store** heading. */
    PLAY_STORE(Settings.APP_DRAWER_SEARCH_HIDE_PLAY_STORE) {
        override fun matches(result: SearchResult): Boolean =
            result.resultType has ResultTypes.PLAY ||
                (result.isSectionHeading && result.packageName == PLAY_STORE_PACKAGE)
    },

    /**
     * The **Search in Apps** row: hand this query to Google, YouTube, Maps and
     * the rest.
     *
     * Its heading carries no package, so it is told from the other headings by
     * the type the launcher gives a result that fulfils nothing on its own.
     */
    SEARCH_IN_APPS(Settings.APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS) {
        override fun matches(result: SearchResult): Boolean =
            result.resultType has ResultTypes.SEARCH_IN_APPS ||
                (result.isHeadingRow && result.resultType has ResultTypes.NO_FULFILLMENT)
    };

    abstract fun matches(result: SearchResult): Boolean

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

/**
 * Everything the app drawer's search has been told to leave out.
 *
 * Two questions with one answer: whole groups a person switched off, and the
 * individual apps they hid from the drawer. An app hidden from the drawer that
 * came back the moment its name was typed would not be hidden at all.
 */
class HiddenSearchResults(
    private val kinds: Set<SearchResultKind>,
    private val apps: Set<String>,
) {

    val isEmpty: Boolean get() = kinds.isEmpty() && apps.isEmpty()

    fun hides(result: SearchResult): Boolean =
        kinds.any { it.matches(result) } || (result.isApp && result.packageName in apps)
}

/** The kinds currently switched off, read fresh so a change applies at once. */
fun hiddenSearchResults(settings: SettingsSource): Set<SearchResultKind> =
    SearchResultKind.entries.filterTo(LinkedHashSet()) { settings[it.setting] }

/**
 * The result-type bits the launcher's search provider sets, as observed on
 * Android 17. They are flags, so a result can carry more than one.
 */
private object ResultTypes {
    /** A Google web suggestion. */
    const val WEB_SUGGEST = 1 shl 17

    /** A Play Store listing. */
    const val PLAY = 1 shl 8

    /** An offer to run this query inside one app. */
    const val SEARCH_IN_APPS = 1 shl 9

    /** An app on the device. */
    const val APPLICATION = 1

    /** A result that opens nothing itself: a heading, or a blank separator. */
    const val NO_FULFILLMENT = 1 shl 18

    /** The heading above one app's group of results. */
    const val SECTION_HEADING = 1 shl 23
}

private const val HEADING_LAYOUT = "text_header_row"

private val SearchResult.isHeadingRow: Boolean get() = layoutType == HEADING_LAYOUT

private val SearchResult.isApp: Boolean get() = resultType has ResultTypes.APPLICATION

private val SearchResult.isSectionHeading: Boolean
    get() = resultType has ResultTypes.SECTION_HEADING

private infix fun Int.has(flag: Int): Boolean = this and flag != 0
