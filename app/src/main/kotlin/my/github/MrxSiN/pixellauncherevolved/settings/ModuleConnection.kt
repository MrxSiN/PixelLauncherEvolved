package my.github.MrxSiN.pixellauncherevolved.settings

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's link to the Xposed framework.
 *
 * The framework hands its binder to the app through a content provider during
 * startup, before anything on screen exists, so the listener is registered once
 * for the whole process and the result is published as state. A framework that
 * is not installed simply never answers, which is why callers treat silence as
 * "not active" rather than waiting forever.
 */
object ModuleConnection {

    private val _service = MutableStateFlow<XposedService?>(null)

    /** Null until a framework answers, and again if it dies. */
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    private var registered = false

    /** Safe to call more than once; the framework only accepts one listener. */
    @Synchronized
    fun register() {
        if (registered) return
        registered = true

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                _service.value = service
            }

            override fun onServiceDied(service: XposedService) {
                _service.value = null
            }
        })
    }
}
