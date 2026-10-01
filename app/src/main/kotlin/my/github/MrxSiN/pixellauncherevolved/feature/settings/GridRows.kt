package my.github.MrxSiN.pixellauncherevolved.feature.settings

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.grid.Grid
import my.github.MrxSiN.pixellauncherevolved.feature.grid.GridSpec

/**
 * The Grid & size row Home settings keeps: All apps columns on the App drawer
 * page. Columns, rows, spacing and icon size are chosen in Wallpaper & style
 * ([my.github.MrxSiN.pixellauncherevolved.picker.GridPicker]).
 *
 * It is a stepped slider; a pick is stored at once and the launcher applies
 * it live (`GridFeature`).
 */
internal object GridRows {

    /** Columns in All apps, 3 up to what keeps a 48dp touch target, the launcher's own labelled Default. */
    val appsColumns = SettingsRow { group, scope ->
        val setting = Settings.APP_DRAWER_COLUMNS
        val key = SettingsKeys.row(setting.key)
        val own = Grid.stockAppsColumns
        val room = room(scope)
        if (!scope.api.hasSlider || room == null || own == 0) {
            val row = scope.api.createAction(scope.context, key, scope.string(R.string.feature_grid_apps_columns_title), scope.string(R.string.feature_grid_unknown)) {}
            scope.api.add(group, row)
            scope.api.setEnabled(row, false)
            scope.requires(CompatibilityFeature.APP_DRAWER_COLUMNS, row)
            return@SettingsRow
        }

        val counts = GridSpec.choices(own, room.appsColumns)
        fun current(): Int = GridSpec.count(scope.settings[setting], own).coerceIn(counts)
        fun spoken(count: Int): String = scope.resources.getQuantityString(R.plurals.feature_grid_columns_count, count, count)
        fun label(count: Int): String = if (count == own) scope.string(R.string.feature_grid_default, spoken(count)) else spoken(count)

        lateinit var row: Any
        row = slider(scope, key, R.string.feature_grid_apps_columns_title, label(current()))
        SteppedSliders.register(
            key,
            SteppedSliders.Stepped(
                steps = counts.last - counts.first,
                index = { current() - counts.first },
                label = { spoken(counts.first + it) },
                onMove = { scope.api.setSummary(row, label(counts.first + it)) },
                onPick = pick@{ index ->
                    val count = counts.first + index
                    if (count == current()) return@pick
                    scope.settings.put(setting, GridSpec.stored(count, own))
                    scope.api.setSummary(row, label(count))
                },
            ),
        )
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.APP_DRAWER_COLUMNS, row)
    }

    /**
     * Restores an imported Grid & size as this phone can take it: a count that
     * does not fit here, or a grid a Home screen widget cannot fit in, reads as
     * the launcher's own; sizes snap to the nearest step.
     */
    fun fitted(stored: Map<IntSetting, Int>, scope: RowScope): Map<IntSetting, Int> {
        val room = room(scope)
        var columns = if (room == null) GridSpec.SYSTEM else GridSpec.fitted(stored[Settings.GRID_COLUMNS] ?: 0, Grid.stockColumns, room.columns)
        var rows = if (room == null) GridSpec.SYSTEM else GridSpec.fitted(stored[Settings.GRID_ROWS] ?: 0, Grid.stockRows, room.rows)
        val items = runCatching { Grid.homeItems(scope.context, scope.context.classLoader) }.getOrNull()
        if (items == null || GridSpec.misfits(items, GridSpec.count(columns, Grid.stockColumns), GridSpec.count(rows, Grid.stockRows)).isNotEmpty()) {
            columns = GridSpec.SYSTEM
            rows = GridSpec.SYSTEM
        }
        return mapOf(
            Settings.GRID_COLUMNS to columns,
            Settings.GRID_ROWS to rows,
            Settings.GRID_ICON_SIZE to GridSpec.nearestIconSize(stored[Settings.GRID_ICON_SIZE] ?: GridSpec.ICON_DEFAULT),
            Settings.GRID_SPACING_X to (stored[Settings.GRID_SPACING_X] ?: 0).coerceIn(Settings.GRID_SPACING_X.range),
            Settings.GRID_SPACING_Y to (stored[Settings.GRID_SPACING_Y] ?: 0).coerceIn(Settings.GRID_SPACING_Y.range),
            Settings.APP_DRAWER_COLUMNS to if (room == null) GridSpec.SYSTEM else GridSpec.fitted(stored[Settings.APP_DRAWER_COLUMNS] ?: 0, Grid.stockAppsColumns, room.appsColumns),
        )
    }

    private fun room(scope: RowScope): Grid.Room? =
        runCatching { Grid.room(scope.context, scope.context.classLoader) }
            .onFailure { scope.environment.logger.warn("Grid & size: unable to measure the screen", it) }
            .getOrNull()

    private fun slider(scope: RowScope, key: String, @StringRes title: Int, summary: String): Any = scope.api.createSlider(
        context = scope.context,
        key = key,
        title = scope.string(title),
        summary = summary,
        value = 0,
        isEnabled = true,
        onChange = {},
    )
}
