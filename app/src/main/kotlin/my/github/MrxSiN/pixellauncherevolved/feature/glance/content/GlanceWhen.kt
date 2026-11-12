package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

/**
 * When a source's content shows ("When to show"), rewritten from Smartspacer's Requirements that
 * need no permission the launcher lacks: a time of day (`TimeDateRequirement`, as fixed periods),
 * music playing (`MusicPlayingRequirement`), and, beyond Smartspacer, charging.
 *
 * Stored per source as `glance_when_<id>`: absent is always, otherwise one of [values]. Pure, so it
 * is tested without a launcher; [GlanceConditions] supplies the state it reads.
 */
internal object GlanceWhen {

    const val KEY_PREFIX = "glance_when_"
    const val MUSIC = "music"
    const val CHARGING = "charging"
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"

    /** Every stored value, in the order settings offers them after "Always". */
    val values = listOf(MUSIC, CHARGING, MORNING, AFTERNOON, EVENING, NIGHT)

    /** Start and end minute of the day for each period; night wraps past midnight. */
    private val periods = mapOf(
        MORNING to (5 * 60 until 12 * 60),
        AFTERNOON to (12 * 60 until 17 * 60),
        EVENING to (17 * 60 until 22 * 60),
        NIGHT to (22 * 60 until 29 * 60),
    )

    fun key(source: GlanceSource): String = KEY_PREFIX + source.id

    /** Whether [value] holds now. An unknown value reads as always, so a newer setting never hides content. */
    fun holds(value: String?, minuteOfDay: Int, musicPlaying: Boolean, charging: Boolean): Boolean = when (value) {
        null -> true
        MUSIC -> musicPlaying
        CHARGING -> charging
        else -> periods[value]?.let { minuteOfDay in it || minuteOfDay + DAY in it } ?: true
    }

    /** Minutes from [minuteOfDay] until a period in [used] starts or ends, or null when none is used. */
    fun minutesToNextBoundary(used: Collection<String>, minuteOfDay: Int): Int? {
        var best: Int? = null
        for (value in used) {
            val range = periods[value] ?: continue
            for (edge in intArrayOf(range.first, range.last + 1)) {
                val at = edge % DAY
                val wait = ((at - minuteOfDay) % DAY + DAY) % DAY
                val minutes = if (wait == 0) DAY else wait
                if (best == null || minutes < best) best = minutes
            }
        }
        return best
    }

    private const val DAY = 24 * 60
}
