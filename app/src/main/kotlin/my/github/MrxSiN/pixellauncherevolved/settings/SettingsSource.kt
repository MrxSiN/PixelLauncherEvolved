package my.github.MrxSiN.pixellauncherevolved.settings

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

/** Read-only view of the stored settings, as the hooked process sees them. */
interface SettingsSource {
    operator fun get(setting: BoolSetting): Boolean
    operator fun get(setting: IntSetting): Int
}

/** Every setting at its default; used when no preferences are reachable. */
object DefaultSettings : SettingsSource {
    override fun get(setting: BoolSetting): Boolean = setting.default
    override fun get(setting: IntSetting): Int = setting.default
}
