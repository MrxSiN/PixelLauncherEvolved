package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The launcher's own `NotificationListener` (bound by the system only while notification dots are
 * on), read without hooks: a listener of its change callbacks is registered through the class's own
 * static add/remove methods, as a dynamic proxy of whatever interface the add method takes. All
 * lookups are cold and by name through [Reflect]/[Host]; anything missing leaves [available] false
 * and the card showing nothing.
 */
internal class NotificationFeed(loader: ClassLoader, private val onChange: () -> Unit) {

    private val type = Host.cls(loader, LISTENER)
    private val connected: Method? = type?.let { Reflect.exactMethod(it, CONNECTED) }
    private val add = type?.let { Reflect.declared(it, ADD) { m -> m.parameterTypes.size == 1 && m.parameterTypes[0].isInterface } }
    private val remove = type?.let { Reflect.declared(it, REMOVE) { m -> m.parameterTypes.size == 1 } }

    private val callbacks: Any? = add?.parameterTypes?.get(0)?.let { iface ->
        Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), InvocationHandler { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "NotificationFeed"
                else -> {
                    onChange()
                    null
                }
            }
        })
    }

    private val available: Boolean get() = connected != null && add != null && remove != null && callbacks != null

    fun register(): Boolean = available && runCatching { add!!.apply { isAccessible = true }.invoke(null, callbacks) }.isSuccess

    fun unregister() {
        if (available) runCatching { remove!!.apply { isAccessible = true }.invoke(null, callbacks) }
    }

    /** The active notifications when the launcher's listener is bound; null otherwise. Binder: not for the main thread. */
    fun active(): Array<StatusBarNotification>? {
        val service = runCatching { connected!!.invoke(null) }.getOrNull() as? NotificationListenerService ?: return null
        return runCatching { service.activeNotifications }.getOrNull()
    }

    private companion object {
        const val LISTENER = "com.android.launcher3.notification.NotificationListener"
        const val CONNECTED = "getInstanceIfConnected"
        const val ADD = "addNotificationsChangeListener"
        const val REMOVE = "removeNotificationsChangeListener"
    }
}
