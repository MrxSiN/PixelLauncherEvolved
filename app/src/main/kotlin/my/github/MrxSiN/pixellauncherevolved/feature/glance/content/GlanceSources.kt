package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.AlarmExtra
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.TorchCard

/**
 * Every At a Glance source there is, in the order they show.
 *
 * Each group of rewritten Smartspacer built-ins lives in its own file and list, so sources can be
 * added without touching another group's.
 */
object GlanceSources {

    const val KEY_PREFIX = "glance_source_"

    val all: List<GlanceSource> by lazy {
        listOf<GlanceSource>(TorchCard(), AlarmExtra()) +
            GlanceSourcesCards.all + GlanceSourcesExtras.all + GlanceSourcesMore.all
    }

    /** The stored switch for [source]; absent reads off. */
    fun key(source: GlanceSource): String = KEY_PREFIX + source.id
}
