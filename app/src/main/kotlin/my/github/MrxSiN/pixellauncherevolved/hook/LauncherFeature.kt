package my.github.MrxSiN.pixellauncherevolved.hook

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * One launcher tweak.
 *
 * A feature installs its own hooks and decides for itself what the current
 * settings call for. Adding a tweak means adding a class and listing it in
 * [FeatureRegistry]; nothing else changes.
 */
interface LauncherFeature {

    /** Name used in logs. */
    val id: String

    /**
     * Whether this feature applies a settings change to a running launcher.
     *
     * A live feature is installed whatever its setting says and consults that
     * setting from inside its hooks, so switching it on or off takes effect on
     * the next frame rather than on the next launcher start. The price is that
     * its hooks exist while it is switched off, where they do nothing.
     *
     * A feature that changes something the launcher reads once — a grid size,
     * say — cannot work that way and is installed only when enabled.
     */
    val isLive: Boolean get() = true

    /**
     * The launcher members this feature stands on, or null for one that checks
     * its own. A feature whose members this launcher build lacks is not
     * installed at all, and settings says so beside its switch.
     */
    val compatibility: CompatibilityFeature? get() = null

    fun isEnabled(settings: SettingsSource): Boolean

    fun install(context: FeatureContext)
}

/** A feature governed by a single on/off preference. */
abstract class ToggleFeature(protected val toggle: BoolSetting) : LauncherFeature {

    override val id: String get() = toggle.key

    override fun isEnabled(settings: SettingsSource): Boolean = settings[toggle]
}
