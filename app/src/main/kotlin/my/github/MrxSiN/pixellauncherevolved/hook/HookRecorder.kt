package my.github.MrxSiN.pixellauncherevolved.hook

import java.lang.reflect.Executable

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker

/**
 * The framework as features see it, keeping the hooks one feature sets while it
 * installs, so a feature that fails halfway takes back what it had set rather
 * than leaving half of itself running.
 *
 * Only the setting up is watched. The hooks themselves are the framework's own,
 * untouched, so nothing here runs when a hooked method is called; an exception a
 * hook throws then is already caught and logged by the framework (libxposed's
 * default `PROTECTIVE` mode), and the launcher carries on without that hook.
 */
class HookRecorder(private val base: XposedInterface) : XposedInterface by base {

    /** What one install had set, taken back. */
    class Failure(val error: Throwable, val undone: Int)

    private var recording: MutableList<HookHandle>? = null

    /**
     * Runs [install], and when it throws, unhooks every hook it set.
     * Null when it installed. Installs run one at a time, at launcher start.
     */
    fun install(install: () -> Unit): Failure? {
        val handles = ArrayList<HookHandle>()
        recording = handles
        return try {
            install()
            null
        } catch (error: Throwable) {
            // libxposed: unhook is idempotent and safe to call more than once.
            handles.forEach { runCatching(it::unhook) }
            Failure(error, handles.size)
        } finally {
            recording = null
        }
    }

    override fun hook(origin: Executable): HookBuilder = Recorded(base.hook(origin))

    override fun hookClassInitializer(origin: Class<*>): HookBuilder = Recorded(base.hookClassInitializer(origin))

    private inner class Recorded(private val builder: HookBuilder) : HookBuilder {
        override fun setPriority(priority: Int): HookBuilder = apply { builder.setPriority(priority) }

        override fun setExceptionMode(mode: ExceptionMode): HookBuilder = apply { builder.setExceptionMode(mode) }

        override fun setId(id: String?): HookBuilder = apply { builder.setId(id) }

        override fun intercept(hooker: Hooker): HookHandle = builder.intercept(hooker).also { recording?.add(it) }
    }
}
