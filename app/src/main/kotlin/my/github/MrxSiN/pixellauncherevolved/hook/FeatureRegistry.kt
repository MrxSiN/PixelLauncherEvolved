package my.github.MrxSiN.pixellauncherevolved.hook

import my.github.MrxSiN.pixellauncherevolved.feature.misc.RestartLauncherFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewActionsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewBubbleFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskMenuClearAllFeature

/**
 * Every tweak the module can install.
 *
 * This list is the only thing that changes when a feature is added, and nothing
 * here knows what any feature does.
 */
object FeatureRegistry {

    val features: List<LauncherFeature> = listOf(
        OverviewBubbleFeature(),
        OverviewActionsFeature(),
        TaskMenuClearAllFeature(),
        RestartLauncherFeature(),
    )

    /**
     * Installs the features that need to be running.
     *
     * A live feature is installed whatever its setting says, because it reads
     * that setting from inside its hooks; anything else is installed only when
     * switched on. A feature that throws is skipped and reported: one broken
     * tweak must not take the rest of the launcher down with it.
     */
    fun install(context: FeatureContext) {
        val wanted = features.filter { it.isLive || it.isEnabled(context.settings) }

        for (feature in wanted) {
            runCatching { feature.install(context) }
                .onSuccess { context.logger.info("Installed ${feature.id}") }
                .onFailure { context.logger.warn("Feature ${feature.id} failed to install", it) }
        }
    }
}
