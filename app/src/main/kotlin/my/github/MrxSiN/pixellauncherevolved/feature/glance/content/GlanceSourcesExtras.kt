package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras.BatteryExtra
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras.CalendarNextExtra
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras.DateExtra
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras.TimeExtra

/** Rewritten Smartspacer Complications (extra information), group B. */
object GlanceSourcesExtras {
    val all: List<GlanceSource> = listOf(BatteryExtra(), DateExtra(), TimeExtra(), CalendarNextExtra())
}
