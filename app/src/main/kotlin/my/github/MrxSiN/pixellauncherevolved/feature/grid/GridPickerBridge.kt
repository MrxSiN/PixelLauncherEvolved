package my.github.MrxSiN.pixellauncherevolved.feature.grid

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.GridBridge
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.layout.LiveLayout
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * The custom grid and the spacing in Wallpaper & style → Layout, and the
 * icon size in Wallpaper & style → Icons, as the launcher answers them over `grid_control`.
 *
 * ```
 * com.android.launcher3.util.ContentProviderProxy
 *   public Cursor query(Uri, String[], String, String[], String)   // /list_options: the grid tiles
 * GridCustomizationsProxy (LauncherBaseAppComponent.getGridCustomizationsProxy())
 *   int handleUpdate(String, ContentValues, Bundle)                  // /default_grid: a tile applied or previewed
 *   public Bundle call(String, String, Bundle)                       // this module's ple_grid_ methods
 * com.android.launcher3.preview.PreviewSurfaceRenderer
 *   public void recreatePreviewRenderer()                            // a preview drawn again
 * ```
 *
 * `list_options` gets one more row after Google's, [GridBridge.CUSTOM], with the
 * custom columns and rows and no icon, so the picker draws its tile as a grid of
 * that size. Applying it makes it the launcher's grid name, as applying any
 * tile does. Changing the columns, rows or icon size redraws every open
 * preview, which reads the settings as it builds its grid. Every call here is
 * the picker's, on a Binder thread; none is a launcher frame.
 */
internal class GridPickerBridge(private val feature: FeatureContext) {

    private val context: Context = feature.appContext
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val previews: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    private var renderers: Renderers? = null

    fun install() {
        val proxy = requireNotNull(feature.findClass(PROXY))
        val provider = feature.findClass(PROVIDER)
        fun ours(owner: Any?) = provider == null || provider.isInstance(owner)

        val call = requireNotNull(Reflect.method(proxy, "call", String::class.java, String::class.java, Bundle::class.java))
        feature.xposed.hook(call).intercept { chain ->
            val method = chain.args[0] as? String
            if (method == null || !method.startsWith(GridBridge.PREFIX) || !ours(chain.thisObject)) return@intercept chain.proceed()
            if (!IconsBridge.callerAllowed(context)) throw SecurityException("$method is only for Wallpaper & style")
            try {
                when (method) {
                    GridBridge.STATE -> state()
                    GridBridge.SET -> set(chain.args[2] as? Bundle)
                    else -> Bundle()
                }
            } catch (error: Throwable) {
                feature.logger.warn("Grid & size: Wallpaper & style's $method failed", error)
                Bundle()
            }
        }

        val query = requireNotNull(Reflect.declared(proxy, "query") { it.parameterTypes.size == 5 })
        feature.xposed.hook(query).intercept { chain ->
            val result = chain.proceed()
            val uri = chain.args[0] as? Uri
            if (uri?.path != LIST_OPTIONS || !ours(chain.thisObject)) return@intercept result
            runCatching { withCustom(result as? Cursor) }
                .onFailure { feature.logger.warn("Grid & size: unable to list the custom grid", it) }
                .getOrDefault(result)
        }

        // Every grid applied, from the picker's Apply and from a preview showing a tile, goes through
        // GridCustomizationsProxy.handleUpdate("/default_grid"), which only knows Google's names.
        val component = requireNotNull(feature.findClass(APP_COMPONENT))
        val customizations = requireNotNull(Reflect.method(component, "getGridCustomizationsProxy")).returnType
        val handle = requireNotNull(Reflect.declared(customizations, "handleUpdate") { it.parameterTypes.size == 3 })
        val idpOf = Reflect.declaredField(customizations, "mIdp")
        feature.xposed.hook(handle).intercept { chain ->
            val values = chain.args[1] as? ContentValues
            val name = values?.getAsString("name") ?: values?.getAsString("grid_name")
            if (chain.args[0] != DEFAULT_GRID || name != GridBridge.CUSTOM) return@intercept chain.proceed()
            applyCustom(idpOf.get(chain.thisObject))
        }

        feature.findClass(PREVIEW_RENDERER)?.let { renderer ->
            renderers = Renderers(renderer, requireNotNull(feature.findClass(IDP)), requireNotNull(feature.findClass(APP_STATE)))
            for (constructor in renderer.declaredConstructors) {
                feature.xposed.hook(constructor).intercept { chain -> chain.proceed().also { chain.thisObject?.let(previews::add) } }
            }
        }
    }

    private fun state(): Bundle = Bundle().apply {
        val room = runCatching { Grid.room(context, feature.classLoader) }.getOrNull()
        putBoolean(GridBridge.AVAILABLE, room != null && Grid.stockColumns > 0 && Grid.support == Grid.Support.PHONE)
        putBoolean(GridBridge.CURRENT, isCurrent())
        putInt(GridBridge.COLUMNS, columns())
        putInt(GridBridge.ROWS, rows())
        room?.let {
            val columns = GridSpec.choices(Grid.stockColumns, it.columns)
            val rows = GridSpec.choices(Grid.stockRows, it.rows)
            putInt(GridBridge.COLUMNS_FROM, columns.first)
            putInt(GridBridge.COLUMNS_TO, columns.last)
            putInt(GridBridge.ROWS_FROM, rows.first)
            putInt(GridBridge.ROWS_TO, rows.last)
        }
        putInt(GridBridge.ICON_SIZE, GridSpec.nearestIconSize(feature.settings[Settings.GRID_ICON_SIZE]))
        putInt(GridBridge.SPACING_X, feature.settings[Settings.GRID_SPACING_X])
        putInt(GridBridge.SPACING_Y, feature.settings[Settings.GRID_SPACING_Y])
    }

    /** Stores one value from the picker; a grid a Home screen widget cannot fit in is refused with its name. */
    private fun set(extras: Bundle?): Bundle {
        val key = extras?.getString(GridBridge.KEY)
        val value = extras?.getInt(GridBridge.VALUE) ?: 0
        val answer = Bundle()
        when (key) {
            Settings.GRID_COLUMNS.key -> misfit(value, rows())?.let { return answer.refused(it) }
                ?: put(Settings.GRID_COLUMNS, GridSpec.stored(value, Grid.stockColumns))
            Settings.GRID_ROWS.key -> misfit(columns(), value)?.let { return answer.refused(it) }
                ?: put(Settings.GRID_ROWS, GridSpec.stored(value, Grid.stockRows))
            Settings.GRID_ICON_SIZE.key -> put(Settings.GRID_ICON_SIZE, GridSpec.nearestIconSize(value))
            Settings.GRID_SPACING_X.key -> put(Settings.GRID_SPACING_X, value.coerceIn(Settings.GRID_SPACING_X.range))
            Settings.GRID_SPACING_Y.key -> put(Settings.GRID_SPACING_Y, value.coerceIn(Settings.GRID_SPACING_Y.range))
            else -> return answer
        }
        answer.putBoolean(GridBridge.OK, true)
        return answer
    }

    private fun Bundle.refused(message: String): Bundle = apply {
        putBoolean(GridBridge.OK, false)
        putString(GridBridge.MESSAGE, message)
    }

    /**
     * Writes [setting] on the main thread, where the launcher's rebuild for it
     * is posted, then redraws every open preview ([Renderers.redraw]) and
     * tells the picker its tiles changed. The picker re-reads `list_options`
     * only when `default_grid` changes, the one URI it observes.
     */
    private fun put(setting: IntSetting, value: Int) = main.post {
        feature.settings.put(setting, value)
        main.post {
            renderers?.let { renderers ->
                val launcherOwn = renderers.idp(context)
                for (preview in synchronized(previews) { previews.toTypedArray() }) {
                    runCatching { if (!renderers.redraw(preview, launcherOwn)) previews.remove(preview) }
                        .onFailure { feature.logger.warn("Grid & size: unable to redraw a preview", it) }
                }
            }
            context.contentResolver.notifyChange(Uri.parse("content://${IconsBridge.AUTHORITY}$DEFAULT_GRID"), null)
        }
    }

    /**
     * Wallpaper & style's previews, drawn again with the settings as they are now.
     *
     * ```
     * PreviewSurfaceRenderer
     *   PreviewContext mPreviewContext; LauncherPreviewRenderer mCurrentRenderer
     *   OnIDPChangeListener mOnIDPChangeListener; boolean mDestroyed
     *   void recreatePreviewRenderer()
     * LauncherModel.mLoadCompleteFuture                       // the load under way, or the last one
     * ```
     *
     * A preview reads its grid once, into the `InvariantDeviceProfile` of its
     * own `PreviewContext`, so that profile is rebuilt the way the launcher's
     * is (`onConfigChanged`), which also reloads the preview's model when the
     * grid changed. Left to the profile's listener, the preview would be drawn
     * again at once, and a new renderer first binds the layout its model still
     * holds: the old grid's, whose items land on the wrong cells, or nowhere
     * outside the new grid, until the reload binds them again. So the listener
     * sits that rebuild out and the preview is drawn once the reload is done;
     * until then it shows the previous grid, unbound so the reload cannot
     * reach it.
     */
    private inner class Renderers(type: Class<*>, idp: Class<*>, appState: Class<*>) {
        private val recreate: Method = Reflect.declaredMethod(type, "recreatePreviewRenderer")
        private val contextOf: Field = Reflect.declaredField(type, "mPreviewContext")
        private val destroyedOf: Field = Reflect.declaredField(type, "mDestroyed")
        private val currentOf: Field = Reflect.declaredField(type, "mCurrentRenderer")
        private val listenerOf: Field = Reflect.declaredField(type, "mOnIDPChangeListener")
        private val unbind: Method = requireNotNull(Reflect.method(currentOf.type, "onViewDestroyed"))
        private val rebuild: Method = Reflect.declaredMethod(idp, "onConfigChanged")
        private val listen: Method = requireNotNull(Reflect.declared(idp, "addOnChangeListener") { it.parameterTypes.size == 1 })
        private val unlisten: Method = requireNotNull(Reflect.declared(idp, "removeOnChangeListener") { it.parameterTypes.size == 1 })
        private val idps: Any = requireNotNull(Reflect.declaredField(idp, "INSTANCE").get(null))
        private val states: Any = requireNotNull(Reflect.declaredField(appState, "INSTANCE").get(null))
        private val get: Method = requireNotNull(Reflect.method(idps.javaClass, "get", Context::class.java))
        private val modelOf: Field = Reflect.declaredField(appState, "model")
        private val loadOf: Field = Reflect.declaredField(modelOf.type, "mLoadCompleteFuture")

        fun idp(context: Context): Any? = get.invoke(idps, context)

        /** Draws [preview] again; false once it is gone for good. */
        fun redraw(preview: Any, launcherOwn: Any?): Boolean {
            if (destroyedOf.getBoolean(preview)) return false
            val previewContext = contextOf.get(preview) as Context
            val idp = idp(previewContext)
            if (idp == null || idp === launcherOwn) {
                recreate.invoke(preview)
                return true
            }
            val listener = listenerOf.get(preview)
            unlisten.invoke(idp, listener)
            try {
                rebuild.invoke(idp)
            } finally {
                listen.invoke(idp, listener)
            }
            // Nothing reloading (only the spacing changed, say): draw now, as the listener would have.
            val loading = get.invoke(states, previewContext)?.let { loadOf.get(modelOf.get(it)) } as? CompletableFuture<*>
            if (loading == null || loading.isDone) {
                recreate.invoke(preview)
                return true
            }
            currentOf.get(preview)?.let {
                unbind.invoke(it)
                currentOf.set(preview, null)
            }
            val draw = Runnable { recreate.invoke(preview) }
            // ponytail: a load that never ends still redraws after LOAD_WAIT_MS, from what the model holds.
            main.postDelayed(draw, LOAD_WAIT_MS)
            loading.whenComplete { _, _ ->
                main.post {
                    if (main.hasCallbacks(draw)) {
                        main.removeCallbacks(draw)
                        draw.run()
                    }
                }
            }
            return true
        }
    }

    /**
     * Makes the custom grid [idp]'s, as applying any tile does: the launcher's
     * own, unless a Home screen widget cannot fit in it, or a preview's.
     */
    private fun applyCustom(idp: Any?): Int {
        idp ?: return 0
        val launcherOwn = idp === LiveLayout.singleton(feature.classLoader, IDP, context)
        if (launcherOwn) misfit(columns(), rows())?.let { message ->
            main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            feature.logger.warn("Grid & size: the custom grid was not applied; $message")
            return 0
        }
        requireNotNull(Reflect.method(idp.javaClass, "setCurrentGrid", String::class.java)).invoke(idp, GridBridge.CUSTOM)
        if (launcherOwn) feature.logger.info("Grid & size: the custom grid ${columns()} × ${rows()} applied from Wallpaper & style")
        return 1
    }

    /** Why a [columns] by [rows] grid cannot hold the Home screen, or null when it can. */
    private fun misfit(columns: Int, rows: Int): String? {
        val text = feature.moduleResources
        val items = Grid.homeItems(context, feature.classLoader) ?: return text?.getString(R.string.feature_grid_misfit_unknown) ?: "unknown"
        val item = GridSpec.misfits(items, columns, rows).firstOrNull() ?: return null
        return text?.getString(R.string.feature_grid_misfit_message, item.title, columns, rows) ?: "${item.title}"
    }

    private fun columns(): Int = GridSpec.count(feature.settings[Settings.GRID_COLUMNS], Grid.stockColumns)

    private fun rows(): Int = GridSpec.count(feature.settings[Settings.GRID_ROWS], Grid.stockRows)

    /** Whether the custom grid is the launcher's now: its grid name, as the launcher keeps it. */
    private fun isCurrent(): Boolean =
        context.getSharedPreferences(LAUNCHER_PREFS, Context.MODE_PRIVATE).getString(GRID_NAME, null) == GridBridge.CUSTOM

    /**
     * Google's tiles as the launcher listed them, none chosen while the custom
     * grid is, and the custom tile after them.
     */
    private fun withCustom(cursor: Cursor?): Cursor? {
        cursor ?: return null
        val current = isCurrent()
        val names = cursor.columnNames
        val copy = MatrixCursor(names, cursor.count + 1)
        cursor.use {
            while (it.moveToNext()) {
                copy.addRow(Array<Any?>(names.size) { index ->
                    when {
                        names[index] == IS_DEFAULT && current -> false
                        it.getType(index) == Cursor.FIELD_TYPE_INTEGER -> it.getInt(index)
                        it.getType(index) == Cursor.FIELD_TYPE_NULL -> null
                        else -> it.getString(index)
                    }
                })
            }
        }
        val title = feature.moduleResources?.getString(R.string.feature_grid_custom) ?: "Custom"
        copy.addRow(Array<Any?>(names.size) { index ->
            when (names[index]) {
                "name" -> GridBridge.CUSTOM
                "grid_title" -> title
                "rows" -> rows()
                "cols" -> columns()
                IS_DEFAULT -> current
                // No drawable: the picker draws the tile as a grid of this size.
                "grid_icon_id" -> 0
                "preview_count" -> 1
                else -> null
            }
        })
        return copy
    }

    private companion object {
        const val PROXY = "com.android.launcher3.util.ContentProviderProxy"
        const val PROVIDER = "com.android.launcher3.graphics.LauncherCustomizationProvider"
        const val APP_COMPONENT = "com.android.launcher3.dagger.LauncherBaseAppComponent"
        const val PREVIEW_RENDERER = "com.android.launcher3.preview.PreviewSurfaceRenderer"
        const val IDP = "com.android.launcher3.InvariantDeviceProfile"
        const val APP_STATE = "com.android.launcher3.LauncherAppState"
        const val LIST_OPTIONS = "/list_options"
        const val DEFAULT_GRID = "/default_grid"
        const val IS_DEFAULT = "is_default"
        const val LAUNCHER_PREFS = "com.android.launcher3.prefs"
        const val GRID_NAME = "idp_grid_name"

        /** How long a preview waits for its model before drawing anyway; a load takes a few hundred ms. */
        const val LOAD_WAIT_MS = 2_000L
    }
}
