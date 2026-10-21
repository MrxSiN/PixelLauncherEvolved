package my.github.MrxSiN.pixellauncherevolved.hook

import java.util.concurrent.ConcurrentHashMap

import my.github.MrxSiN.pixellauncherevolved.feature.layout.TabletModeFeature
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HideAppsPickerFeature
import my.github.MrxSiN.pixellauncherevolved.feature.diagnostics.CompatibilityWatchFeature
import my.github.MrxSiN.pixellauncherevolved.feature.dock.DockFeature
import my.github.MrxSiN.pixellauncherevolved.feature.safemode.SafeModeFeature
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.apps.ModeDrawerSectionFeature
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusHomeFeature
import my.github.MrxSiN.pixellauncherevolved.feature.gesture.DoubleTapToSleepFeature
import my.github.MrxSiN.pixellauncherevolved.feature.grid.GridFeature
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconPackFeature
import my.github.MrxSiN.pixellauncherevolved.feature.labels.AppLabelsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.OverviewOnlyFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.TaskbarAllAppsButtonFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.TaskbarHomeVisibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.TaskbarOnlyFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.TaskbarTransitionFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewActionsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewActionsMotionFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewBubbleFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewClearAllButtonFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewSplitButtonFeature
import my.github.MrxSiN.pixellauncherevolved.feature.search.AppDrawerSearchFeature
import my.github.MrxSiN.pixellauncherevolved.feature.search.HomeSearchBarFeature
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppFeature
import my.github.MrxSiN.pixellauncherevolved.feature.settings.LauncherSettingsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.statusbar.StatusBarSleepFeature
import my.github.MrxSiN.pixellauncherevolved.feature.wallpaper.HomeWallpaperBlurFeature

/**
 * Every tweak the module can install.
 *
 * This list is the only thing that changes when a feature is added, and nothing
 * here knows what any feature does.
 */
object FeatureRegistry {

    val features: List<LauncherFeature> = listOf(
        LauncherSettingsFeature(),
        SafeModeFeature(),
        CompatibilityWatchFeature(),
        TabletModeFeature(),
        TaskbarOnlyFeature(),
        OverviewOnlyFeature(),
        TaskbarTransitionFeature(),
        TaskbarHomeVisibilityFeature(),
        TaskbarAllAppsButtonFeature(),
        HomeWallpaperBlurFeature(),
        DoubleTapToSleepFeature(),
        StatusBarSleepFeature(),
        HomeSearchBarFeature(),
        DockFeature(),
        GridFeature(),
        IconPackFeature(),
        AppLabelsFeature(),
        HiddenAppsFeature(),
        ModeDrawerSectionFeature(),
        HideAppsPickerFeature(),
        AppDrawerSearchFeature(),
        WebSearchAppFeature(),
        FocusHomeFeature(),
        OverviewBubbleFeature(),
        OverviewSplitButtonFeature(),
        OverviewActionsFeature(),
        OverviewClearAllButtonFeature(),
        OverviewActionsMotionFeature(),
    )

    /**
     * Installs the features that need to be running.
     *
     * A live feature is installed whatever its setting says, because it reads
     * that setting from inside its hooks; anything else is installed only when
     * switched on. A feature this launcher build lacks the members for is not
     * installed, rather than installed to fail somewhere later. A feature that
     * throws is skipped and reported: one broken tweak must not take the rest of
     * the launcher down with it, nor leave half of itself hooked: every hook it
     * had set by then is taken back ([HookRecorder]), and it is listed in
     * [failures] for Diagnostics to show.
     */
    fun install(context: FeatureContext, hooks: HookRecorder) {
        val wanted = features.filter { it.isLive || it.isEnabled(context.settings) }

        for (feature in wanted) {
            val needs = feature.compatibility
            if (needs != null && !context.analysis.isAvailable(needs)) {
                context.logger.warn(
                    "Feature ${feature.id} is unavailable on Pixel Launcher ${context.analysis.launcherVersion}; not installed",
                )
                continue
            }

            val failure = hooks.install { feature.install(context) }
            if (failure == null) {
                context.logger.info("Installed ${feature.id}")
            } else {
                failed[feature.id] = failure.error.toString()
                context.logger.warn("Feature ${feature.id} failed to install; took back the ${failure.undone} hook(s) it had set", failure.error)
            }
        }
    }

    private val failed = ConcurrentHashMap<String, String>()

    /** Features that failed to install in this launcher process, by id, with why. */
    val failures: Map<String, String> get() = failed
}
