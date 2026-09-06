package my.github.MrxSiN.pixellauncherevolved.hook

import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewActionsFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewBubbleFeature

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
    )

    /**
     * Installs the features the current settings call for.
     *
     * A feature that throws is skipped and reported: one broken tweak must not
     * take the rest of the launcher down with it.
     */
    fun installEnabled(context: FeatureContext) {
        for (feature in features.filter { it.isEnabled(context.settings) }) {
            runCatching { feature.install(context) }
                .onSuccess { context.logger.info("Installed ${feature.id}") }
                .onFailure { context.logger.warn("Feature ${feature.id} failed to install", it) }
        }
    }
}
