package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

/**
 * When a source's content shows ("When to show"), rewritten from Smartspacer's Requirements that
 * need no permission the launcher lacks: a time of day (`TimeDateRequirement`, as fixed periods),
 * music playing (`MusicPlayingRequirement`), the kind of network (`WiFiRequirement` without a network
 * name, which needs location), and, beyond Smartspacer, charging and headphones.
 *
 * Stored per source as `glance_when_<id>`: absent is always, otherwise one of [values]. Pure, so it
 * is tested without a launcher; [GlanceConditions] supplies the state it reads.
 */
internal object GlanceWhen {

    const val KEY_PREFIX = "glance_when_"
    const val MUSIC = "music"
    const val CHARGING = "charging"
    const val WIFI = "wifi"
    const val MOBILE = "mobile"
    const val HEADPHONES = "headphones"
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"

    /**
     * A custom time: `time:<start>-<end>:<days>`, minutes of the day and a bit per day of the week
     * (bit 0 Sunday to bit 6 Saturday; 0 or 127 every day). An end at or before the start runs past
     * midnight, counted as the day it started.
     */
    const val CUSTOM_PREFIX = "time:"
    const val EVERY_DAY = 0x7F

    fun custom(start: Int, end: Int, days: Int): String = "$CUSTOM_PREFIX$start-$end:${days and EVERY_DAY}"

    /** A custom value's start, end and days, packed as `start | end << 11 | days << 22`; -1 when [value] is not one. */
    fun parseCustom(value: String?): Int {
        if (value == null || !value.startsWith(CUSTOM_PREFIX)) return -1
        var i = CUSTOM_PREFIX.length
        val parts = IntArray(3)
        var part = 0
        var digits = 0
        while (i < value.length) {
            val c = value[i++]
            if (c in '0'..'9') {
                parts[part] = parts[part] * 10 + (c - '0')
                if (++digits > 4) return -1
            } else if ((c == '-' && part == 0) || (c == ':' && part == 1)) {
                if (digits == 0) return -1
                part++
                digits = 0
            } else {
                return -1
            }
        }
        if (part != 2 || digits == 0) return -1
        val start = parts[0]
        val end = parts[1]
        val days = parts[2]
        if (start >= DAY || end > DAY || days > EVERY_DAY) return -1
        return start or (end shl 11) or (days shl 22)
    }

    fun customStart(packed: Int): Int = packed and 0x7FF
    fun customEnd(packed: Int): Int = (packed shr 11) and 0x7FF
    fun customDays(packed: Int): Int = (packed shr 22).let { if (it == 0) EVERY_DAY else it }

    /** Whether a [packed] custom time holds at [minuteOfDay] on [dayOfWeek] (0 Sunday; -1 unknown, any day). */
    fun holdsCustom(packed: Int, minuteOfDay: Int, dayOfWeek: Int): Boolean {
        val start = customStart(packed)
        val end = customEnd(packed)
        val days = customDays(packed)
        fun on(day: Int) = day < 0 || days and (1 shl day) != 0
        val yesterday = if (dayOfWeek < 0) -1 else (dayOfWeek + 6) % 7
        return if (end > start) {
            minuteOfDay in start until end && on(dayOfWeek)
        } else {
            (minuteOfDay >= start && on(dayOfWeek)) || (minuteOfDay < end && on(yesterday))
        }
    }

    /** Every stored value, in the order settings offers them after "Always". */
    val values = listOf(MUSIC, HEADPHONES, CHARGING, WIFI, MOBILE, MORNING, AFTERNOON, EVENING, NIGHT)

    /** Values that are not a time of day, so need no clock; a custom time needs one. */
    val stateValues = setOf(MUSIC, HEADPHONES, CHARGING, WIFI, MOBILE)

    /** Start and end minute of the day for each period; night wraps past midnight. */
    private val periods = mapOf(
        MORNING to (5 * 60 until 12 * 60),
        AFTERNOON to (12 * 60 until 17 * 60),
        EVENING to (17 * 60 until 22 * 60),
        NIGHT to (22 * 60 until 29 * 60),
    )

    fun key(source: GlanceSource): String = KEY_PREFIX + source.id

    /** Whether [value] holds now. An unknown value reads as always, so a newer setting never hides content. */
    fun holds(
        value: String?,
        minuteOfDay: Int,
        musicPlaying: Boolean,
        charging: Boolean,
        onWifi: Boolean = false,
        onMobile: Boolean = false,
        headphones: Boolean = false,
        dayOfWeek: Int = -1,
    ): Boolean = when (value) {
        null -> true
        MUSIC -> musicPlaying
        CHARGING -> charging
        WIFI -> onWifi
        MOBILE -> onMobile
        HEADPHONES -> headphones
        else -> {
            val custom = parseCustom(value)
            if (custom >= 0) holdsCustom(custom, minuteOfDay, dayOfWeek)
            else periods[value]?.let { minuteOfDay in it || minuteOfDay + DAY in it } ?: true
        }
    }

    /** Minutes from [minuteOfDay] until a period in [used] starts or ends, or null when none is used. */
    fun minutesToNextBoundary(used: Collection<String>, minuteOfDay: Int): Int? {
        var best: Int? = null
        for (value in used) {
            val custom = parseCustom(value)
            val edges = when {
                // Midnight too when only some days count: the day changing can flip it.
                custom >= 0 -> if (customDays(custom) == EVERY_DAY) intArrayOf(customStart(custom), customEnd(custom)) else intArrayOf(customStart(custom), customEnd(custom), 0)
                else -> periods[value]?.let { intArrayOf(it.first, it.last + 1) } ?: continue
            }
            for (edge in edges) {
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
