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

    /** Silent when the drawer has not been built yet; there is then nothing stale. */
    fun rebuild() {
        val current = store?.get() ?: return
        Reflect.method(current.javaClass, NOTIFY_UPDATE)?.invoke(current)
    }

    private const val NOTIFY_UPDATE = "notifyUpdate"
}
