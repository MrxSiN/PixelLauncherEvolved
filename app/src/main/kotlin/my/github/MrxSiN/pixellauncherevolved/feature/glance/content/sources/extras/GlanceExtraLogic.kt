package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import java.time.Instant
import java.time.ZoneId

/** The Android-free arithmetic the At a Glance extras share. */
internal object GlanceExtraLogic {

    /** The battery as a whole percent, or -1 when the broadcast carried no usable level. */
    fun percent(level: Int, scale: Int): Int = if (level < 0 || scale <= 0) -1 else (level * 100L / scale).toInt().coerceIn(0, 100)

    /** The first instant of the day after [now] in [zone], in epoch milliseconds. */
    fun nextMidnight(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
