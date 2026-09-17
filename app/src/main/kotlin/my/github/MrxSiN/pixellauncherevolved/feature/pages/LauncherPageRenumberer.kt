package my.github.MrxSiN.pixellauncherevolved.feature.pages

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase

import java.util.concurrent.Executor

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Gives home screen pages new ids in the launcher's own database.
 *
 * ```
 * com.android.launcher3.LauncherAppState.INSTANCE     DaggerSingletonObject
 *   .model                                           LauncherModel
 *     .modelDbController.getOpenHelper()             DatabaseHelper (SQLiteOpenHelper)
 *     .forceReload(String)
 * com.android.launcher3.util.Executors.MODEL_EXECUTOR LooperExecutor
 * ```
 *
 * One statement renumbers every moved page at once, so no two pages ever share
 * an id part way through. It runs on the launcher's model thread, which is the
 * thread every other write to that database runs on, and the model is reloaded
 * straight after, so the launcher never works from its old ids.
 *
 * Only items sitting on the workspace carry a page; an icon in a folder names
 * the folder, and one in the hotseat names the hotseat.
 */
internal class LauncherPageRenumberer(
    private val context: Context,
    private val logger: Logger,
) {

    /**
     * Renumbers the pages in [mapping], then runs [onRenumbered] on the main
     * thread before the launcher reloads, so whatever else is kept by page id
     * follows first.
     */
    fun renumber(mapping: Map<Int, Int>, onRenumbered: () -> Unit) {
        if (mapping.isEmpty()) return
        val launcher = runCatching { launcher() }
            .onFailure { logger.warn("The launcher's database is unreachable; the page order is unchanged", it) }
            .getOrNull() ?: return

        launcher.modelThread.execute {
            val written = runCatching { write(launcher.database, mapping) }
                .onFailure { logger.warn("The new page order could not be written", it) }
                .isSuccess
            if (!written) return@execute

            context.mainExecutor.execute {
                onRenumbered()
                runCatching { launcher.reload() }
                    .onFailure { logger.warn("The launcher could not reload the new page order", it) }
                logger.info("Home screen pages reordered: $mapping")
            }
        }
    }

    private fun write(database: SQLiteDatabase, mapping: Map<Int, Int>) {
        val cases = mapping.keys.joinToString(" ") { "WHEN ? THEN ?" }
        val moved = mapping.keys.joinToString(",") { "?" }
        val arguments = mapping.flatMap { (old, new) -> listOf(old, new) } + mapping.keys

        database.beginTransaction()
        try {
            database.execSQL(
                "UPDATE favorites SET screen = CASE screen $cases END " +
                    "WHERE container = $CONTAINER_DESKTOP AND screen IN ($moved)",
                arguments.toTypedArray(),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun launcher(): Launcher {
        val loader = context.classLoader
        val appStateClass = Class.forName(APP_STATE, false, loader)
        val singleton = requireNotNull(Reflect.field(appStateClass, INSTANCE)).get(null)
        val appState = requireNotNull(
            Reflect.method(Class.forName(SINGLETON, false, loader), GET, Context::class.java)
        ).invoke(singleton, context)
        val model = requireNotNull(Reflect.field(appStateClass, MODEL)).get(appState)!!
        val controller = requireNotNull(Reflect.field(model.javaClass, DB_CONTROLLER)).get(model)!!
        val helper = requireNotNull(Reflect.method(controller.javaClass, OPEN_HELPER)).invoke(controller) as SQLiteOpenHelper
        val executor = requireNotNull(Reflect.field(Class.forName(EXECUTORS, false, loader), MODEL_EXECUTOR))
            .get(null) as Executor
        val reload = requireNotNull(Reflect.method(model.javaClass, FORCE_RELOAD, String::class.java))

        return Launcher(
            database = helper.writableDatabase,
            modelThread = executor,
            reload = { reload.invoke(model, RELOAD_REASON) },
        )
    }

    private class Launcher(
        val database: SQLiteDatabase,
        val modelThread: Executor,
        val reload: () -> Unit,
    )

    private companion object {
        const val APP_STATE = "com.android.launcher3.LauncherAppState"
        const val SINGLETON = "com.android.launcher3.util.DaggerSingletonObject"
        const val EXECUTORS = "com.android.launcher3.util.Executors"
        const val INSTANCE = "INSTANCE"
        const val GET = "get"
        const val MODEL = "model"
        const val DB_CONTROLLER = "modelDbController"
        const val OPEN_HELPER = "getOpenHelper"
        const val MODEL_EXECUTOR = "MODEL_EXECUTOR"
        const val FORCE_RELOAD = "forceReload"
        const val RELOAD_REASON = "pixel_launcher_evolved_page_order"

        /** `LauncherSettings.Favorites.CONTAINER_DESKTOP`, inlined by the launcher's compiler. */
        const val CONTAINER_DESKTOP = -100
    }
}
