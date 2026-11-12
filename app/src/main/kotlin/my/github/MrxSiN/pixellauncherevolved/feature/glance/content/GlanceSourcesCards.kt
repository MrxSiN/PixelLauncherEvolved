package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.CalendarCard
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.ClockCard
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.DateCard
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.GreetingCard

/** Rewritten Smartspacer Targets (information cards), group A. */
object GlanceSourcesCards {
    val all: List<GlanceSource> = listOf(CalendarCard(), DateCard(), ClockCard(), GreetingCard())
}
