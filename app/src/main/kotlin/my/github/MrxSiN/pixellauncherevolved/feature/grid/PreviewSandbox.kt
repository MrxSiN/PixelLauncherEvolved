package my.github.MrxSiN.pixellauncherevolved.feature.grid

import android.content.Context
import android.database.sqlite.SQLiteDatabase

import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Keeps Wallpaper & style's previews off the launcher's grid databases.
 *
 * ```
 * com.android.launcher3.preview.PreviewContext
 *   File mDbDir                                  // set: every database of the preview lives there
 *   public File getDatabasePath(String)
 *   void cleanUpObjects()                        // deletes mDbDir when the preview ends
 * com.android.launcher3.model.DeviceGridState(Context)   // the grid a context's model was last on
 * ```
 *
 * Stock previews open the launcher's own database files. A preview of
 * another grid migrates into that grid's file, the Home screen's included,
 * from whichever grid was previewed before. The launcher gives a preview a
 * folder of its own only when it shows a layout file. Here every preview gets
 * one, and each database is copied in the first time the preview opens it, so
 * a preview reads the person's layout and writes only its copies.
 *
 * Each preview migration also starts from a fresh copy of the Home screen's
 * own database, never from the grid previewed last, so a preview shows what
 * applying that grid gives ([source]). Everything here runs when a preview
 * opens or changes grid, never on a launcher frame.
 */
internal class PreviewSandbox(private val feature: FeatureContext, state: Class<*>, private val stateDbOf: Field) {

    private val app: Context = feature.appContext
    private val root = File(app.cacheDir, ROOT)
    private val newState: Constructor<*> = state.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
    private val type: Class<*> = requireNotNull(feature.findClass(PREVIEW_CONTEXT))
    private val dirOf: Field = Reflect.declaredField(type, "mDbDir")

    fun install() {
        // Left over from a preview the launcher did not get to clean up.
        root.deleteRecursively()
        feature.xposed.hook(Reflect.declaredMethod(type, "getDatabasePath", String::class.java)).intercept { chain ->
            val preview = chain.thisObject
            // A preview of a layout file already has a folder of its own, and must stay empty.
            if (dirOf.get(preview) == null) dirOf.set(preview, File(root, Integer.toHexString(System.identityHashCode(preview))).apply { mkdirs() })
            val file = chain.proceed() as File
            if (file.parentFile?.parentFile == root && !file.exists()) {
                runCatching { snapshot(app.getDatabasePath(chain.args[0] as String), file) }
                    .onFailure { feature.logger.warn("Grid & size: unable to copy ${file.name} into a preview", it) }
            }
            file
        }
    }

    /**
     * The Home screen's grid and a copy of its database, opened read only,
     * for a migration in [preview]; null for a context this does not keep.
     * The copy is named apart from every grid's file, so a preview of the
     * Home screen's own grid still copies it across rather than skipping it.
     */
    fun source(preview: Any): Source? {
        val dir = dirOf.get(preview) as? File ?: return null
        if (dir.parentFile != root) return null
        val state = newState.newInstance(app)
        val copy = File(dir, SOURCE)
        copy.delete()
        if (!snapshot(app.getDatabasePath(stateDbOf.get(state) as? String ?: return null), copy)) return null
        stateDbOf.set(state, SOURCE)
        return Source(state, SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY))
    }

    class Source(val state: Any, val db: SQLiteDatabase)

    /**
     * A consistent copy of [from] at [to], taken under SQLite's own read lock
     * even while the launcher writes; false when there is no [from].
     */
    private fun snapshot(from: File, to: File): Boolean {
        if (!from.exists()) return false
        SQLiteDatabase.openDatabase(from.path, null, SQLiteDatabase.OPEN_READONLY).use { it.execSQL("VACUUM INTO ?", arrayOf(to.path)) }
        return true
    }

    private companion object {
        const val PREVIEW_CONTEXT = "com.android.launcher3.preview.PreviewContext"
        const val ROOT = "ple_previews"
        const val SOURCE = "ple_preview_source.db"
    }
}
