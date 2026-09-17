package my.github.MrxSiN.pixellauncherevolved.feature.settings

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.backup.BackupRows
import my.github.MrxSiN.pixellauncherevolved.feature.diagnostics.DiagnosticsRows

/** One of this module's pages: headed groups of rows, in the order they are read. */
internal class SettingsPage(
    val key: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val groups: List<SettingsGroup>,
)

/** A run of rows drawn as one card, under a heading unless [titleRes] is null. */
internal class SettingsGroup(
    @param:StringRes val titleRes: Int?,
    val rows: List<SettingsRow>,
)

/**
 * Every page this module adds to Home settings, and where each row sits.
 *
 * This is the only place the layout of the settings is written down. What a row
 * does belongs to the row; what a switch stores belongs to [FeatureCatalog].
 */
internal object SettingsPages {

    private val SEARCH_BAR = SettingsPage(
        key = "home_search_bar",
        titleRes = R.string.settings_page_search_bar,
        summaryRes = R.string.settings_page_search_bar_summary,
        groups = listOf(SettingsGroup(null, listOf(ToggleRow(FeatureCatalog.SEARCH_OPENS_DRAWER, CompatibilityFeature.HOME_SEARCH_BAR)))),
    )

    private val DIAGNOSTICS = SettingsPage(
        key = "diagnostics",
        titleRes = R.string.settings_page_diagnostics,
        summaryRes = R.string.settings_page_diagnostics_summary,
        groups = listOf(SettingsGroup(null, listOf(DiagnosticsRows))),
    )

    private val HOME_SCREEN = SettingsPage(
        key = "home_screen",
        titleRes = R.string.settings_page_home_screen,
        summaryRes = R.string.settings_page_home_screen_summary,
        groups = listOf(
            SettingsGroup(
                R.string.settings_group_appearance,
                listOf(ToggleRow(FeatureCatalog.HOME_BLUR_WALLPAPER, CompatibilityFeature.BLUR_WALLPAPER), BlurStrengthRow),
            ),
            SettingsGroup(
                R.string.settings_group_pages,
                listOf(OrganizePagesRow, ToggleRow(FeatureCatalog.FOCUS_HOME_SCREENS, CompatibilityFeature.FOCUS_HOME_SCREENS), FocusPagesRow),
            ),
            SettingsGroup(R.string.settings_group_gestures, listOf(ToggleRow(FeatureCatalog.DOUBLE_TAP_TO_SLEEP, CompatibilityFeature.DOUBLE_TAP_TO_SLEEP))),
            SettingsGroup(R.string.settings_group_search, listOf(PageLinkRow(SEARCH_BAR))),
        ),
    )

    private val APP_DRAWER = SettingsPage(
        key = "app_drawer",
        titleRes = R.string.settings_page_app_drawer,
        summaryRes = R.string.settings_page_app_drawer_summary,
        groups = listOf(
            SettingsGroup(R.string.settings_group_apps, listOf(HiddenAppsRow)),
            SettingsGroup(
                R.string.settings_group_search_results,
                listOf(
                    ToggleRow(FeatureCatalog.WEB_SEARCH, CompatibilityFeature.APP_DRAWER_SEARCH),
                    ToggleRow(FeatureCatalog.PLAY_STORE, CompatibilityFeature.APP_DRAWER_SEARCH),
                    ToggleRow(FeatureCatalog.SEARCH_IN_APPS, CompatibilityFeature.APP_DRAWER_SEARCH),
                    WebSearchAppRow,
                ),
            ),
        ),
    )

    private val OVERVIEW = SettingsPage(
        key = "overview",
        titleRes = R.string.settings_page_overview,
        summaryRes = R.string.settings_page_overview_summary,
        groups = listOf(
            SettingsGroup(
                R.string.settings_group_overview_actions,
                listOf(
                    ToggleRow(FeatureCatalog.BUBBLE, CompatibilityFeature.BUBBLE_LAUNCHER),
                    ToggleRow(FeatureCatalog.SPLIT_SCREEN, CompatibilityFeature.SPLIT_SCREEN),
                    ToggleRow(FeatureCatalog.CLEAR_ALL, CompatibilityFeature.OVERVIEW_ACTIONS),
                    ToggleRow(FeatureCatalog.SCREENSHOT, CompatibilityFeature.OVERVIEW_ACTIONS),
                    ToggleRow(FeatureCatalog.SELECT, CompatibilityFeature.OVERVIEW_ACTIONS),
                ),
            ),
        ),
    )

    private val LAYOUT_AND_TASKBAR = SettingsPage(
        key = "layout",
        titleRes = R.string.settings_page_layout,
        summaryRes = R.string.settings_page_layout_summary,
        groups = listOf(
            SettingsGroup(R.string.settings_group_layout_mode, listOf(LayoutModeRows)),
            SettingsGroup(R.string.settings_group_taskbar, listOf(ToggleRow(FeatureCatalog.TASKBAR_APP_DRAWER_BUTTON, CompatibilityFeature.TASKBAR_APP_DRAWER_BUTTON))),
        ),
    )

    private val ADVANCED = SettingsPage(
        key = "advanced",
        titleRes = R.string.settings_page_advanced,
        summaryRes = R.string.settings_page_advanced_summary,
        groups = listOf(
            SettingsGroup(null, listOf(RestartRow, PageLinkRow(DIAGNOSTICS))),
            SettingsGroup(R.string.settings_group_backup, listOf(BackupRows)),
        ),
    )

    /** The pages listed under this module's heading in Home settings. */
    val topLevel: List<SettingsPage> = listOf(HOME_SCREEN, APP_DRAWER, OVERVIEW, LAYOUT_AND_TASKBAR, ADVANCED)

    private val all: List<SettingsPage> = topLevel + SEARCH_BAR + DIAGNOSTICS

    /** The page the launcher is opening under [rootKey], or null for one of its own. */
    fun byRootKey(rootKey: String?): SettingsPage? = all.firstOrNull { SettingsKeys.page(it) == rootKey }
}
