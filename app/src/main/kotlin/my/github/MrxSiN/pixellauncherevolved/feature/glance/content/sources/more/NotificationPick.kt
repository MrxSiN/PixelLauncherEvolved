package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

/** The pure rules behind the Notification card: which apps are chosen, and which notification wins. */
internal object NotificationPick {

    /** The stored comma-separated package names; empty means every app. */
    fun apps(value: String?): Set<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun encode(apps: Collection<String>): String = apps.sorted().joinToString(",")

    /**
     * Place of the newest eligible notification among [count], or -1. Eligible: from a chosen app
     * ([apps] empty is all) and not [skip] (secret, ongoing, group summary, this launcher's own).
     */
    inline fun newest(count: Int, apps: Set<String>, packageOf: (Int) -> String, timeOf: (Int) -> Long, skip: (Int) -> Boolean): Int {
        var best = -1
        var bestTime = Long.MIN_VALUE
        for (i in 0 until count) {
            if (skip(i) || (apps.isNotEmpty() && packageOf(i) !in apps)) continue
            val time = timeOf(i)
            if (best < 0 || time > bestTime) {
                best = i
                bestTime = time
            }
        }
        return best
    }
}
