package my.github.MrxSiN.pixellauncherevolved.feature.settings

import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogEntry
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature

/**
 * A switch described by the catalogue.
 *
 * @param requires what the tweak behind the switch stands on in the launcher,
 * which greys the switch out on a launcher build that lacks it.
 */
internal class ToggleRow(
    private val entry: CatalogEntry,
    private val requires: CompatibilityFeature,
) : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val row = scope.api.createSwitch(
            context = scope.context,
            key = SettingsKeys.row(entry.setting.key),
            title = scope.string(entry.titleRes),
            summary = scope.string(entry.summaryRes),
            checked = scope.isOn(entry),
            onChange = { on -> scope.switch(entry, on) },
        )
        scope.api.add(group, row)
        scope.requires(requires, row)
    }
}

/**
 * A row that opens another of this module's pages.
 *
 * The launcher opens it itself, naming the page by this row's key; the page is
 * only built when it is opened.
 */
internal class PageLinkRow(private val page: SettingsPage) : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val key = SettingsKeys.page(page)
        val link = scope.api.createScreen(scope.context, key, scope.string(page.titleRes), scope.string(page.summaryRes))
        scope.api.add(group, link)
        // androidx ignores a tap on a screen with no rows. The page is built
        // afresh when opened, so one empty row is all this copy needs to hold.
        scope.api.add(link, scope.api.createAction(scope.context, "$key$PLACEHOLDER", "", "") {})
    }

    private companion object {
        const val PLACEHOLDER = "_placeholder"
    }
}
