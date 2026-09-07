package my.github.MrxSiN.pixellauncherevolved.catalog

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * The single description of every tweak this module offers.
 *
 * The settings section renders from this list and the hooks read the same
 * [Setting] objects, so a tweak is described once and cannot drift between the
 * two sides. The order here is the order a person reads on screen.
 */
object FeatureCatalog {

    /**
     * The framework preference group earlier versions stored settings in.
     *
     * Settings now live in the launcher's own data directory; this is only
     * still named so the values already written can be carried across once.
     */
    const val SETTINGS_GROUP: String = "settings"

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            setting = Settings.HOME_DOUBLE_TAP_TO_SLEEP,
            titleRes = R.string.feature_home_double_tap_to_sleep_title,
            summaryRes = R.string.feature_home_double_tap_to_sleep_summary,
            page = CatalogPage.HOME_SCREEN,
        ),
        CatalogEntry(
            setting = Settings.HOME_SEARCH_OPENS_DRAWER,
            titleRes = R.string.feature_home_search_opens_drawer_title,
            summaryRes = R.string.feature_home_search_opens_drawer_summary,
            page = CatalogPage.HOME_SCREEN,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_BUBBLE_BUTTON,
            titleRes = R.string.feature_overview_bubble_title,
            summaryRes = R.string.feature_overview_bubble_summary,
            page = CatalogPage.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_CLEAR_ALL_IN_ACTIONS,
            titleRes = R.string.feature_overview_clear_all_in_actions_title,
            summaryRes = R.string.feature_overview_clear_all_in_actions_summary,
            page = CatalogPage.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_SCREENSHOT,
            titleRes = R.string.feature_overview_hide_screenshot_title,
            summaryRes = R.string.feature_overview_hide_screenshot_summary,
            page = CatalogPage.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_SELECT,
            titleRes = R.string.feature_overview_hide_select_title,
            summaryRes = R.string.feature_overview_hide_select_summary,
            page = CatalogPage.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.TABLET_MODE,
            titleRes = R.string.feature_tablet_mode_title,
            summaryRes = R.string.feature_tablet_mode_summary,
            page = CatalogPage.TABLET_LAYOUT,
        ),
        CatalogEntry(
            setting = Settings.TASKBAR_ONLY,
            titleRes = R.string.feature_taskbar_only_title,
            summaryRes = R.string.feature_taskbar_only_summary,
            page = CatalogPage.TABLET_LAYOUT,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_TASKBAR_ALL_APPS,
            titleRes = R.string.feature_overview_hide_taskbar_all_apps_title,
            summaryRes = R.string.feature_overview_hide_taskbar_all_apps_summary,
            page = CatalogPage.TABLET_LAYOUT,
        ),
    )

    fun entriesIn(page: CatalogPage): List<CatalogEntry> = entries.filter { it.page == page }
}

/** A tweak as the settings section shows it. */
data class CatalogEntry(
    val setting: Setting<*>,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val page: CatalogPage,
)

enum class CatalogPage(
    val key: String,
    @param:StringRes val titleRes: Int,
) {
    HOME_SCREEN("home_screen", R.string.settings_page_home_screen),
    OVERVIEW("overview", R.string.settings_page_overview),
    TABLET_LAYOUT("tablet_layout", R.string.settings_page_tablet_layout),
}
