package my.github.MrxSiN.pixellauncherevolved.feature.search

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The results below are the ones an Android 17 Pixel Launcher was seen to
 * receive for a typed query; see `HOOK_NOTES.md` for the capture.
 */
class SearchResultKindTest {

    private val webHeading = SearchResult(131072, "text_header_row", GOOGLE)
    private val webSuggestion = SearchResult(131072, "short_icon_row", GOOGLE)
    private val playHeading = SearchResult(8388608, "text_header_row", PLAY_STORE)
    private val playListing = SearchResult(256, "play_placeholder", PLAY_STORE)
    private val inAppsHeading = SearchResult(262144, "text_header_row", "")
    private val inAppsRow = SearchResult(512, "short_icon_row", PLAY_STORE)

    private val installedApp = SearchResult(1, "icon", "com.google.android.youtube")
    private val appHeading = SearchResult(8388608, "text_header_row", "com.google.android.youtube")
    private val appShortcut = SearchResult(2, "short_icon_row", "com.google.android.youtube")
    private val settingsSlice = SearchResult(16, "icon_row", "com.android.settings")
    private val separator = SearchResult(262144, "empty_divider", "")

    @Test
    fun webSearchTakesItsHeadingAndItsSuggestions() {
        val hidden = HiddenSearchResults(setOf(SearchResultKind.WEB_SEARCH), emptySet())

        assertTrue(hidden.hides(webHeading))
        assertTrue(hidden.hides(webSuggestion))
        assertEquals(untouched, untouched.filter { !hidden.hides(it) })
    }

    @Test
    fun playStoreTakesItsHeadingAndItsListings() {
        val hidden = HiddenSearchResults(setOf(SearchResultKind.PLAY_STORE), emptySet())

        assertTrue(hidden.hides(playHeading))
        assertTrue(hidden.hides(playListing))
        assertEquals(untouched, untouched.filter { !hidden.hides(it) })
    }

    @Test
    fun playStoreLeavesTheRowThatSearchesPlayStore() {
        // Same package, but it belongs to Search in Apps rather than to the
        // Play Store group, and hiding one must not empty the other.
        assertFalse(HiddenSearchResults(setOf(SearchResultKind.PLAY_STORE), emptySet()).hides(inAppsRow))
    }

    @Test
    fun searchInAppsTakesItsHeadingAndItsRows() {
        val hidden = HiddenSearchResults(setOf(SearchResultKind.SEARCH_IN_APPS), emptySet())

        assertTrue(hidden.hides(inAppsHeading))
        assertTrue(hidden.hides(inAppsRow))
        assertEquals(untouched, untouched.filter { !hidden.hides(it) })
    }

    @Test
    fun aSeparatorIsNotAHeading() {
        // It carries the heading's result type, so only the layout tells them
        // apart, and taking every separator would close up the whole list.
        assertFalse(HiddenSearchResults(SearchResultKind.entries.toSet(), emptySet()).hides(separator))
    }

    @Test
    fun aHiddenAppIsTakenOutOfTheResultsToo() {
        val hidden = HiddenSearchResults(emptySet(), setOf("com.instagram.android"))

        assertTrue(hidden.hides(SearchResult(1, "icon", "com.instagram.android")))
        assertFalse(hidden.hides(SearchResult(1, "icon", "com.instagram.other")))
    }

    @Test
    fun onlyAppResultsAnswerForATheirPackage() {
        // Search in Apps offers to search inside the same app; hiding the app
        // from the drawer is not a reason to drop that offer's own row.
        val hidden = HiddenSearchResults(emptySet(), setOf(PLAY_STORE))

        assertFalse(hidden.hides(inAppsRow))
        assertFalse(hidden.hides(playListing))
    }

    @Test
    fun nothingHiddenIsEmpty() {
        assertTrue(HiddenSearchResults(emptySet(), emptySet()).isEmpty)
        assertFalse(HiddenSearchResults(setOf(SearchResultKind.WEB_SEARCH), emptySet()).isEmpty)
        assertFalse(HiddenSearchResults(emptySet(), setOf("com.example")).isEmpty)
    }

    @Test
    fun nothingSwitchedOnHidesNothing() {
        val hidden = HiddenSearchResults(emptySet(), emptySet())

        for (result in untouched + webHeading + playListing + inAppsRow) {
            assertFalse(hidden.hides(result))
        }
    }

    @Test
    fun everySwitchedOnKindIsCollected() {
        assertEquals(emptySet<SearchResultKind>(), hiddenSearchResults(NothingOn))

        assertEquals(
            setOf(SearchResultKind.WEB_SEARCH, SearchResultKind.SEARCH_IN_APPS),
            hiddenSearchResults(
                On(
                    Settings.APP_DRAWER_SEARCH_HIDE_WEB,
                    Settings.APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS,
                ),
            ),
        )
    }

    /** What every kind has to leave alone: the launcher's own results. */
    private val untouched
        get() = listOf(installedApp, appHeading, appShortcut, settingsSlice, separator)

    private object NothingOn : SettingsSource {
        override fun get(setting: BoolSetting): Boolean = setting.default
    }

    private class On(private vararg val on: BoolSetting) : SettingsSource {
        override fun get(setting: BoolSetting): Boolean =
            on.any { it.key == setting.key } || setting.default
    }

    private companion object {
        const val GOOGLE = "com.google.android.googlequicksearchbox"
        const val PLAY_STORE = "com.android.vending"
    }
}
