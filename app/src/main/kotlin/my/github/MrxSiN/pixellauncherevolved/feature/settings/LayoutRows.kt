package my.github.MrxSiN.pixellauncherevolved.feature.settings

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconPackController

/**
 * The layout modes, as one radio button each.
 *
 * They are several answers to one question, so choosing one is the only way to
 * leave another: every row redraws, and the radio button that gained the choice
 * springs in while the one that lost it empties.
 */
internal object LayoutModeRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val rows = mutableListOf<Any>()

        for (mode in LayoutMode.entries) {
            val key = SettingsKeys.row("layout_mode_" + mode.name.lowercase())
            val row = scope.api.createAction(scope.context, key, scope.string(mode.titleRes), scope.string(mode.summaryRes)) {
                mode.switches().forEach { (setting, on) -> scope.settings.put(setting, on) }
                rows.forEach(scope.api::refresh)
            }
            scope.accessory(key) { RowAccessory.Radio(LayoutMode.current(scope.settings::get) == mode) }
            scope.api.add(group, row)
            requirementOf(mode)?.let { scope.requires(it, row) }
            rows += row
        }
    }

    /** What each mode stands on in the launcher; the default stands on nothing. */
    private fun requirementOf(mode: LayoutMode): CompatibilityFeature? = when (mode) {
        LayoutMode.DEFAULT -> null
        LayoutMode.OVERVIEW_ONLY -> CompatibilityFeature.OVERVIEW_ONLY
        LayoutMode.TASKBAR_ONLY -> CompatibilityFeature.TASKBAR_ONLY
        LayoutMode.FULL_TABLET -> CompatibilityFeature.FULL_TABLET_LAYOUT
    }
}

/**
 * Ends the launcher process and lets Android start it again.
 *
 * Asked first: the home screen, the taskbar and the gesture navigation all go
 * away for a moment, which is not something to do on a stray tap.
 */
/** Draws the icon pack's icons again, without restarting the launcher. */
internal object RefreshIconsRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        lateinit var row: Any
        row = scope.link(SettingsKeys.row("refresh_icons"), R.string.action_refresh_icons, scope.string(R.string.action_refresh_icons_summary)) {
            val controller = IconPackController.current()
            if (controller == null) {
                scope.api.setSummary(row, scope.string(R.string.action_refresh_icons_unavailable))
            } else {
                controller.refresh()
                scope.api.setSummary(row, scope.string(R.string.action_refresh_icons_done))
            }
        }
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.ICON_PACK, row)
    }
}

internal object RestartRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val row = scope.link(
            SettingsKeys.row("restart_launcher"),
            R.string.action_restart_launcher,
            scope.string(R.string.action_restart_launcher_summary),
        ) {
            ExpressiveDialog(scope.context)
                .title(scope.string(R.string.action_restart_launcher_confirm_title))
                .message(scope.string(R.string.action_restart_launcher_confirm_message))
                .dismiss(scope.context.getString(android.R.string.cancel))
                .confirm(scope.string(R.string.action_restart_launcher_confirm)) { scope.environment.onRestart() }
                .show()
        }
        scope.api.add(group, row)
    }
}
