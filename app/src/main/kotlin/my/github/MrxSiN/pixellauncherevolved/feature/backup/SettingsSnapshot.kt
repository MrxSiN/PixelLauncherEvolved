package my.github.MrxSiN.pixellauncherevolved.feature.backup

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/**
 * Every tweak a person chose, as one value that can leave the device.
 *
 * Only what means the same on another phone is here. Which pages a Mode shows
 * and the order of the pages are not: both are kept by screen id and Mode id,
 * which belong to this launcher database and this device's Modes.
 */
data class SettingsSnapshot(
    /** The catalogue's switches, by stored value — a hide setting stored as hidden. */
    val switches: Map<BoolSetting, Boolean>,
    val blurStrength: Int,
    val layoutMode: LayoutMode,
    val hiddenApps: Set<String>,
    /** Null leaves tapped web results to the launcher's own choice. */
    val webSearchApp: String?,
) {
    companion object {
        /** What a fresh install has. */
        val DEFAULTS = SettingsSnapshot(
            switches = FeatureCatalog.entries.associate { it.setting to it.setting.default },
            blurStrength = Settings.HOME_BLUR_STRENGTH.default,
            layoutMode = LayoutMode.DEFAULT,
            hiddenApps = emptySet(),
            webSearchApp = null,
        )
    }
}

/** Reads the tweaks out of their stores, and writes a snapshot back into them. */
class TweakStores(
    private val settings: SettingsStore,
    private val hiddenApps: HiddenAppsStore,
    private val webSearchApp: WebSearchAppStore,
) {

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        switches = FeatureCatalog.entries.associate { it.setting to settings[it.setting] },
        blurStrength = settings[Settings.HOME_BLUR_STRENGTH],
        layoutMode = LayoutMode.current(settings::get),
        hiddenApps = hiddenApps.hidden(),
        webSearchApp = webSearchApp.chosen(),
    )

    /** Replaces every tweak with [snapshot]'s. */
    fun restore(snapshot: SettingsSnapshot) {
        snapshot.switches.forEach { (setting, value) -> settings.put(setting, value) }
        settings.put(Settings.HOME_BLUR_STRENGTH, snapshot.blurStrength)
        snapshot.layoutMode.switches().forEach { (setting, on) -> settings.put(setting, on) }
        hiddenApps.hide(snapshot.hiddenApps)
        webSearchApp.choose(snapshot.webSearchApp)
    }

    fun reset() = restore(SettingsSnapshot.DEFAULTS)
}
