package my.github.MrxSiN.pixellauncherevolved.catalog

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * How much of the tablet layout the launcher adopts.
 *
 * Each answer other than [DEFAULT] is stored as a switch of its own, because
 * that is how earlier versions stored them and a renamed key resets the choice.
 * At most one of those switches is on, and none being on is [DEFAULT].
 *
 * Every mode but the default rebuilds the launcher's layout at startup, which
 * is what makes them the experimental tweaks Safe Mode turns off after a crash
 * loop.
 */
enum class LayoutMode(
    val setting: BoolSetting?,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
) {
    DEFAULT(null, R.string.layout_mode_default_title, R.string.layout_mode_default_summary),
    OVERVIEW_ONLY(Settings.OVERVIEW_ONLY, R.string.layout_mode_overview_only_title, R.string.layout_mode_overview_only_summary),
    TASKBAR_ONLY(Settings.TASKBAR_ONLY, R.string.layout_mode_taskbar_only_title, R.string.layout_mode_taskbar_only_summary),
    FULL_TABLET(Settings.TABLET_MODE, R.string.layout_mode_full_tablet_title, R.string.layout_mode_full_tablet_summary);

    /** The stored switches that choose this mode: its own on, every other off. */
    fun switches(): Map<BoolSetting, Boolean> = settings.associateWith { it == setting }

    companion object {

        /** The stored switches, one per mode that is not the default. */
        val settings: List<BoolSetting> = entries.mapNotNull(LayoutMode::setting)

        /** The mode whose switch is on, or [DEFAULT] when none is. */
        fun current(isOn: (BoolSetting) -> Boolean): LayoutMode =
            entries.firstOrNull { mode -> mode.setting?.let(isOn) == true } ?: DEFAULT

        /** The mode whose stored switch is keyed [key], or null for a key that is not a layout mode. */
        fun ofKey(key: String): LayoutMode? = entries.firstOrNull { it.setting?.key == key }
    }
}
