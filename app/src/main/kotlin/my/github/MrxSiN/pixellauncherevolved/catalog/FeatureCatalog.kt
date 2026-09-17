package my.github.MrxSiN.pixellauncherevolved.catalog

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * The single description of every switch this module offers.
 *
 * The settings pages place these entries and the hooks read the same [Setting]
 * objects, so a tweak is described once and cannot drift between the two sides.
 * Where on screen an entry appears is not decided here: that belongs to the
 * settings pages, which lay out rows that are not switches as well.
 *
 * The layout choices are not here either. They are one question with several
 * answers rather than a switch each, and [LayoutMode] describes them.
 */
object FeatureCatalog {

    /**
     * The framework preference group earlier versions stored settings in.
     *
     * Settings now live in the launcher's own data directory; this is only
     * still named so the values already written can be carried across once.
     */
    const val SETTINGS_GROUP: String = "settings"

    val HOME_BLUR_WALLPAPER = CatalogEntry(
        setting = Settings.HOME_BLUR_WALLPAPER,
        titleRes = R.string.feature_home_blur_wallpaper_title,
        summaryRes = R.string.feature_home_blur_wallpaper_summary,
    )
    val FOCUS_HOME_SCREENS = CatalogEntry(
        setting = Settings.FOCUS_HOME_SCREENS,
        titleRes = R.string.feature_focus_home_screens_title,
        summaryRes = R.string.feature_focus_home_screens_summary,
    )
    val DOUBLE_TAP_TO_SLEEP = CatalogEntry(
        setting = Settings.HOME_DOUBLE_TAP_TO_SLEEP,
        titleRes = R.string.feature_home_double_tap_to_sleep_title,
        summaryRes = R.string.feature_home_double_tap_to_sleep_summary,
    )
    val SEARCH_OPENS_DRAWER = CatalogEntry(
        setting = Settings.HOME_SEARCH_OPENS_DRAWER,
        titleRes = R.string.feature_home_search_opens_drawer_title,
        summaryRes = R.string.feature_home_search_opens_drawer_summary,
    )
    val WEB_SEARCH = CatalogEntry(
        setting = Settings.APP_DRAWER_SEARCH_HIDE_WEB,
        titleRes = R.string.feature_app_drawer_search_web_title,
        summaryRes = R.string.feature_app_drawer_search_web_summary,
        isInverted = true,
    )
    val PLAY_STORE = CatalogEntry(
        setting = Settings.APP_DRAWER_SEARCH_HIDE_PLAY_STORE,
        titleRes = R.string.feature_app_drawer_search_play_store_title,
        summaryRes = R.string.feature_app_drawer_search_play_store_summary,
        isInverted = true,
    )
    val SEARCH_IN_APPS = CatalogEntry(
        setting = Settings.APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS,
        titleRes = R.string.feature_app_drawer_search_in_apps_title,
        summaryRes = R.string.feature_app_drawer_search_in_apps_summary,
        isInverted = true,
    )
    val BUBBLE = CatalogEntry(
        setting = Settings.OVERVIEW_BUBBLE_BUTTON,
        titleRes = R.string.feature_overview_bubble_title,
        summaryRes = R.string.feature_overview_bubble_summary,
    )
    val SPLIT_SCREEN = CatalogEntry(
        setting = Settings.OVERVIEW_SPLIT_BUTTON,
        titleRes = R.string.feature_overview_split_title,
        summaryRes = R.string.feature_overview_split_summary,
    )
    val CLEAR_ALL = CatalogEntry(
        setting = Settings.OVERVIEW_CLEAR_ALL_IN_ACTIONS,
        titleRes = R.string.feature_overview_clear_all_title,
        summaryRes = R.string.feature_overview_clear_all_summary,
    )
    val SCREENSHOT = CatalogEntry(
        setting = Settings.OVERVIEW_HIDE_SCREENSHOT,
        titleRes = R.string.feature_overview_screenshot_title,
        summaryRes = R.string.feature_overview_screenshot_summary,
        isInverted = true,
    )
    val SELECT = CatalogEntry(
        setting = Settings.OVERVIEW_HIDE_SELECT,
        titleRes = R.string.feature_overview_select_title,
        summaryRes = R.string.feature_overview_select_summary,
        isInverted = true,
    )
    val TASKBAR_APP_DRAWER_BUTTON = CatalogEntry(
        setting = Settings.OVERVIEW_HIDE_TASKBAR_ALL_APPS,
        titleRes = R.string.feature_taskbar_app_drawer_button_title,
        summaryRes = R.string.feature_taskbar_app_drawer_button_summary,
        isInverted = true,
    )

    val entries: List<CatalogEntry> = listOf(
        HOME_BLUR_WALLPAPER,
        FOCUS_HOME_SCREENS,
        DOUBLE_TAP_TO_SLEEP,
        SEARCH_OPENS_DRAWER,
        WEB_SEARCH,
        PLAY_STORE,
        SEARCH_IN_APPS,
        BUBBLE,
        SPLIT_SCREEN,
        CLEAR_ALL,
        SCREENSHOT,
        SELECT,
        TASKBAR_APP_DRAWER_BUTTON,
    )

    /** Every on/off setting there is, the switches and the layout choices alike. */
    val switches: List<BoolSetting> = entries.map(CatalogEntry::setting) + LayoutMode.settings
}

/**
 * A switch as the settings pages show it.
 *
 * @property isInverted true when the switch reads on while the stored setting
 * is off. The stored settings say what to hide, because hiding is what the
 * hooks do and a stored key cannot be renamed without resetting it; the row says
 * what is shown, because that is what a person is choosing.
 */
data class CatalogEntry(
    val setting: BoolSetting,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val isInverted: Boolean = false,
) {

    /** Whether the switch reads on, for a stored value. */
    fun shown(stored: Boolean): Boolean = stored != isInverted

    /** The value to store, for a switch that reads [shown]. */
    fun stored(shown: Boolean): Boolean = shown != isInverted
}
