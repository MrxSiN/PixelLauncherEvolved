package my.github.MrxSiN.pixellauncherevolved.feature.grid

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.GridBridge
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.layout.LiveLayout
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Grid & size: more Home screen columns and rows, the icon size, the spacing
 * between cells, and the columns in All apps.
 *
 * Nothing is drawn or scaled after layout. Each value goes in where the
 * launcher reads its own, before any profile is measured, so the launcher's own
 * math sizes the cells, icons, labels, dots, folders, drag outlines and widgets:
 *
 * - **Columns and rows** are written into the grid option the launcher picks
 *   (`DisplayOption.parseWeightedPredefinedDisplayOption`, parsed afresh on every
 *   call) together with a database of this module's own
 *   ([GridSpec.dbFile]). A new size is then a new database to the launcher, and
 *   its own grid migration (`GridSizeMigrationLogic.migrateGrid`) copies the
 *   layout over, leaving the previous database untouched as the way back.
 * - **Icon size** scales the icon size of every responsive cell spec as it is
 *   parsed (`ResponsiveCellSpecsProvider.Companion.create`). All apps matches the
 *   Home screen's, and the launcher still shrinks an icon to fit its cell.
 * - **Spacing** scales the gutters of the Home screen's responsive spec
 *   (`ResponsiveSpecsProvider.Companion.create`, type `Workspace`) and takes
 *   what they gain from its outer padding ([GridSpec.spread]), so the cells
 *   keep their size and the icons move apart or together.
 * - **Pages** stay as they were through a grid change: the launcher's
 *   placement (`GridSizeMigrationLogic.solveGridPlacement`) is replaced by
 *   [GridSpec.placeOnPage], which keeps every item on its page and moves what
 *   a smaller grid has no room for onto new pages after the last.
 * - **All apps columns** are written into each profile's copy of the grid spec
 *   (`DisplayOptionSpec.mapTypeIndex`) and the model's count after `initGrid`.
 *
 * Every change goes through `InvariantDeviceProfile.onConfigChanged`, the path a
 * grid picked in Wallpaper & style takes. Each part stands on its own contracts
 * and is installed alone. Every hook here runs once per grid parse, spec parse,
 * profile build or migration, never per frame, and does nothing more than read
 * a setting while everything is at its default.
 */
class GridFeature : LauncherFeature, SharedPreferences.OnSharedPreferenceChangeListener {

    override val id: String = "home_grid"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    private var context: FeatureContext? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var pending = false

    /** The columns and rows the last grid parsed on this thread was given. */
    private val applied = ThreadLocal<IntArray>()

    private val rebuild = Runnable {
        pending = false
        val context = context ?: return@Runnable
        runCatching { LiveLayout.rebuild(context) }
            .onFailure { context.logger.warn("Unable to apply Grid & size live; it applies on the next launcher start", it) }
    }

    override fun install(context: FeatureContext) {
        this.context = context
        part(context, CompatibilityFeature.GRID, "grid") { installGrid(it) }
        part(context, CompatibilityFeature.GRID_ICON_SIZE, "icon size") { installIconSize(it) }
        part(context, CompatibilityFeature.GRID_SPACING, "spacing") { installSpacing(it) }
        part(context, CompatibilityFeature.APP_DRAWER_COLUMNS, "All apps columns") { installAppsColumns(it) }
        LauncherSettings.preferences(context.appContext).registerOnSharedPreferenceChangeListener(this)
        context.logger.info("Grid & size: ${Settings.GRID.joinToString { "${it.key}=${context.settings[it]}" }}")
    }

    private inline fun part(context: FeatureContext, needs: CompatibilityFeature, name: String, install: (FeatureContext) -> Unit) {
        if (!context.analysis.isAvailable(needs)) {
            context.logger.warn("Grid & size: $name is unavailable on Pixel Launcher ${context.analysis.launcherVersion}")
            return
        }
        runCatching { install(context) }.onFailure { context.logger.warn("Grid & size: $name failed to install", it) }
    }

    /** Settings write several keys for one choice (Restore defaults, an import); they are applied once. */
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == null || pending || Settings.GRID.none { it.key == key }) return
        // Columns and rows only shape the custom grid; under any other, only a preview shows them.
        if (!Grid.custom && (key == Settings.GRID_COLUMNS.key || key == Settings.GRID_ROWS.key)) return
        pending = true
        main.post(rebuild)
    }

    // ---- Columns and rows -------------------------------------------------------------------

    private fun installGrid(context: FeatureContext) {
        rememberOwnSource(context.appContext)

        val option = requireNotNull(context.findClass(DISPLAY_OPTION))
        val gridOption = requireNotNull(context.findClass(GRID_OPTION))
        val parse = requireNotNull(Reflect.declared(option, "parseWeightedPredefinedDisplayOption") { it.parameterTypes.size == 4 })
        val gridOf = Reflect.declaredField(option, "grid")
        val columnsOf = Reflect.declaredField(gridOption, "numColumns")
        val rowsOf = Reflect.declaredField(gridOption, "numRows")
        val searchOf = Reflect.declaredField(gridOption, "numSearchContainerColumns")
        val dbOf = Reflect.declaredField(gridOption, "dbFile")
        val nameOf = Reflect.declaredField(gridOption, "name")
        val landscapeOf = Reflect.declaredField(gridOption, "isFixedLandscape")
        val dualOf = Reflect.field(gridOption, "mIsDualGrid")
        val specOf = Reflect.declaredField(gridOption, "displayOptionSpec")
        val appsOf = Reflect.field(specOf.type, "numAllAppsColumns")
        var deviceTypeOf: Method? = null

        context.xposed.hook(parse).intercept { chain ->
            // The custom grid is the launcher's own Google grid with other counts: parsed under
            // that one's name, its icons and spacing are the launcher's, not those of whichever
            // grid lies closest to what was shown before (Large's, after previewing Large).
            val base = Grid.stockName
            val parsed = if (chain.args[1] == GridBridge.CUSTOM && base != null) {
                chain.proceed(chain.args.toTypedArray().also { it[1] = base })
            } else {
                chain.proceed()
            }
            parsed?.also { result ->
                runCatching {
                    // initGrid names a grid; a secondary display's profile (no name) keeps the launcher's.
                    if (chain.args[1] == null) return@runCatching
                    val grid = gridOf.get(result) ?: return@runCatching
                    val info = chain.args[0] ?: return@runCatching
                    val typeOf = deviceTypeOf ?: Reflect.method(info.javaClass, "getDeviceType").also { deviceTypeOf = it }
                    val support = when {
                        typeOf?.invoke(info) != PHONE || dualOf?.getBoolean(grid) == true -> Grid.Support.OTHER
                        landscapeOf.getBoolean(grid) -> Grid.Support.LANDSCAPE
                        else -> Grid.Support.PHONE
                    }
                    // Only the launcher's own grid is recorded; a preview's is parsed the same way.
                    val own = Grid.ownInit.get() != false
                    val columns = columnsOf.getInt(grid)
                    val rows = rowsOf.getInt(grid)
                    // Renamed, so initGrid keeps the custom grid as the launcher's grid name.
                    val custom = chain.args[1] == GridBridge.CUSTOM
                    if (custom) nameOf.set(grid, GridBridge.CUSTOM)
                    if (own) {
                        Grid.support = support
                        Grid.custom = custom
                    }
                    if (support != Grid.Support.PHONE) return@runCatching

                    // The launcher's own grid is the one System means. The grid picked for the custom
                    // one is only the closest to whatever was shown before, a preview's included.
                    if (own && (!custom || Grid.stockColumns == 0)) {
                        Grid.stockColumns = columns
                        Grid.stockRows = rows
                        if (!custom) Grid.stockName = chain.args[1] as? String
                        specOf.get(grid)?.let { spec -> appsOf?.let { Grid.stockAppsColumns = it.getInt(spec) } }
                    }
                    val wanted = if (custom) Grid.wanted(context.settings, Grid.stockColumns.takeIf { it > 0 } ?: columns, Grid.stockRows.takeIf { it > 0 } ?: rows) else android.graphics.Point(columns, rows)
                    applied.set(intArrayOf(wanted.x, wanted.y))
                    if (own) {
                        Grid.appliedColumns = wanted.x
                        Grid.appliedRows = wanted.y
                    }
                    if (wanted.x == columns && wanted.y == rows) return@runCatching

                    Grid.addOwnSize(wanted.x, wanted.y)
                    columnsOf.setInt(grid, wanted.x)
                    rowsOf.setInt(grid, wanted.y)
                    if (searchOf.getInt(grid) == columns) searchOf.setInt(grid, wanted.x)
                    dbOf.set(grid, GridSpec.dbFile(wanted.x, wanted.y))
                }.onFailure { context.logger.warn("Grid & size: unable to set the grid", it) }
            }
        }

        // Which profile a parse belongs to: the launcher's own, or a preview's.
        val idp = requireNotNull(context.findClass(IDP))
        context.xposed.hook(Reflect.declaredMethod(idp, "initGrid", String::class.java)).intercept { chain ->
            Grid.ownInit.set(!Grid.isPreview(chain.thisObject))
            try {
                chain.proceed()
            } finally {
                Grid.ownInit.remove()
            }
        }

        installMigrationGate(context)
        installMigrationCheck(context)
        installPlacement(context)
        GridPickerBridge(context).install()
    }

    /**
     * Lets the launcher migrate to and from this module's sizes.
     *
     * `ModelDbController.attemptMigrateDb` migrates only between the sizes
     * `GridMigrationOption.Companion.from(columns, rows)` names, Google's own; any
     * other size answers null and the launcher then loads the old database under
     * the new grid, dropping what lies outside it. For this module's sizes
     * [GridSpec] answers a stand-in, and `canMigrate` agrees for that one call.
     */
    private fun installMigrationGate(context: FeatureContext) {
        val companion = requireNotNull(context.findClass(MIGRATION_OPTION_COMPANION))
        val option = requireNotNull(context.findClass(MIGRATION_OPTION))
        val from = Reflect.declaredMethod(companion, "from", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        val canMigrate = requireNotNull(Reflect.declared(option, "canMigrate") { it.parameterTypes.size == 2 })
        val standIn = requireNotNull(context.findClass(MIGRATION_OPTION_STAND_IN)).let { Reflect.declaredField(it, "INSTANCE").get(null) }
        val asked = ThreadLocal<Boolean>()

        context.xposed.hook(from).intercept { chain ->
            val result = chain.proceed()
            val columns = chain.args[0] as Int
            val rows = chain.args[1] as Int
            if (result == null && Grid.isOwnSize(columns, rows)) {
                asked.set(true)
                standIn
            } else {
                result
            }
        }
        context.xposed.hook(canMigrate).intercept { chain ->
            val result = chain.proceed()
            if (asked.get() == true) {
                // ponytail: a stand-in answered by from() without a canMigrate after it
                // stays set until the next canMigrate on that thread; both run back to back in attemptMigrateDb.
                asked.set(false)
                true
            } else {
                result
            }
        }
    }

    /**
     * Checks every migration into or out of this module's databases, and
     * undoes one that lost anything.
     *
     * Settings already refuses a grid a widget cannot fit in, the one case in
     * which the launcher's migration drops an item. This is the safety net
     * behind it: every app, shortcut, folder, folder item and widget of the old
     * database has to be in the new one. If not, the old database, which a
     * migration never writes to, is made the launcher's again and the columns
     * and rows go back to its size; the launcher then loads it as it was.
     *
     * Going back to one of Google's grids leaves this module's databases
     * unused, so they are deleted once that migration is checked.
     *
     * A Wallpaper & style preview migrates its own copies ([PreviewSandbox]),
     * always from the Home screen's database. The migration itself always
     * runs: its destination is emptied before `migrateGrid` is called.
     */
    private fun installMigrationCheck(context: FeatureContext) {
        val logic = requireNotNull(context.findClass(MIGRATION_LOGIC))
        val state = requireNotNull(context.findClass(GRID_STATE))
        val migrate = requireNotNull(Reflect.declared(logic, "migrateGrid") { it.parameterTypes.size == 5 })
        val dbOf = Reflect.declaredField(state, "mDbFile")
        val writeToPrefs = Reflect.declaredMethod(state, "writeToPrefs", Context::class.java)
        val contextOf = Reflect.declaredField(logic, "context")
        val sandbox = PreviewSandbox(context, state, dbOf).also { it.install() }

        context.xposed.hook(migrate).intercept { chain ->
            val launcher = contextOf.get(chain.thisObject) as Context
            // Only the launcher's own model is checked, rolled back or cleaned up after.
            if (launcher !== context.appContext) {
                val source = runCatching { sandbox.source(launcher) }
                    .onFailure { context.logger.warn("Grid & size: a preview migrates from the grid it showed last", it) }
                    .getOrNull() ?: return@intercept chain.proceed()
                return@intercept try {
                    chain.proceed(chain.args.toTypedArray().also {
                        it[0] = source.state
                        it[3] = source.db
                    })
                } finally {
                    source.db.close()
                }
            }
            val src = chain.args[0]
            val dest = chain.args[1]
            val from = src?.let { dbOf.get(it) as? String }
            val to = dest?.let { dbOf.get(it) as? String }
            if (from == to || (!GridSpec.isOwnDb(from) && !GridSpec.isOwnDb(to))) return@intercept chain.proceed()
            val result = try {
                chain.proceed()
            } catch (error: Throwable) {
                rollBack(context, launcher, src!!, writeToPrefs, from, "the launcher's migration failed")
                throw error
            }
            runCatching {
                val lost = lost(chain.args[3] as SQLiteDatabase, (chain.args[2] as SQLiteOpenHelper).writableDatabase)
                when {
                    lost > 0 -> rollBack(context, launcher, src!!, writeToPrefs, from, "$lost item(s) did not reach $to")
                    !GridSpec.isOwnDb(to) -> deleteOwnDatabases(context, launcher)
                    else -> context.logger.info("Grid & size: migrated $from to $to; every item arrived")
                }
            }.onFailure { context.logger.warn("Grid & size: unable to check the migration from $from to $to", it) }
            result
        }
    }

    /** How many distinct Home screen, dock and folder items of [src] are missing from [dest]. */
    private fun lost(src: SQLiteDatabase, dest: SQLiteDatabase): Int {
        val arrived = keys(dest)
        return keys(src).count { it !in arrived }
    }

    /**
     * Each item as what it is and where it lives: its type, what it opens or
     * shows, and Home screen, dock or folder. Ids and cells are not part of it,
     * because a migration gives every item new ones.
     */
    private fun keys(db: SQLiteDatabase): Set<String> {
        val keys = HashSet<String>()
        db.rawQuery(ITEMS_QUERY, null).use { cursor ->
            while (cursor.moveToNext()) {
                val container = cursor.getInt(3)
                val where = if (container == DESKTOP || container == HOTSEAT) container else FOLDER
                keys += "${cursor.getInt(0)}|${cursor.getString(1)}|${cursor.getString(2)}|$where"
            }
        }
        return keys
    }

    private fun rollBack(context: FeatureContext, launcher: Context, src: Any, writeToPrefs: Method, from: String?, why: String) {
        context.logger.warn("Grid & size: $why; going back to $from")
        runCatching { writeToPrefs.invoke(src, launcher) }
            .onFailure { context.logger.warn("Grid & size: unable to make $from the launcher's database again", it) }
        val size = from?.takeIf(GridSpec::isOwnDb)?.removePrefix(GridSpec.DB_PREFIX)?.removeSuffix(".db")?.split("_by_")
        val columns = size?.getOrNull(0)?.toIntOrNull()
        val rows = size?.getOrNull(1)?.toIntOrNull()
        main.post {
            context.moduleResources?.let { Toast.makeText(launcher, it.getString(R.string.feature_grid_failed_message), Toast.LENGTH_LONG).show() }
            context.settings.put(Settings.GRID_COLUMNS, columns?.let { GridSpec.stored(it, Grid.stockColumns) } ?: GridSpec.SYSTEM)
            context.settings.put(Settings.GRID_ROWS, rows?.let { GridSpec.stored(it, Grid.stockRows) } ?: GridSpec.SYSTEM)
        }
    }

    /**
     * Replaces the launcher's page filling during a grid migration with
     * [GridSpec.placeOnPage], for every migration, the launcher's own and
     * Wallpaper & style's previews alike.
     *
     * `solveGridPlacement(screenId, columns, rows, remaining, onScreen)` is
     * called for each page in turn and answers the items it places there,
     * taking them out of `remaining`; the launcher writes them and moves to
     * the next page until none remain. Items the launcher keeps on a page of
     * its own (`extraItemsProvider`) are kept clear, as it does.
     *
     * A grid's database also remembers the Home screen it had when that grid
     * was last used, and the launcher leaves an item found there at its old
     * cell, on whatever page that was. `getItemsToBeAdded` and
     * `getItemsToBeRemoved` are answered by [GridSpec.remembered]: only an
     * item remembered on its current page keeps its old cell. Previews follow
     * the same rules in their own copies, so they show what applying gives.
     */
    private fun installPlacement(context: FeatureContext) {
        val logic = requireNotNull(context.findClass(MIGRATION_LOGIC))
        installMemory(context, logic)
        val solve = requireNotNull(Reflect.declared(logic, "solveGridPlacement") { it.parameterTypes.size == 5 })
        val toPlace = requireNotNull(solve.returnType.declaredConstructors.firstOrNull { it.parameterTypes.size == 2 }).apply { isAccessible = true }
        val extraOf = Reflect.declaredField(logic, "extraItemsProvider")
        val item = requireNotNull(context.findClass(ITEM_INFO))
        val screenOf = Reflect.declaredField(item, "screenId")
        val containerOf = Reflect.declaredField(item, "container")
        val xOf = Reflect.declaredField(item, "cellX")
        val yOf = Reflect.declaredField(item, "cellY")
        val spanXOf = Reflect.declaredField(item, "spanX")
        val spanYOf = Reflect.declaredField(item, "spanY")
        val minXOf = Reflect.declaredField(item, "minSpanX")
        val minYOf = Reflect.declaredField(item, "minSpanY")

        fun mark(taken: Array<BooleanArray>, entry: Any) {
            val x = xOf.getInt(entry)
            val y = yOf.getInt(entry)
            for (cx in x until x + spanXOf.getInt(entry)) for (cy in y until y + spanYOf.getInt(entry)) {
                if (cx in taken.indices && cy in taken[cx].indices) taken[cx][cy] = true
            }
        }

        context.xposed.hook(solve).intercept { chain ->
            runCatching {
                val screen = chain.args[0] as Int
                val columns = chain.args[1] as Int
                val rows = chain.args[2] as Int
                @Suppress("UNCHECKED_CAST")
                val remaining = chain.args[3] as MutableList<Any?>
                val taken = Array(columns) { BooleanArray(rows) }
                (chain.args[4] as? List<*>)?.forEach { it?.let { entry -> mark(taken, entry) } }
                val extra = extraOf.get(chain.thisObject)?.let { provider -> provider.javaClass.getMethod("get").invoke(provider) } as? Iterable<*>
                extra?.forEach { it?.let { entry -> if (containerOf.getInt(entry) == DESKTOP && screenOf.getInt(entry) == screen) mark(taken, entry) } }

                val entries = remaining.filterNotNull()
                val cells = entries.map {
                    GridSpec.Cell(
                        screenOf.getInt(it), xOf.getInt(it), yOf.getInt(it), spanXOf.getInt(it), spanYOf.getInt(it),
                        minXOf.getInt(it).coerceAtLeast(1), minYOf.getInt(it).coerceAtLeast(1),
                    )
                }
                val placements = GridSpec.placeOnPage(screen, columns, rows, taken, cells)
                val solution = ArrayList<Any>()
                for (index in entries.indices) {
                    val placement = placements[index] ?: continue
                    val entry = entries[index]
                    remaining.remove(entry)
                    if (placement === GridSpec.Placement.DROPPED) continue
                    screenOf.setInt(entry, screen)
                    xOf.setInt(entry, placement.x)
                    yOf.setInt(entry, placement.y)
                    spanXOf.setInt(entry, placement.spanX)
                    spanYOf.setInt(entry, placement.spanY)
                    solution += entry
                }
                toPlace.newInstance(remaining, solution)
            }.getOrElse {
                context.logger.warn("Grid & size: unable to keep items on their pages; the launcher places them", it)
                chain.proceed()
            }
        }
    }

    private fun installMemory(context: FeatureContext, logic: Class<*>) {
        val toAdd = requireNotNull(Reflect.declared(logic, "getItemsToBeAdded") { it.parameterTypes.size == 2 })
        val toRemove = requireNotNull(Reflect.declared(logic, "getItemsToBeRemoved") { it.parameterTypes.size == 2 })
        val entry = requireNotNull(context.findClass(DB_ENTRY))
        val screenOf = Reflect.declaredField(requireNotNull(context.findClass(ITEM_INFO)), "screenId")
        val idOf = Reflect.declaredField(requireNotNull(context.findClass(ITEM_INFO)), "id")
        val typeOf = Reflect.declaredField(requireNotNull(context.findClass(ITEM_INFO)), "itemType")
        val folderOf = Reflect.declaredField(entry, "mFolderItems")
        val ids = toRemove.returnType
        val add = requireNotNull(Reflect.method(ids, "add", Int::class.javaPrimitiveType!!))

        fun remembered(src: Any?, dest: Any?) =
            GridSpec.remembered((src as List<*>).filterNotNull(), (dest as List<*>).filterNotNull(), screenOf::getInt)

        context.xposed.hook(toAdd).intercept { chain ->
            runCatching { ArrayList(remembered(chain.args[0], chain.args[1]).first) }
                .onFailure { context.logger.warn("Grid & size: unable to keep the remembered layout to its pages", it) }
                .getOrElse { chain.proceed() }
        }
        context.xposed.hook(toRemove).intercept { chain ->
            runCatching {
                val removed = ids.getDeclaredConstructor().newInstance()
                for (item in remembered(chain.args[0], chain.args[1]).second) {
                    add.invoke(removed, idOf.getInt(item))
                    // A folder's items go with it, as the launcher removes them.
                    if (typeOf.getInt(item) == ITEM_TYPE_FOLDER) {
                        (folderOf.get(item) as? Map<*, *>)?.values?.forEach { set -> (set as? Iterable<*>)?.forEach { add.invoke(removed, it as Int) } }
                    }
                }
                removed
            }.onFailure { context.logger.warn("Grid & size: unable to keep the remembered layout to its pages", it) }
                .getOrElse { chain.proceed() }
        }
    }

    private fun deleteOwnDatabases(context: FeatureContext, launcher: Context) {
        val names = launcher.databaseList().filter { GridSpec.isOwnDb(it.removeSuffix("-journal").removeSuffix("-wal").removeSuffix("-shm")) }
        for (name in names) launcher.getDatabasePath(name).delete()
        if (names.isNotEmpty()) context.logger.info("Grid & size: back on the launcher's own grid; removed ${names.joinToString()}")
    }

    /**
     * Remembers the size of the database the launcher last migrated to when it
     * is one of this module's, so the way back to Google's grid is let through
     * after a restart too. Read from the launcher's own record of it.
     */
    private fun rememberOwnSource(launcher: Context) {
        val prefs = launcher.getSharedPreferences(LAUNCHER_PREFS, Context.MODE_PRIVATE)
        if (!GridSpec.isOwnDb(prefs.getString(SRC_DB_FILE, null))) return
        val size = prefs.getString(SRC_WORKSPACE_SIZE, null)?.split(',') ?: return
        val columns = size.getOrNull(0)?.trim()?.toIntOrNull() ?: return
        val rows = size.getOrNull(1)?.trim()?.toIntOrNull() ?: return
        Grid.addOwnSize(columns, rows)
    }

    // ---- Icon size ------------------------------------------------------------------------

    /**
     * Scales the icon size of every responsive cell spec as it is parsed, and
     * the grid's icon sizes, which the launcher draws its icon bitmaps at.
     *
     * Every provider is parsed afresh for each profile it builds, so each spec
     * is scaled exactly once. All apps' cell spec matches the Home screen's
     * (`matchWorkspace`), so it follows; a fixed one is scaled the same way.
     */
    private fun installIconSize(context: FeatureContext) {
        val companion = requireNotNull(context.findClass(CELL_SPECS_COMPANION))
        val create = requireNotNull(Reflect.declared(companion, "create") { it.parameterTypes.size == 1 })
        val provider = requireNotNull(context.findClass(CELL_SPECS_PROVIDER))
        val groupsOf = Reflect.declaredField(provider, "groupOfSpecs")
        val group = requireNotNull(context.findClass(SPEC_GROUP))
        val heightsOf = Reflect.declaredField(group, "heightSpecs")
        val widthsOf = Reflect.declaredField(group, "widthSpecs")
        val cell = requireNotNull(context.findClass(CELL_SPEC))
        val iconOf = Reflect.declaredField(cell, "iconSize")
        val size = Sizes(requireNotNull(context.findClass(SIZE_SPEC)))

        context.xposed.hook(create).intercept { chain ->
            chain.proceed()?.also { result ->
                val scale = GridSpec.iconScale(context.settings[Settings.GRID_ICON_SIZE])
                if (scale != 1f) runCatching {
                    for (each in groupsOf.get(result) as List<*>) {
                        each ?: continue
                        for (specs in arrayOf(heightsOf.get(each), widthsOf.get(each))) {
                            for (spec in specs as List<*>) spec?.let { size.scale(iconOf.get(it), scale, scaleCap = true) }
                        }
                    }
                }.onFailure { context.logger.warn("Grid & size: unable to size the icons", it) }
            }
        }

        val option = requireNotNull(context.findClass(DISPLAY_OPTION))
        val parse = requireNotNull(Reflect.declared(option, "parseWeightedPredefinedDisplayOption") { it.parameterTypes.size == 4 })
        val sizesOf = Reflect.declaredField(option, "iconSizes")
        context.xposed.hook(parse).intercept { chain ->
            chain.proceed()?.also { result ->
                val scale = GridSpec.iconScale(context.settings[Settings.GRID_ICON_SIZE])
                if (scale != 1f) runCatching {
                    val sizes = sizesOf.get(result) as FloatArray
                    for (index in sizes.indices) sizes[index] *= scale
                }.onFailure { context.logger.warn("Grid & size: unable to size the icon bitmaps", it) }
            }
        }
    }

    // ---- Spacing ----------------------------------------------------------------------------

    /**
     * Spreads the Home screen spec's cells apart or together: across for
     * width specs, down for height specs ([GridSpec.spread]).
     */
    private fun installSpacing(context: FeatureContext) {
        val companion = requireNotNull(context.findClass(SPECS_COMPANION))
        val create = requireNotNull(Reflect.declared(companion, "create") { it.parameterTypes.size == 2 })
        val provider = requireNotNull(context.findClass(SPECS_PROVIDER))
        val groupsOf = Reflect.declaredField(provider, "groupOfSpecs")
        val group = requireNotNull(context.findClass(SPEC_GROUP))
        val heightsOf = Reflect.declaredField(group, "heightSpecs")
        val widthsOf = Reflect.declaredField(group, "widthSpecs")
        val spec = requireNotNull(context.findClass(RESPONSIVE_SPEC))
        val gutterOf = Reflect.declaredField(spec, "gutter")
        val startOf = Reflect.declaredField(spec, "startPadding")
        val endOf = Reflect.declaredField(spec, "endPadding")
        val size = Sizes(requireNotNull(context.findClass(SIZE_SPEC)))

        context.xposed.hook(create).intercept { chain ->
            chain.proceed()?.also { result ->
                val across = GridSpec.spacingFactor(context.settings[Settings.GRID_SPACING_X])
                val down = GridSpec.spacingFactor(context.settings[Settings.GRID_SPACING_Y])
                if ((across != 1f || down != 1f) && (chain.args[1] as? Enum<*>)?.name == WORKSPACE) runCatching {
                    for (each in groupsOf.get(result) as List<*>) {
                        each ?: continue
                        // The grid this profile is built for, a preview's included, parsed on this thread.
                        val grid = applied.get()
                        val columns = grid?.get(0) ?: Grid.appliedColumns
                        val rows = grid?.get(1) ?: Grid.appliedRows
                        for (it in widthsOf.get(each) as List<*>) it?.let { size.spread(startOf.get(it), endOf.get(it), gutterOf.get(it), columns, across) }
                        for (it in heightsOf.get(each) as List<*>) it?.let { size.spread(startOf.get(it), endOf.get(it), gutterOf.get(it), rows, down) }
                    }
                }.onFailure { context.logger.warn("Grid & size: unable to space the cells", it) }
            }
        }
    }

    /** `SizeSpec`'s own values: a fixed size, a share of the available space, and a cap. */
    private class Sizes(type: Class<*>) {
        private val fixed: Field = Reflect.declaredField(type, "fixedSize")
        private val share: Field = Reflect.declaredField(type, "ofAvailableSpace")
        private val match: Field = Reflect.declaredField(type, "matchWorkspace")
        private val cap: Field = Reflect.declaredField(type, "maxSize")

        /**
         * Applies [GridSpec.spread] to one spec's paddings and gap when all
         * three are in the same unit, a share of the space or fixed dp; a spec
         * that mixes them is left as the launcher wrote it.
         */
        fun spread(start: Any?, end: Any?, gap: Any?, cells: Int, factor: Float) {
            if (start == null || end == null || gap == null) return
            val parts = arrayOf(start, end, gap)
            if (parts.any { match.getBoolean(it) }) return
            val unit = when {
                parts.all { fixed.getFloat(it) == 0f } -> share
                parts.all { share.getFloat(it) == 0f } -> fixed
                else -> return
            }
            val spread = GridSpec.spread(unit.getFloat(start), unit.getFloat(end), unit.getFloat(gap), cells, factor)
            for (index in parts.indices) unit.setFloat(parts[index], spread[index])
        }

        /** Scales a size spec; one that matches the Home screen's is left to follow it. */
        fun scale(size: Any?, factor: Float, scaleCap: Boolean) {
            size ?: return
            if (match.getBoolean(size)) return
            fixed.setFloat(size, fixed.getFloat(size) * factor)
            share.setFloat(size, share.getFloat(size) * factor)
            val max = cap.getInt(size)
            if (scaleCap && max in 1 until Int.MAX_VALUE) cap.setInt(size, (max * factor).toInt())
        }
    }

    // ---- All apps columns -------------------------------------------------------------------

    private fun installAppsColumns(context: FeatureContext) {
        val spec = requireNotNull(Host.cls(context.classLoader, DISPLAY_OPTION_SPEC))
        val columnsOf = Reflect.declaredField(spec, "numAllAppsColumns")
        val map = Reflect.declaredMethod(spec, "mapTypeIndex", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)

        // Each profile lays All apps out from its own copy of the spec.
        context.xposed.hook(map).intercept { chain ->
            chain.proceed()?.also { copy ->
                val chosen = context.settings[Settings.APP_DRAWER_COLUMNS]
                if (chosen != GridSpec.SYSTEM) runCatching {
                    columnsOf.setInt(copy, GridSpec.count(chosen, columnsOf.getInt(copy)))
                }.onFailure { context.logger.warn("Grid & size: unable to set the All apps columns", it) }
            }
        }

        // The model's count, which the app suggestions row is filled to.
        val idp = requireNotNull(context.findClass(IDP))
        val databaseOf = Reflect.declaredField(idp, "numDatabaseAllAppsColumns")
        context.xposed.hook(Reflect.declaredMethod(idp, "initGrid", String::class.java)).intercept { chain ->
            chain.proceed().also {
                val chosen = context.settings[Settings.APP_DRAWER_COLUMNS]
                if (chosen != GridSpec.SYSTEM) runCatching {
                    databaseOf.setInt(chain.thisObject, GridSpec.count(chosen, databaseOf.getInt(chain.thisObject)))
                }.onFailure { context.logger.warn("Grid & size: unable to set the All apps suggestions", it) }
            }
        }
    }

    private companion object {
        const val PHONE = 0
        const val DESKTOP = -100
        const val HOTSEAT = -101
        const val FOLDER = 0
        const val WORKSPACE = "Workspace"

        const val ITEMS_QUERY =
            "SELECT itemType, intent, appWidgetProvider, container FROM favorites WHERE container IN (-100, -101) OR container >= 0"

        const val LAUNCHER_PREFS = "com.android.launcher3.prefs"
        const val SRC_DB_FILE = "migration_src_db_file"
        const val SRC_WORKSPACE_SIZE = "migration_src_workspace_size"

        const val IDP = "com.android.launcher3.InvariantDeviceProfile"
        const val DISPLAY_OPTION = "com.android.launcher3.deviceprofile.parser.DisplayOption"
        const val GRID_OPTION = "com.android.launcher3.deviceprofile.parser.GridOption"
        const val DISPLAY_OPTION_SPEC = "com.android.launcher3.deviceprofile.parser.DisplayOptionSpec"
        const val MIGRATION_OPTION = "com.android.launcher3.model.GridMigrationOption"
        const val MIGRATION_OPTION_COMPANION = "com.android.launcher3.model.GridMigrationOption\$Companion"
        const val MIGRATION_OPTION_STAND_IN = "com.android.launcher3.model.GridMigrationOption\$FourByFour"
        const val MIGRATION_LOGIC = "com.android.launcher3.model.GridSizeMigrationLogic"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val DB_ENTRY = "com.android.launcher3.model.DbEntry"
        const val ITEM_TYPE_FOLDER = 2
        const val GRID_STATE = "com.android.launcher3.model.DeviceGridState"
        const val CELL_SPECS_PROVIDER = "com.android.launcher3.responsive.ResponsiveCellSpecsProvider"
        const val CELL_SPECS_COMPANION = "com.android.launcher3.responsive.ResponsiveCellSpecsProvider\$Companion"
        const val SPECS_PROVIDER = "com.android.launcher3.responsive.ResponsiveSpecsProvider"
        const val SPECS_COMPANION = "com.android.launcher3.responsive.ResponsiveSpecsProvider\$Companion"
        const val SPEC_GROUP = "com.android.launcher3.responsive.ResponsiveSpecGroup"
        const val CELL_SPEC = "com.android.launcher3.responsive.CellSpec"
        const val RESPONSIVE_SPEC = "com.android.launcher3.responsive.ResponsiveSpec"
        const val SIZE_SPEC = "com.android.launcher3.responsive.SizeSpec"
    }
}
