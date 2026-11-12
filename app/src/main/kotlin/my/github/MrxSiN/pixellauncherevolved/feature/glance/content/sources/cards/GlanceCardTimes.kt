package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards

import java.util.Calendar
import java.util.TimeZone

/** The time arithmetic behind the cards, free of Android so it can be tested on the JVM. */
internal object GlanceCardTimes {
    const val MINUTE = 60_000L
    const val DAY = 24L * 60 * MINUTE

    /** Local hours at which a greeting starts: morning, afternoon, evening, night (as Smartspacer). */
    private val GREETING_STARTS = intArrayOf(3, 12, 18, 22)

    /** 0 morning, 1 afternoon, 2 evening, 3 night. */
    fun greetingPeriod(hour: Int): Int = when (hour) {
        in 3..11 -> 0
        in 12..17 -> 1
        in 18..21 -> 2
        else -> 3
    }

    /** The first moment after [now] that a greeting starts. */
    fun nextGreetingChange(now: Long, zone: TimeZone): Long {
        var best = Long.MAX_VALUE
        for (hour in GREETING_STARTS) {
            val c = day(now, zone).apply { set(Calendar.HOUR_OF_DAY, hour) }
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
            if (c.timeInMillis < best) best = c.timeInMillis
        }
        return best
    }

    fun startOfDay(now: Long, zone: TimeZone): Long = day(now, zone).timeInMillis

    fun nextMidnight(now: Long, zone: TimeZone): Long =
        day(now, zone).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    /** Calendar days from [now] to [time]: 0 same day, -1 yesterday, 1 tomorrow. */
    fun dayOffset(now: Long, time: Long, zone: TimeZone): Int =
        Math.round((startOfDay(time, zone) - startOfDay(now, zone)).toDouble() / DAY).toInt()

    /** Whether an event shows: not over, and starting within [lead] (a started one always does). */
    fun shows(start: Long, end: Long, now: Long, lead: Long): Boolean = end > now && start - now <= lead

    /** Minutes until [start] when that is 1 to 60, else 0; counted from the start of the current minute. */
    fun countdown(start: Long, now: Long): Int {
        val minute = now - now % MINUTE
        if (start < minute) return 0
        val minutes = ((start - minute) / MINUTE).toInt()
        return if (minutes in 1..60) minutes else 0
    }

    /**
     * The next moment the calendar card can differ: an event comes within [lead], starts or ends; the
     * minute turns while a countdown shows; or [midnight], when the day's events are read again.
     */
    fun nextCalendarChange(starts: LongArray, ends: LongArray, now: Long, lead: Long, midnight: Long): Long {
        var best = midnight
        for (i in starts.indices) {
            val s = starts[i]
            val e = ends[i]
            if (e <= now) continue
            if (s - lead > now) best = minOf(best, s - lead)
            if (s > now) best = minOf(best, s)
            best = minOf(best, e)
            if (shows(s, e, now, lead) && countdown(s, now) > 0) best = minOf(best, now - now % MINUTE + MINUTE)
        }
        return best
    }

    private fun day(now: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
