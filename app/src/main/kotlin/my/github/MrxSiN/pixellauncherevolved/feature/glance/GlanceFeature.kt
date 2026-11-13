package my.github.MrxSiN.pixellauncherevolved.feature.glance

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.SparseArray

import io.github.libxposed.api.XposedInterface

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.focus.WorkspaceBinding
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Puts At a Glance on the main home screen of whatever layout is showing, and
 * on every home screen when asked.
 *
 * On this launcher At a Glance is a model item, not a view the workspace makes:
 * ```
 * com.google.android.apps.nexuslauncher.smartspace.SmartspaceItemInfo extends ItemInfo
 *   itemType 14, container -100, screenId 0, cell (0, 0),
 *   span (InvariantDeviceProfile.numSearchContainerColumns, 1),
 *   id R.id.search_container_workspace                      // CP4A.261105.040.A1
 * com.android.launcher3.util.ItemInflater.inflateItem(ItemInfo, ViewGroup, int)
 *   instanceof SmartspaceItemInfo -> new SmartspaceViewContainer
 * ```
 * It is in memory only; no database row names it. So more of them are made the
 * same way the Mode filter hides pages: the complete bind is handed a second
 * model holding copies of that one item, each on another screen, and the
 * launcher inflates and lays each out exactly as it does the first. The
 * database and the shared model are never written.
 *
 * Two hooks on the one bind. The outermost sees the model as loaded and keeps
 * the launcher's own item; the innermost runs after the Mode filter
 * ([my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusHomeFeature]) and
 * sees only the pages that will show. When a Mode has hidden screen 0, the
 * original is gone from what it sees, and a copy goes only where that Mode asks
 * (all its pages, or one); following Home settings, it shows on none.
 *
 * A copy goes only onto a page whose top row is free under the item's span,
 * the same test the launcher makes before putting the original back on screen
 * 0. Nothing is moved to make room.
 *
 * Home settings' own At a Glance switch adds or removes only the original,
 * through the model (`SmartspaceToggledTask`: `BgDataModel.removeItems`, or an
 * incremental `bindItems` of it). Either is followed by one complete bind again,
 * so the copies come and go with it.
 */
class GlanceFeature : ToggleFeature(Settings.HOME_GLANCE_ALL_SCREENS), SharedPreferences.OnSharedPreferenceChangeListener {

    override val compatibility = CompatibilityFeature.AT_A_GLANCE

    /** The launcher's own At a Glance item in the latest complete bind, or null when it is switched off. */
    @Volatile
    private var original: Any? = null

    @Volatile
    private var everyScreen = false

    /** The copies handed to the latest complete bind, which the launcher's model does not hold. */
    @Volatile
    private var copies: List<Any> = emptyList()

    private val main by lazy { Handler(Looper.getMainLooper()) }

    override fun install(context: FeatureContext) {
        val callbacks = context.findClass(MODEL_CALLBACKS)
        val bind = callbacks?.let(WorkspaceBinding::find)
        val glance = context.findClass(SMARTSPACE_ITEM)
        val itemInfo = context.findClass(ITEM_INFO)
        val data = context.findClass(WORKSPACE_DATA)
        val fields = itemInfo?.let { ItemFields.of(it) }
        val items = data?.let { Reflect.field(it, ITEMS) }
        val version = data?.let { Reflect.field(it, VERSION) }
        val modificationId = data?.let { Reflect.field(it, MODIFICATION_ID) }
        val rebuild = data?.let {
            runCatching {
                it.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, SparseArray::class.java)
                    .apply { isAccessible = true }
            }.getOrNull()
        }
        val copier = glance?.let { Copier.of(it) }
        if (bind == null || glance == null || fields == null || items == null || version == null ||
            modificationId == null || rebuild == null || copier == null
        ) {
            context.logger.warn("The launcher's At a Glance item is unavailable; At a Glance placement is not installed")
            return
        }

        val model = Model(items, version, modificationId, rebuild)
        everyScreen = context.settings[toggle]

        context.xposed.hook(bind).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept { chain ->
            original = runCatching { model.items(chain.args[0])?.let { find(it, glance) } }.getOrNull()
            chain.proceed()
        }
        context.xposed.hook(bind).setPriority(XposedInterface.PRIORITY_LOWEST).intercept { chain ->
            val placed = runCatching { place(chain.args[0], model, glance, fields, copier) }
                .onFailure { context.logger.warn("At a Glance could not be placed on the home screens", it) }
                .getOrNull()
            if (placed == null) chain.proceed() else chain.proceed(chain.args.toTypedArray().also { it[0] = placed })
        }

        followStockSwitch(context, callbacks!!, glance)
        keepNewAppsFromUnderCopies(context)
        ModeOverrides.onGlanceChanged = ::rebindLater
        LauncherSettings.preferences(context.appContext).registerOnSharedPreferenceChangeListener(this)
        context.logger.info("At a Glance placement ready")
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key != toggle.key) return
        everyScreen = preferences.getBoolean(key, toggle.default)
        rebindLater()
    }

    private fun rebindLater() {
        main.post { ModeOverrides.rebindWorkspace?.invoke() }
    }

    /**
     * Counts the copies as taken cells when the launcher finds room for a new app.
     *
     * ```
     * com.android.launcher3.model.WorkspaceItemSpaceFinder
     *   findSpaceForItem(ArrayList itemsBeingAdded, int, int, IntSet, WorkspaceItemCoordinates)
     * ```
     * It lays out each screen from the model's items plus the list it is handed;
     * the copies are not in the model, so they go into a new list beside the
     * caller's own, which is never changed.
     */
    private fun keepNewAppsFromUnderCopies(context: FeatureContext) {
        val finder = context.findClass(SPACE_FINDER)?.let { type ->
            Reflect.declared(type, FIND_SPACE) { it.parameterTypes.size == 5 && it.parameterTypes[0] == ArrayList::class.java }
        }
        if (finder == null) {
            context.logger.warn("The launcher's space finder is unavailable; a new app may land under an At a Glance copy")
            return
        }
        context.xposed.hook(finder).intercept { chain ->
            val adding = chain.args[0] as? ArrayList<*>
            val bound = copies
            if (adding == null || bound.isEmpty()) {
                chain.proceed()
            } else {
                chain.proceed(chain.args.toTypedArray().also { it[0] = ArrayList<Any?>(adding.size + bound.size).apply { addAll(adding); addAll(bound) } })
            }
        }
    }

    /** Binds everything again after Home settings' At a Glance switch adds or removes the original. */
    private fun followStockSwitch(context: FeatureContext, callbacks: Class<*>, glance: Class<*>) {
        val remove = context.findClass(BG_DATA_MODEL)?.let { model -> Reflect.declared(model, REMOVE_ITEMS) { it.parameterTypes.size == 3 } }
        val bindItems = Reflect.declared(callbacks, BIND_ITEMS) { it.parameterTypes.size == 2 }
        if (remove == null || bindItems == null) {
            context.logger.warn("The launcher's item updates are unavailable; switching At a Glance applies to copies on the next reload")
            return
        }
        for (method in arrayOf(remove, bindItems)) {
            context.xposed.hook(method).intercept { chain ->
                val result = chain.proceed()
                val items = chain.args.firstOrNull { it is List<*> } as? List<*>
                if (items != null && items.any(glance::isInstance)) rebindLater()
                result
            }
        }
    }

    /** A second model with the copies in, or null when the launcher's own needs nothing added. */
    private fun place(model: Any?, models: Model, glance: Class<*>, fields: ItemFields, copier: Copier): Any? {
        copies = emptyList()
        val source = original ?: return null
        val map = models.items(model) ?: return null
        val span = fields.spanX.getInt(source)

        var shown = false
        val screens = java.util.TreeSet<Int>()
        val blocked = HashSet<Int>()
        for (index in 0 until map.size()) {
            val item = map.valueAt(index) ?: continue
            if (item === source) {
                shown = true
                continue
            }
            if (glance.isInstance(item) || fields.container.getInt(item) != CONTAINER_DESKTOP) continue
            val screen = fields.screenId.getInt(item)
            screens += screen
            if (fields.cellY.getInt(item) == 0 && fields.cellX.getInt(item) < span) blocked += screen
        }

        val originalScreen = if (shown) fields.screenId.getInt(source) else null
        val wanted = GlancePlacement.screens(
            screens = screens.toIntArray(),
            blocked = blocked,
            originalScreen = originalScreen,
            everyScreen = ModeOverrides.glanceEveryScreen ?: everyScreen,
            selected = ModeOverrides.glanceScreen,
        )
        val keepOriginal = originalScreen != null && originalScreen in wanted
        // Nothing to add, and the launcher's own item is not to be moved off its page.
        if (wanted.size == (if (keepOriginal) 1 else 0) && (originalScreen == null || keepOriginal)) return null

        val kept = SparseArray<Any?>(map.size() + wanted.size)
        for (index in 0 until map.size()) {
            val item = map.valueAt(index)
            if (item !== source || keepOriginal) kept.put(map.keyAt(index), item)
        }
        val made = ArrayList<Any>(wanted.size)
        for (screen in wanted) {
            if (keepOriginal && screen == originalScreen) continue
            val copy = copier.copy(source)
            fields.screenId.setInt(copy, screen)
            fields.id.setInt(copy, GlancePlacement.copyId(screen))
            kept.put(GlancePlacement.copyId(screen), copy)
            made += copy
        }
        return models.rebuild(model!!, kept).also { copies = made }
    }

    private fun find(map: SparseArray<*>, glance: Class<*>): Any? {
        for (index in 0 until map.size()) {
            val item = map.valueAt(index)
            if (glance.isInstance(item)) return item
        }
        return null
    }

    private class Model(val items: Field, val version: Field, val modificationId: Field, val constructor: Constructor<*>) {
        fun items(model: Any?): SparseArray<*>? = model?.let { items.get(it) as? SparseArray<*> }

        fun rebuild(model: Any, map: SparseArray<Any?>): Any =
            constructor.newInstance(version.getInt(model), modificationId.getInt(model), map)
    }

    private class ItemFields(
        val id: Field,
        val container: Field,
        val screenId: Field,
        val cellX: Field,
        val cellY: Field,
        val spanX: Field,
    ) {
        companion object {
            fun of(type: Class<*>): ItemFields? {
                val found = listOf("id", "container", "screenId", "cellX", "cellY", "spanX").map { Reflect.field(type, it) }
                if (found.any { it == null }) return null
                val (id, container, screenId, cellX, cellY) = found.map { it!! }
                return ItemFields(id, container, screenId, cellX, cellY, found[5]!!)
            }
        }
    }

    /**
     * Copies one At a Glance item, field for field.
     *
     * The launcher's class has no constructor of its own left after shrinking
     * (`new-instance` then `ItemInfo.<init>`), so an instance is allocated
     * without one and every instance field it inherits is copied across.
     */
    private class Copier(private val type: Class<*>, private val allocate: Method, private val unsafe: Any, private val fields: List<Field>) {
        fun copy(source: Any): Any {
            val copy = allocate.invoke(unsafe, type)!!
            for (field in fields) field.set(copy, field.get(source))
            return copy
        }

        companion object {
            fun of(type: Class<*>): Copier? = runCatching {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)!!
                val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
                val fields = generateSequence(type) { it.superclass }
                    .takeWhile { it != Any::class.java }
                    .flatMap { it.declaredFields.asSequence() }
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .onEach { it.isAccessible = true }
                    .toList()
                Copier(type, allocate, unsafe, fields)
            }.getOrNull()
        }
    }

    private companion object {
        const val MODEL_CALLBACKS = "com.android.launcher3.ModelCallbacks"
        const val SMARTSPACE_ITEM = "com.google.android.apps.nexuslauncher.smartspace.SmartspaceItemInfo"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val WORKSPACE_DATA = "com.android.launcher3.model.data.WorkspaceData\$MutableWorkspaceData"
        const val ITEMS = "itemsIdMap"
        const val SPACE_FINDER = "com.android.launcher3.model.WorkspaceItemSpaceFinder"
        const val FIND_SPACE = "findSpaceForItem"
        const val BG_DATA_MODEL = "com.android.launcher3.model.BgDataModel"
        const val REMOVE_ITEMS = "removeItems"
        const val BIND_ITEMS = "bindItems"
        const val VERSION = "version"
        const val MODIFICATION_ID = "modificationId"

        /** `LauncherSettings.Favorites.CONTAINER_DESKTOP`, inlined by the launcher's compiler. */
        const val CONTAINER_DESKTOP = -100
    }
}

/** Which home screens show At a Glance. Pure, so it is tested without a launcher. */
internal object GlancePlacement {

    /**
     * Every page that shows At a Glance, the launcher's own item's page included when it stays.
     *
     * @param screens the shown pages with other items on them, by id; pages show in ascending id.
     * @param blocked pages whose top row has an item under At a Glance's span.
     * @param originalScreen the launcher's own item's page when it will show, else null.
     * @param everyScreen whether every page gets one, rather than only the main one.
     * @param selected the page a Mode chose for it; ignored unless it is showing.
     */
    fun screens(screens: IntArray, blocked: Set<Int>, originalScreen: Int?, everyScreen: Boolean, selected: Int? = null): IntArray {
        val shown = (screens.toList() + listOfNotNull(originalScreen)).toSortedSet()
        fun free(screen: Int) = screen == originalScreen || screen !in blocked
        // The main home screen is the launcher's own page only: while a Mode hides it, At a Glance
        // shows where the Mode says (all pages, or one), and nowhere when it follows Home settings.
        val main = selected?.takeIf { it in shown } ?: originalScreen
        val out = ArrayList<Int>()
        if (main != null && free(main)) out += main
        if (everyScreen) for (screen in shown) if (screen != main && free(screen)) out += screen
        return out.toIntArray()
    }

    /**
     * The id a copy on [screen] goes by. Negative, so it can be neither a
     * database row nor a resource id that some launcher view is looked up by.
     */
    fun copyId(screen: Int): Int = COPY_ID_BASE - screen

    private const val COPY_ID_BASE = -0x10000
}
