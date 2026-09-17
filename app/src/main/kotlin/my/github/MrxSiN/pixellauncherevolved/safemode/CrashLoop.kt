package my.github.MrxSiN.pixellauncherevolved.safemode

/**
 * When the launcher's crashes count as a loop.
 *
 * Three crashes inside a minute is not bad luck: it is a launcher that starts,
 * reaches the same broken hook and dies again, which leaves nothing on screen to
 * reach Home settings from. One crash is not a loop and is left alone.
 */
object CrashLoop {

    const val CRASHES: Int = 3
    const val WINDOW_MILLIS: Long = 60_000L

    /** Whether [crashes], as wall-clock times, make a loop as of [now]. */
    fun isLooping(crashes: List<Long>, now: Long): Boolean =
        recent(crashes, now).size >= CRASHES

    /** The crashes still inside the window, which are the only ones worth keeping. */
    fun recent(crashes: List<Long>, now: Long): List<Long> =
        crashes.filter { now - it in 0..WINDOW_MILLIS }
}
