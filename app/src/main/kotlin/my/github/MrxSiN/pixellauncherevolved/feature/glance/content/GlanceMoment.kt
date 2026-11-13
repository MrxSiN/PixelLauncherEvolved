package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

/**
 * A state of the phone for the settings preview to show At a Glance in: everything "When to show"
 * ([GlanceWhen]) reads. The preview builds its list for a moment the person picks, so each choice
 * can be tried without waiting for the evening or plugging in a charger.
 */
data class GlanceMoment(
    val minuteOfDay: Int,
    /** 0 Sunday to 6 Saturday; -1 any day. */
    val dayOfWeek: Int = -1,
    val music: Boolean = false,
    val headphones: Boolean = false,
    val charging: Boolean = false,
    val wifi: Boolean = false,
    val mobile: Boolean = false,
) {
    fun holds(value: String?): Boolean = GlanceWhen.holds(value, minuteOfDay, music, charging, wifi, mobile, headphones, dayOfWeek)

    companion object {
        /** A minute well inside each part of the day, for the preview's time choices. */
        fun minuteOf(period: String): Int? = when (period) {
            GlanceWhen.MORNING -> 9 * 60
            GlanceWhen.AFTERNOON -> 14 * 60
            GlanceWhen.EVENING -> 19 * 60
            GlanceWhen.NIGHT -> 23 * 60
            else -> null
        }
    }
}
