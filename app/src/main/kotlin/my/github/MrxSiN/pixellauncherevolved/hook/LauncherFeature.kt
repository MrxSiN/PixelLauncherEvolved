package my.github.MrxSiN.pixellauncherevolved.hook

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * One launcher tweak.
 *
 * A feature decides for itself whether the current settings call for it and
 * then installs its own hooks. Adding a tweak means adding a class and listing
 * it in [FeatureRegistry]; nothing else changes.
 */
interface LauncherFeature {

    /** Name used in logs. */
    val id: String

    fun isEnabled(settings: SettingsSource): Boolean

    fun install(context: FeatureContext)
}

/** A feature governed by a single on/off preference. */
abstract class ToggleFeature(private val toggle: BoolSetting) : LauncherFeature {

    override val id: String get() = toggle.key

    override fun isEnabled(settings: SettingsSource): Boolean = settings[toggle]
}
