package my.github.MrxSiN.pixellauncherevolved.feature.apps

/**
 * The apps being ticked, while they are being ticked.
 *
 * Choosing happens in the app drawer itself rather than in a list of names, so
 * the choice is made where the apps are, against the icons a person recognises.
 * That puts the question in one activity and the answer in another — Home
 * settings asks, the launcher answers — and both are the same process, so a
 * plain object is the whole bridge. Nothing here is written down: the store is
 * written once, when the choice is confirmed.
 *
 * Everything runs on the main thread, which is the only thread that draws
 * icons or handles taps on them.
 */
object HideAppsSelection {

    private val chosen = LinkedHashSet<String>()
    private var selecting = false

    /** Called when a tick changes, so what is on screen can catch up. */
    var onChanged: (() -> Unit)? = null

    val isSelecting: Boolean get() = selecting

    val count: Int get() = chosen.size

    /** Starts from what is already hidden, so the ticks open where they left off. */
    fun begin(hidden: Set<String>) {
        chosen.clear()
        chosen.addAll(hidden)
        selecting = true
    }

    fun isChosen(packageName: String): Boolean = packageName in chosen

    fun toggle(packageName: String) {
        if (!chosen.remove(packageName)) chosen.add(packageName)
        onChanged?.invoke()
    }

    /** The answer, and the end of the asking. */
    fun confirm(): Set<String> = chosen.toSet().also { cancel() }

    fun cancel() {
        selecting = false
        chosen.clear()
        onChanged = null
    }
}
