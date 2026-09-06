package my.github.MrxSiN.pixellauncherevolved.catalog

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * The single description of every tweak this module offers.
 *
 * The settings screen renders from this list and the hooks read the same
 * [Setting] objects, so a tweak is described once and cannot drift between the
 * two sides.
 */
object FeatureCatalog {

    /** Preference group shared by the settings screen and the hooked process. */
    const val SETTINGS_GROUP: String = "settings"

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            setting = Settings.OVERVIEW_BUBBLE_BUTTON,
            titleRes = R.string.feature_overview_bubble_title,
            summaryRes = R.string.feature_overview_bubble_summary,
            category = CatalogCategory.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_SCREENSHOT,
            titleRes = R.string.feature_overview_hide_screenshot_title,
            summaryRes = R.string.feature_overview_hide_screenshot_summary,
            category = CatalogCategory.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_SELECT,
            titleRes = R.string.feature_overview_hide_select_title,
            summaryRes = R.string.feature_overview_hide_select_summary,
            category = CatalogCategory.OVERVIEW,
        ),
        CatalogEntry(
            setting = Settings.OVERVIEW_HIDE_CLEAR_ALL,
            titleRes = R.string.feature_overview_hide_clear_all_title,
            summaryRes = R.string.feature_overview_hide_clear_all_summary,
            category = CatalogCategory.OVERVIEW,
        ),
    )

    fun entriesIn(category: CatalogCategory): List<CatalogEntry> =
        entries.filter { it.category == category }
}

/** A tweak as the settings screen shows it. */
data class CatalogEntry(
    val setting: Setting<*>,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val category: CatalogCategory,
)

enum class CatalogCategory(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
) {
    OVERVIEW(R.string.category_overview_title, R.string.category_overview_summary),
    LAYOUT(R.string.category_layout_title, R.string.category_layout_summary),
    MISC(R.string.category_misc_title, R.string.category_misc_summary),
}
