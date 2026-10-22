package my.github.MrxSiN.pixellauncherevolved.feature.apps

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The drawer's own list, kept so it can be asked to build itself again.
 *
 * The launcher rebuilds the drawer when the installed apps change and not
 * otherwise, so hiding an app has to say when it has happened. The store that
 * feeds the list carries the launcher's own way of saying it, and every list
 * built from that store hears it.
 *
 * Held weakly: the launcher outlives no activity, and a stale store is a
 * missed refresh rather than a leak.
 */
object AppDrawerList {

    private var store: WeakReference<Any>? = null

    fun remember(appsStore: Any) {
        store = WeakReference(appsStore)
    }

    /**
     * The prediction row above the list and the last apps the launcher gave it.
     * Predictions arrive on their own schedule, so a hide has to hand the row
     * its last list again rather than wait for the next one.
     */
    private var predictionRow: WeakReference<Any>? = null
    private var predicted: List<*>? = null
    private var applyPredicted: java.lang.reflect.Method? = null

    fun rememberPredictions(row: Any, apps: List<*>, apply: java.lang.reflect.Method) {
        if (predictionRow?.get() !== row) predictionRow = WeakReference(row)
        predicted = apps
        applyPredicted = apply
    }

    /** Silent when the drawer has not been built yet; there is then nothing stale. */
    fun rebuild() {
        val row = predictionRow?.get()
        val apps = predicted
        // A failing row must not keep the list itself stale.
        if (row != null && apps != null) runCatching { applyPredicted?.invoke(row, apps) }
        val current = store?.get() ?: return
        Reflect.method(current.javaClass, NOTIFY_UPDATE)?.invoke(current)
    }

    private const val NOTIFY_UPDATE = "notifyUpdate"
}
