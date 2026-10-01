package my.github.MrxSiN.pixellauncherevolved.feature.settings

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.dock.Dock
import my.github.MrxSiN.pixellauncherevolved.feature.dock.DockFeature
import my.github.MrxSiN.pixellauncherevolved.feature.dock.DockIconSize
import my.github.MrxSiN.pixellauncherevolved.feature.dock.DockIcons

/**
 * Show dock, greyed out with the reason while a taskbar mirrors the dock.
 *
 * The switch is the catalogue's; only the taskbar check is added here.
 */
internal object ShowDockRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val entry = FeatureCatalog.SHOW_DOCK
        val row = scope.api.createSwitch(
            context = scope.context,
            key = SettingsKeys.row(entry.setting.key),
            title = scope.string(entry.titleRes),
            summary = scope.string(entry.summaryRes),
            checked = scope.isOn(entry),
            onChange = { on -> scope.switch(entry, on) },
        )
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.DOCK, row)

        // A dock hidden before the taskbar came on can still be shown again.
        val hotseat = Dock.hotseat()
        if (scope.isOn(entry) && hotseat != null && runCatching { Dock.hasTaskbar(hotseat) }.getOrDefault(false)) {
            scope.api.setEnabled(row, false)
            scope.api.setSummary(row, scope.string(R.string.feature_dock_taskbar))
        }
    }
}

/**
 * How many icons the dock holds, as a slider stepping through whole icons.
 *
 * The steps run from [DockIcons.FEWEST] to as many as fit across the dock with
 * each cell keeping a 48dp touch target, and include the grid's own count,
 * which is stored as System and is the only step with a summary, Default. Past
 * what fits at the Home screen's icon size the dock icons shrink into their
 * cells ([DockIconSize]). What a lower count does with the pinned apps it
 * leaves out is the Move to Home screen switch's, and [DockFeature] does it,
 * so a reset or an import is handled the same way. The count applies live.
 */
internal object DockIconsRow : SettingsRow {

    private val KEY = SettingsKeys.row(Settings.DOCK_ICONS.key)

    /**
     * An imported count as this phone's dock can take it: kept when it fits,
     * System otherwise. Moving any apps it leaves out is [DockFeature]'s.
     */
    fun fitted(count: Int): Int {
        if (count == 0) return 0
        val hotseat = Dock.hotseat() ?: return 0
        val stock = runCatching { Dock.stockIcons(hotseat) }.getOrNull() ?: return 0
        val fits = runCatching { Dock.fits(hotseat, DockIconSize.minCellPx(), DockIconSize.gapPx()) }.getOrNull() ?: return 0
        return if (count in DockIcons.choices(stock, fits)) count else 0
    }

    override fun addTo(group: Any, scope: RowScope) {
        val hotseat = Dock.hotseat()
        val stock = hotseat?.let { runCatching { Dock.stockIcons(it) }.getOrNull() }
        val fits = hotseat?.let { runCatching { Dock.fits(it, DockIconSize.minCellPx(), DockIconSize.gapPx()) }.getOrNull() }
        if (!scope.api.hasSlider || hotseat == null || stock == null || fits == null) {
            val row = scope.api.createAction(
                scope.context,
                KEY,
                scope.string(R.string.feature_dock_icons_title),
                scope.string(R.string.feature_dock_icons_unknown),
            ) {}
            scope.api.add(group, row)
            scope.api.setEnabled(row, false)
            return
        }

        val counts = DockIcons.choices(stock, fits)
        fun current(): Int = DockIcons.shown(scope.settings[Settings.DOCK_ICONS], stock).coerceIn(counts)
        fun label(count: Int): String = if (count == stock) scope.string(R.string.feature_dock_icons_default) else ""
        fun spoken(count: Int): String = scope.resources.getQuantityString(R.plurals.feature_dock_icons_count, count, count)

        lateinit var row: Any
        fun pick(count: Int) {
            if (count == current()) return
            scope.settings.put(Settings.DOCK_ICONS, DockIcons.stored(count, stock))
            scope.api.setSummary(row, label(count))
        }

        row = scope.api.createSlider(
            context = scope.context,
            key = KEY,
            title = scope.string(R.string.feature_dock_icons_title),
            summary = label(current()),
            value = 0,
            isEnabled = true,
            onChange = {},
        )
        SteppedSliders.register(
            KEY,
            SteppedSliders.Stepped(
                steps = counts.last - counts.first,
                index = { current() - counts.first },
                label = { spoken(counts.first + it) },
                onMove = { scope.api.setSummary(row, label(counts.first + it)) },
                onPick = { pick(counts.first + it) },
            ),
        )
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.DOCK_ICONS, row)
    }
}

/**
 * What to do before the module goes away.
 *
 * The extra Home screen row and a dock bigger than the grid's exist only while
 * the module runs. Without it Pixel Launcher reads its own grid again and
 * deletes what lies outside it at the next load, and nothing is left running
 * to move those items first. So the page says, in words, which two settings
 * to put back.
 */
internal object DockRemovalNoteRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val stock = Dock.hotseat()?.let { runCatching { Dock.stockIcons(it) }.getOrNull() }
        val summary = if (stock != null) {
            scope.string(R.string.feature_dock_removal_summary, stock)
        } else {
            scope.string(R.string.feature_dock_removal_summary_plain)
        }
        scope.api.add(group, scope.link(SettingsKeys.row("dock_removal_note"), R.string.feature_dock_removal_title, summary) {})
    }
}
