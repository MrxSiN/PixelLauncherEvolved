package my.github.MrxSiN.pixellauncherevolved.layout

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.UserManager
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

import my.github.MrxSiN.pixellauncherevolved.bridge.GridBridge
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.grid.GridSpec

/**
 * Copies of the Home screen layout from which it can be put back.
 *
 * A custom grid, a hidden dock's extra row and more dock icons than the grid's
 * own are kept only while this module runs. Without it, Pixel Launcher deletes
 * whatever lies outside its own grid, so before the layout first comes to
 * depend on the module, and at each launcher start while it does, the layout
 * database is copied here with what the launcher and this module need to read it
 * as it was:
 *
 * - [Slot.BEFORE]: the last layout that did not depend on the module, taken
 *   only when it is about to. Restoring it is the way back to stock.
 * - [Slot.LATEST]: the layout as the module last saw it start.
 *
 * Two copies at most, in the launcher's `no_backup` folder, each a few hundred KB.
 * Nothing is copied while the layout does not depend on the module.
 *
 * A copy is put back only when asked ([requestRestore]), at the next launcher
 * start and before the launcher opens its database. Widgets come back where they
 * were, but a widget whose id the launcher released in the meantime cannot be
 * bound again and has to be added anew; nothing here can undo that.
 */
object LayoutCheckpoints : SharedPreferences.OnSharedPreferenceChangeListener {

    enum class Slot(val file: String) { BEFORE("before"), LATEST("latest") }

    /** Whether the layout now depends on the module, as of the last check. */
    @Volatile
    private var dependent = false

    /** Set by [install]; read by [onSharedPreferenceChanged]. */
    @Volatile
    private var launcher: Context? = null

    @Volatile
    private var logger: Logger? = null

    /**
     * At launcher start, before any feature: puts back a copy that was asked for,
     * then copies the layout again if it depends on the module, and watches the
     * dock settings that would make it.
     */
    fun install(launcher: Context, settings: SharedPreferences, logger: Logger) {
        if (!launcher.getSystemService(UserManager::class.java).isUserUnlocked) return
        runCatching { restoreIfRequested(launcher, settings, logger) }
            .onFailure { logger.warn("Layout: the copy asked for could not be put back; the layout is as it was", it) }

        dependent = dependsOnModule(liveDb(launcher), settings.getBoolean(Settings.DOCK_HIDDEN.key, false), settings.getInt(Settings.DOCK_ICONS.key, 0))
        // Copied while the launcher loads its first screen, so behind it rather than beside it.
        if (dependent) {
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                take(launcher, settings, Slot.LATEST, logger)
            }, "PixelLauncherEvolved-layout").start()
        }

        this.launcher = launcher
        this.logger = logger
        // This object is the listener, not a lambda in a field: preferences hold a listener
        // only weakly, and a field written but never read is removed by the shrinker.
        settings.registerOnSharedPreferenceChangeListener(this)
    }

    /** The dock settings are the ones that bring the layout to depend on the module from Settings. */
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key != Settings.DOCK_HIDDEN.key && key != Settings.DOCK_ICONS.key) return
        val launcher = launcher ?: return
        val logger = logger ?: return
        val now = dependsOnModule(liveDb(launcher), preferences.getBoolean(Settings.DOCK_HIDDEN.key, false), preferences.getInt(Settings.DOCK_ICONS.key, 0))
        if (now && !dependent) take(launcher, preferences, Slot.BEFORE, logger)
        dependent = now
    }

    /**
     * Called by the launcher's own grid migration, before it runs: a move from
     * one of Google's grids to one of this module's is the moment the layout
     * comes to depend on it. [from] is the database being left, which the
     * migration only reads.
     */
    fun beforeMigration(launcher: Context, settings: SharedPreferences, from: String?, to: String?, logger: Logger) {
        if (dependent || GridSpec.isOwnDb(from) || !GridSpec.isOwnDb(to)) return
        take(launcher, settings, Slot.BEFORE, logger, from)
        dependent = true
    }

    /** Asks for [slot] to be put back at the next launcher start. */
    fun requestRestore(launcher: Context, slot: Slot) {
        File(folder(launcher), RESTORE).writeText(slot.file)
    }

    /** Whether the layout depends on the module, from what makes it so. */
    fun dependsOnModule(liveDb: String?, dockHidden: Boolean, dockIcons: Int): Boolean =
        GridSpec.isOwnDb(liveDb) || dockHidden || dockIcons != 0

    // ---- Taking a copy ----------------------------------------------------------------

    @Synchronized
    private fun take(launcher: Context, settings: SharedPreferences, slot: Slot, logger: Logger, db: String? = liveDb(launcher)) {
        db ?: return
        runCatching {
            val folder = folder(launcher)
            val copy = File(folder, "${slot.file}.db")
            val partial = File(folder, "${slot.file}.db.partial")
            partial.delete()
            // One consistent copy, even while the launcher writes: SQLite's own.
            SQLiteDatabase.openDatabase(launcher.getDatabasePath(db).path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.execSQL("VACUUM INTO ?", arrayOf(partial.path))
            }
            val items = items(partial)
            var launcherValues = launcher.getSharedPreferences(LAUNCHER_PREFS, Context.MODE_PRIVATE).all.filterKeys(::isLauncherKey)
            var moduleValues = settings.all.filterKeys(::isModuleKey)
            if (slot == Slot.BEFORE) {
                // Taken as the change that makes the layout depend on the module is made, so the
                // settings already say it. The copy is the layout before it: stock values for those.
                moduleValues = moduleValues + STOCK_SAFE
                // ponytail: the Google grid left for a custom one is not named anywhere once it is
                // left; without a name the launcher takes its default grid and migrates to it if it differs.
                if (launcherValues[GRID_NAME] == GridBridge.CUSTOM) launcherValues = launcherValues - GRID_NAME
            }
            val manifest = Manifest(db = db, items = items, launcher = launcherValues, module = moduleValues)
            check(partial.renameTo(copy)) { "the copy could not be kept" }
            File(folder, "${slot.file}.properties").writeText(manifest.encode())
            logger.info("Layout: kept a copy of $db ($items items) as ${slot.file}")
        }.onFailure { logger.warn("Layout: unable to keep a copy of $db", it) }
    }

    // ---- Putting one back -------------------------------------------------------------

    private fun restoreIfRequested(launcher: Context, settings: SharedPreferences, logger: Logger) {
        // Looked for without making the folder: nothing is written unless a copy is.
        val request = File(File(launcher.noBackupFilesDir, FOLDER), RESTORE)
        if (!request.exists()) return
        val slot = Slot.entries.firstOrNull { it.file == request.readText().trim() }
        // Gone before anything else, so a restore that brings the launcher down is not tried again.
        request.delete()
        slot ?: return logger.warn("Layout: a restore was asked for with no copy named")

        val folder = folder(launcher)
        val copy = File(folder, "${slot.file}.db")
        val manifest = File(folder, "${slot.file}.properties").takeIf(File::exists)?.readText()?.let(Manifest::decode)
        if (!copy.exists() || manifest == null) return logger.warn("Layout: there is no ${slot.file} copy to put back")

        val target = launcher.getDatabasePath(manifest.db)
        val staged = File(target.path + ".ple-restore")
        copy.copyTo(staged, overwrite = true)
        val items = items(staged)
        if (items != manifest.items) {
            staged.delete()
            return logger.warn("Layout: the ${slot.file} copy holds $items items, not ${manifest.items}; nothing was put back")
        }

        // What it replaces is kept, so putting a copy back can itself be undone by hand.
        if (target.exists()) target.copyTo(File(folder, "replaced.db"), overwrite = true)
        File(target.path + "-journal").delete()
        check(staged.renameTo(target)) { "the copy could not take the database's place" }

        write(launcher.getSharedPreferences(LAUNCHER_PREFS, Context.MODE_PRIVATE), manifest.launcher, ::isLauncherKey)
        write(settings, manifest.module, ::isModuleKey)
        logger.info("Layout: put back the ${slot.file} copy of ${manifest.db} ($items items)")
    }

    /** Makes [preferences]' [owned] keys exactly [values]: written, or removed when absent. */
    private fun write(preferences: SharedPreferences, values: Map<String, *>, owned: (String) -> Boolean) {
        val editor = preferences.edit()
        preferences.all.keys.filter(owned).filterNot(values::containsKey).forEach(editor::remove)
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        check(editor.commit()) { "the settings could not be written" }
    }

    // ---- Shared -----------------------------------------------------------------------

    /** Home screen, dock and folder items, and an integrity check that must pass. */
    private fun items(db: File): Int = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use {
        it.rawQuery("PRAGMA integrity_check", null).use { check ->
            check(check.moveToFirst() && check.getString(0) == "ok") { "${db.name} is damaged" }
        }
        it.rawQuery(ITEMS_QUERY, null).use { count -> if (count.moveToFirst()) count.getInt(0) else 0 }
    }

    private fun liveDb(launcher: Context): String? =
        launcher.getSharedPreferences(LAUNCHER_PREFS, Context.MODE_PRIVATE).getString(SRC_DB_FILE, null)

    private fun folder(launcher: Context): File = File(launcher.noBackupFilesDir, FOLDER).apply { mkdirs() }

    private fun isLauncherKey(key: String) = key == GRID_NAME || key.startsWith(MIGRATION_PREFIX)

    private fun isModuleKey(key: String) = key in MODULE_KEYS

    private val MODULE_KEYS: Set<String> = (Settings.GRID + listOf(Settings.DOCK_ICONS, Settings.DOCK_KEPT)).map { it.key }.toSet() +
        listOf(Settings.DOCK_HIDDEN, Settings.DOCK_MOVE_TO_HOME).map { it.key }

    /** The settings that make the layout depend on the module, at the values that do not. */
    private val STOCK_SAFE: Map<String, Any> = mapOf(
        Settings.GRID_COLUMNS.key to Settings.GRID_COLUMNS.default,
        Settings.GRID_ROWS.key to Settings.GRID_ROWS.default,
        Settings.DOCK_HIDDEN.key to Settings.DOCK_HIDDEN.default,
        Settings.DOCK_ICONS.key to Settings.DOCK_ICONS.default,
        Settings.DOCK_KEPT.key to Settings.DOCK_KEPT.default,
    )

    private const val FOLDER = "ple_layout"
    private const val RESTORE = "restore"
    private const val LAUNCHER_PREFS = "com.android.launcher3.prefs"
    private const val SRC_DB_FILE = "migration_src_db_file"
    private const val GRID_NAME = "idp_grid_name"
    private const val MIGRATION_PREFIX = "migration_src_"
    private const val ITEMS_QUERY = "SELECT COUNT(*) FROM favorites WHERE container IN (-100, -101) OR container >= 0"
}

/**
 * What a copy is: which database it is, how many items it holds, and the
 * launcher's and this module's settings that say how to read it. Each value
 * keeps its type, as one letter before it.
 */
internal class Manifest(
    val db: String,
    val items: Int,
    val launcher: Map<String, *>,
    val module: Map<String, *>,
) {

    fun encode(): String = StringWriter().also { out ->
        Properties().apply {
            setProperty(DB, db)
            setProperty(ITEMS, items.toString())
            launcher.forEach { (key, value) -> typed(value)?.let { setProperty(LAUNCHER + key, it) } }
            module.forEach { (key, value) -> typed(value)?.let { setProperty(MODULE + key, it) } }
        }.store(out, null)
    }.toString()

    companion object {
        private const val DB = "db"
        private const val ITEMS = "items"
        private const val LAUNCHER = "launcher."
        private const val MODULE = "module."

        fun decode(text: String): Manifest? {
            val properties = Properties().apply { load(StringReader(text)) }
            val db = properties.getProperty(DB)?.takeIf { it.endsWith(".db") && '/' !in it } ?: return null
            val items = properties.getProperty(ITEMS)?.toIntOrNull() ?: return null
            fun under(prefix: String) = properties.stringPropertyNames().filter { it.startsWith(prefix) }
                .mapNotNull { key -> value(properties.getProperty(key))?.let { key.removePrefix(prefix) to it } }.toMap()
            return Manifest(db, items, under(LAUNCHER), under(MODULE))
        }

        private fun typed(value: Any?): String? = when (value) {
            is String -> "s$value"
            is Int -> "i$value"
            is Long -> "l$value"
            is Boolean -> "b$value"
            is Float -> "f$value"
            else -> null
        }

        private fun value(text: String): Any? {
            val raw = text.drop(1)
            return when (text.firstOrNull()) {
                's' -> raw
                'i' -> raw.toIntOrNull()
                'l' -> raw.toLongOrNull()
                'b' -> raw.toBooleanStrictOrNull()
                'f' -> raw.toFloatOrNull()
                else -> null
            }
        }
    }
}
