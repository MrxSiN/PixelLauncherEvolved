package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.Context

import android.os.SystemClock

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Makes a running launcher draw the icons it now wants.
 *
 * ```
 * com.android.launcher3.LauncherAppState.INSTANCE      DaggerSingletonObject
 *   .model.forceReload(String)                        LauncherModel
 * com.android.launcher3.util.Executors.MODEL_EXECUTOR  LooperExecutor
 * ```
 *
 * Nothing is deleted. The launcher keys each stored icon by a freshness
 * identifier it asks for per app, this feature adds the active pack and its own
 * generation to that identifier, and a reload makes the launcher compare the two
 * and regenerate what disagrees. So the launcher's icon database is read and
 * rewritten by the launcher itself, an app whose icon did not change is not
 * touched, and removing this module leaves nothing to undo.
 *
 * The launcher's in-memory icon cache is left alone. Emptying it would turn
 * every icon on screen into a placeholder until regenerated; left alone, the
 * reload's own freshness pass replaces exactly the stale entries, on screen
 * as it goes.
 *
 * The reload runs on the launcher's model thread, where its own icon work runs.
 */
internal class IconReloader(private val context: Context, private val logger: Logger) {

    @Volatile
    private var parts: Parts? = null

    /** Reloads the model, on the model thread. */
    /** [done], when given, completes once the launcher has reloaded, or could not. */
    fun reload(reason: String, asked: Long, done: CompletableFuture<Unit>?) {
        val resolved = resolve() ?: run {
            logger.warn("Icons: this launcher build cannot be reloaded; icons change on the next launcher start")
            done?.complete(Unit)
            return
        }

        resolved.modelThread.execute {
            try {
                val stage = resolved.forceReload.invoke(resolved.model, RELOAD_REASON) as? CompletionStage<*>
                if (stage == null) done?.complete(Unit)
                stage?.whenComplete { _, _ ->
                    logger.info("Icons: launcher reloaded for $reason, ${SystemClock.elapsedRealtime() - asked} ms after it was asked for")
                    done?.complete(Unit)
                }
            } catch (error: Throwable) {
                logger.warn("Icons: the launcher could not be reloaded for $reason", error)
                done?.complete(Unit)
            }
        }
    }

    private fun resolve(): Parts? {
        parts?.let { return it }

        val found = runCatching { build() }
            .onFailure { logger.warn("Icons: the launcher's model is unreachable", it) }
            .getOrNull()
        parts = found
        return found
    }

    private fun build(): Parts {
        val loader = context.classLoader
        val appStateClass = Host.clsOrThrow(loader, APP_STATE)
        val singleton = requireNotNull(Reflect.field(appStateClass, INSTANCE)).get(null)
        val appState = requireNotNull(
            Reflect.method(Host.clsOrThrow(loader, SINGLETON), GET, Context::class.java),
        ).invoke(singleton, context)

        val model = requireNotNull(Reflect.field(appStateClass, MODEL)).get(appState)!!
        val executor = requireNotNull(Reflect.field(Host.clsOrThrow(loader, EXECUTORS), MODEL_EXECUTOR))
            .get(null) as Executor
        val reload = requireNotNull(Reflect.method(model.javaClass, FORCE_RELOAD, String::class.java))

        return Parts(model, reload, executor)
    }

    private class Parts(
        val model: Any,
        val forceReload: java.lang.reflect.Method,
        val modelThread: Executor,
    )

    private companion object {
        const val APP_STATE = "com.android.launcher3.LauncherAppState"
        const val SINGLETON = "com.android.launcher3.util.DaggerSingletonObject"
        const val EXECUTORS = "com.android.launcher3.util.Executors"
        const val INSTANCE = "INSTANCE"
        const val GET = "get"
        const val MODEL = "model"
        const val MODEL_EXECUTOR = "MODEL_EXECUTOR"
        const val FORCE_RELOAD = "forceReload"

        /** What the launcher logs the reload against. */
        const val RELOAD_REASON = "pixel_launcher_evolved_icons"
    }
}
