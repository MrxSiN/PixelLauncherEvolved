package my.github.MrxSiN.pixellauncherevolved.settings

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

/**
 * Read-only view of the stored settings, as a feature sees them.
 *
 * Values are read on every call rather than snapshotted, so a feature that
 * consults its setting from inside a hook picks up a change the moment Home
 * settings writes it, with no launcher restart involved.
 */
interface SettingsSource {

    operator fun get(setting: BoolSetting): Boolean

    operator fun get(setting: IntSetting): Int
}
