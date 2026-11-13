package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.os.Handler
import android.os.Looper

import java.util.concurrent.CopyOnWriteArrayList

/**
 * What At a Glance was last handed, after this module's content was merged in, for the settings
 * preview. Settings runs in the launcher's process, so the preview reads the very list the home
 * screen draws.
 *
 * [publish] runs on every list the launcher hands a view, on any thread: one identity check and a
 * volatile write, and a post only while a preview is open ([listen]).
 */
object GlancePreview {

    @Volatile
    var targets: List<*> = emptyList<Any>()
        private set

    /** The system's own list [targets] was made from, before this module's content. */
    @Volatile
    private var given: List<*> = emptyList<Any>()

    /** Set by the content feature: the system's list with this module's content as it would be at a moment. */
    @Volatile
    internal var simulator: ((List<*>, GlanceMoment) -> List<*>)? = null

    /** Set by the content feature: the state its conditions read now. */
    @Volatile
    internal var reader: (() -> GlanceMoment)? = null

    /** What At a Glance would show at [moment]; what it shows now when nothing can simulate. */
    fun at(moment: GlanceMoment): List<*> = simulator?.let { simulate -> runCatching { simulate(given, moment) }.getOrNull() } ?: targets

    /** The phone's state now, as far as the content feature watches it; null before it is installed. */
    fun now(): GlanceMoment? = reader?.let { read -> runCatching { read() }.getOrNull() }

    private val listeners = CopyOnWriteArrayList<Runnable>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val notify = Runnable { for (listener in listeners) listener.run() }

    internal fun publish(system: List<*>, list: List<*>) {
        if (list === targets) return
        given = system
        targets = list
        if (listeners.isEmpty()) return
        main.removeCallbacks(notify)
        main.post(notify)
    }

    /** Main thread: [listener] runs on the main thread after each change, until [unlisten]. */
    fun listen(listener: Runnable) {
        listeners += listener
    }

    fun unlisten(listener: Runnable) {
        listeners -= listener
    }
}
