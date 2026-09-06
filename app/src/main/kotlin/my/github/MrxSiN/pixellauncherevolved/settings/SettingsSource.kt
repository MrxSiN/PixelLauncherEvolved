package my.github.MrxSiN.pixellauncherevolved.settings

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting

/**
 * Read-only view of the stored settings, as the hooked process sees them.
 *
 * Values are read on every call rather than snapshotted, so a feature that
 * consults its setting from inside a hook picks up a change the moment the
 * settings screen writes it, with no launcher restart involved.
 */
interface SettingsSource {

    operator fun get(setting: BoolSetting): Boolean

    operator fun get(setting: IntSetting): Int

    /**
     * Calls [onChange] whenever any stored setting changes.
     *
     * Returns null when this source can never change, which lets a caller skip
     * work rather than wait for an event that will not arrive.
     */
    fun observe(onChange: () -> Unit): AutoCloseable?
}

/** Every setting at its default; used when no preferences are reachable. */
object DefaultSettings : SettingsSource {
    override fun get(setting: BoolSetting): Boolean = setting.default
    override fun get(setting: IntSetting): Int = setting.default
    override fun observe(onChange: () -> Unit): AutoCloseable? = null
}
