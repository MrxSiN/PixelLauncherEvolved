package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

/**
 * Foreground time per app from a stream of resume/pause events, oldest first. Android-free so a
 * JVM test can drive it. An app is in the foreground from a resume until the next pause of the
 * same package; a repeated resume does not restart it, a pause with no resume is ignored, and an
 * app still open at [finish] counts up to `now`.
 */
internal class WellbeingTally(
    private val resumed: Int,
    private val paused: Int,
    private val skip: (String) -> Boolean,
) {
    private val since = HashMap<String, Long>()
    private val total = HashMap<String, Long>()

    fun add(type: Int, pkg: String, time: Long) {
        if (skip(pkg)) return
        if (type == resumed) {
            if (pkg !in since) since[pkg] = time
        } else if (type == paused) {
            val from = since.remove(pkg) ?: return
            if (time > from) total[pkg] = (total[pkg] ?: 0L) + (time - from)
        }
    }

    fun finish(now: Long) {
        for ((pkg, from) in since) if (now > from) total[pkg] = (total[pkg] ?: 0L) + (now - from)
        since.clear()
    }

    fun totalMillis(): Long = total.values.sum()

    /** Packages by time, longest first, at most [n]. */
    fun top(n: Int): List<String> = total.entries.sortedByDescending { it.value }.take(n).map { it.key }

    companion object {
        fun hours(millis: Long): Long = millis / 3_600_000L

        fun minutes(millis: Long): Long = millis / 60_000L % 60L
    }
}
