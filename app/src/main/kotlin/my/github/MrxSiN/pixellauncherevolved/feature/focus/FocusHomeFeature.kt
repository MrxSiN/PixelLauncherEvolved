package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.os.Handler
import android.os.Looper

import android.util.SparseArray
import android.view.View

import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Executor

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPlan
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.FocusWatcher
import my.github.MrxSiN.pixellauncherevolved.focus.ProviderFocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.SharedPreferencesFocusStore
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Shows a different set of home screens while a Mode is on.
 *
 * The launcher builds its workspace from what the model hands it: a list of
 * screens, then the items that sit on them. This filters that hand-over and
 * nothing else. The database is never touched, so a page a Mode hides is hidden
 * the way a page scrolled off the side is hidden — it is still there, and
 * turning the Mode off brings it back exactly as it was.
 *
 * Filtering a view of a database is safe only while nothing writes the view
 * back. The launcher has one thing that would: `stripEmptyScreens` prunes the
 * screens it finds empty and saves what is left. While a Mode is on, the screens
 * it cannot see are not empty but absent, so that pruning is held off. Without
 * it the first prune during a Mode would delete the ordinary home screen for
 * good.
 */
class FocusHomeFeature : ToggleFeature(Settings.FOCUS_HOME_SCREENS) {

    /** Kept for as long as the feature is: an observer reports only while referenced. */
    private var watcher: FocusWatcher? = null

    /** The launcher that is running, held weakly so a finished one can be collected. */
    @Volatile
    private var current: WeakReference<Any>? = null

    override fun install(context: FeatureContext) {
        val callbacks = context.findClass(MODEL_CALLBACKS)
        val intArray = context.findClass(INT_ARRAY)
        val itemInfo = context.findClass(ITEM_INFO)

        val bindScreens = callbacks?.declaredMethods?.firstOrNull {
            it.name == BIND_SCREENS && it.parameterTypes.size == 1 && it.parameterTypes[0] == intArray
        }
        val wrap = intArray?.method(WRAP, IntArray::class.java)
        val toArray = intArray?.method(TO_ARRAY)
        val screenId = itemInfo?.field(SCREEN_ID)
        val container = itemInfo?.field(CONTAINER)

        if (itemInfo == null || bindScreens == null || wrap == null || toArray == null ||
            screenId == null || container == null
        ) {
            context.logger.warn(
                "The launcher's workspace binding is unavailable; focus home screens are not installed",
            )
            return
        }

        // Nothing is filtered until the pruning guard is in place, because a
        // filtered workspace the launcher is free to prune is a deleted one.
        val workspace = context.findClass(WORKSPACE)
        val strip = workspace?.method(STRIP)
        if (workspace == null || strip == null) {
            context.logger.warn(
                "The launcher's screen pruning cannot be found; focus home screens are not installed, " +
                    "because hiding a page the launcher may prune would delete it",
            )
            return
        }

        val focus = FocusHome(
            // Read on every call rather than captured, so the switch takes
            // effect on the next binding rather than the next launcher start.
            isEnabled = { context.settings[toggle] },
            store = SharedPreferencesFocusStore(LauncherSettings.preferences(context.appContext)),
            source = ProviderFocusSource(context.appContext.contentResolver, context.logger),
            logger = context.logger,
        )
        val previews = FocusPreviewRecorder(itemInfo, context.appContext)
        val reveal = FocusPageReveal(context.logger)
        if (!previews.isUsable) {
            context.logger.warn("The launcher's item positions are unavailable; Focus page previews will be empty")
        }

        holdOffPruning(context, strip, focus)
        hideAddedScreens(context, bindScreens, wrap, toArray, focus)
        hideBoundItems(context, callbacks, screenId, container, focus, previews)
        hideEmptyPages(context, focus)
        hideItems(context, callbacks, screenId, container, focus)
        revealAfterBinding(context, workspace, reveal)
        watch(context, workspace, toArray, focus, previews, reveal)

        context.logger.info("Focus home screens ready")
    }

    /**
     * Stops the launcher pruning screens it was never shown.
     *
     * This is the one write that would turn a hidden page into a deleted one.
     */
    private fun holdOffPruning(context: FeatureContext, strip: Method, focus: FocusHome) {
        context.xposed.hook(strip).intercept { chain ->
            if (focus.isFiltering()) null else chain.proceed()
        }
    }

    /**
     * Handles screens delivered outside a complete model bind.
     *
     * A complete bind was already filtered by [hideBoundItems], so every id in
     * that callback is known and passes through. An incremental callback can
     * contain a newly-created page; that page is added to the complete page
     * catalogue, then kept only when the current home state should show it.
     */
    private fun hideAddedScreens(
        context: FeatureContext,
        bindScreens: Method,
        wrap: Method,
        toArray: Method,
        focus: FocusHome,
    ) {
        context.xposed.hook(bindScreens).intercept { chain ->
            val all = runCatching { toArray.invoke(chain.args.firstOrNull()) as IntArray }.getOrNull()

            if (all == null) {
                chain.proceed()
            } else {
                val wanted = focus.addedScreens(all.toList())
                chain.proceed(arrayOf(wrap.invoke(null, wanted.toIntArray())))
            }
        }
    }

    /**
     * Filters the workspace the launcher is about to build.
     *
     * `bindAddScreens` is not the path that builds it on this launcher: it adds
     * screens to a workspace that already exists, and the first one is built by
     * `bindCompleteModelAsync`, which is handed the whole model at once.
     *
     * Only the items are filtered, and the screens follow. The launcher does not
     * keep a list of screens at all — `collectWorkspaceScreens` walks the items,
     * takes the ones sitting on the workspace and collects the screens they name.
     * So a page with no items left is a page that is never asked for, and
     * filtering the screen list as well would only be describing the same thing
     * twice, in a way the launcher would undo the moment it read the items.
     *
     * The model itself is never touched. The launcher is handed a second one
     * built around a second map, because the model is shared with the loader and
     * with the code that writes; `copy()` would not be enough, as it hands back
     * the very same map.
     */
    private fun hideBoundItems(
        context: FeatureContext,
        callbacks: Class<*>,
        screenId: Field,
        container: Field,
        focus: FocusHome,
        previews: FocusPreviewRecorder,
    ) {
        val data = context.findClass(WORKSPACE_DATA)
        val collect = data?.method(COLLECT_SCREENS)
        val toArray = context.findClass(INT_ARRAY)?.method(TO_ARRAY)
        val version = data?.field(VERSION)
        val modificationId = data?.field(MODIFICATION_ID)
        val items = data?.field(ITEMS)
        val rebuild = data?.let {
            runCatching {
                it.getDeclaredConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    SparseArray::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }
        val bind = callbacks.declaredMethods.firstOrNull {
            it.name == BIND_COMPLETE && it.parameterTypes.size == 2
        }

        if (collect == null || toArray == null || version == null || modificationId == null ||
            items == null || rebuild == null || bind == null
        ) {
            context.logger.warn("The launcher's workspace model is unavailable; a Mode cannot hide a page")
            return
        }

        context.xposed.hook(bind).intercept { chain ->
            val model = chain.args.firstOrNull()
            val filtered = model?.let {
                runCatching {
                    val all = (toArray.invoke(collect.invoke(it)) as IntArray).toList()
                    val itemMap = items.get(it) as SparseArray<*>
                    previews.remember(itemMap)
                    if (focus.screens(all).size == all.size) {
                        null
                    } else {
                        rebuild.newInstance(
                            version.getInt(it),
                            modificationId.getInt(it),
                            keep(itemMap, focus, screenId, container),
                        )
                    }
                }.onFailure { error ->
                    context.logger.warn("The workspace could not be filtered for the focus", error)
                }.getOrNull()
            }

            if (filtered == null) chain.proceed() else chain.proceed(arrayOf(filtered, chain.args.getOrNull(1)))
        }
    }

    /** A second map holding only the items the current Mode shows. */
    private fun keep(
        items: SparseArray<*>,
        focus: FocusHome,
        screenId: Field,
        container: Field,
    ): SparseArray<Any?> {
        val kept = SparseArray<Any?>(items.size())

        for (index in 0 until items.size()) {
            val item = items.valueAt(index)
            if (item == null || focus.keeps(item, screenId, container)) kept.put(items.keyAt(index), item)
        }
        return kept
    }

    /**
     * Drops the items that would land on a screen the launcher was not given.
     *
     * Only the ones on the workspace itself: an icon in the hotseat or inside a
     * folder names its container rather than a screen, and belongs to every Mode.
     */
    private fun hideItems(
        context: FeatureContext,
        callbacks: Class<*>,
        screenId: Field,
        container: Field,
        focus: FocusHome,
    ) {
        val bindItems = callbacks.declaredMethods.firstOrNull {
            it.name == BIND_ITEMS && it.parameterTypes.size == 2
        }

        if (bindItems == null) {
            context.logger.warn("The launcher's item binding is unavailable; a hidden page may keep its icons")
            return
        }

        context.xposed.hook(bindItems).intercept { chain ->
            val items = chain.args.firstOrNull() as? List<*>

            if (items == null || !focus.isFiltering()) {
                chain.proceed()
            } else {
                val kept = items.filter { item -> item == null || focus.keeps(item, screenId, container) }
                chain.proceed(arrayOf(kept, chain.args.getOrNull(1)))
            }
        }
    }

    /**
     * Stops the launcher laying out a page for a screen the Mode hides.
     *
     * The launcher makes its first page before it is told what to show, so a
     * Mode whose pages do not include that screen would leave an empty one at
     * the front. Filtering the model cannot prevent it: the page is made, and
     * then nothing is put on it.
     *
     * Only screens the Mode hides are skipped. The pages the launcher makes
     * while something is being dragged carry ids of their own that no Mode owns,
     * and they are left alone, so dragging still opens a new page.
     */
    private fun hideEmptyPages(context: FeatureContext, focus: FocusHome) {
        val insert = context.findClass(WORKSPACE)?.declaredMethods?.firstOrNull {
            it.name == INSERT_SCREEN && it.parameterTypes.size == 2 &&
                it.parameterTypes.all { type -> type == Int::class.javaPrimitiveType }
        }

        if (insert == null) {
            context.logger.warn("The launcher's page insertion is unavailable; a Mode may leave an empty page")
            return
        }

        context.xposed.hook(insert).intercept { chain ->
            val screen = chain.args.firstOrNull() as? Int

            if (screen != null && screen >= 0 && focus.isFiltering() && !focus.shows(screen)) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    /** Builds the workspace again when its pages or the active Mode change. */
    private fun watch(
        context: FeatureContext,
        workspace: Class<*>,
        toArray: Method,
        focus: FocusHome,
        previews: FocusPreviewRecorder,
        reveal: FocusPageReveal,
    ) {
        val launcher = context.findClass(LAUNCHER)
        if (launcher == null) {
            context.logger.warn("The launcher activity is unavailable; a Mode applies on the next launcher start")
            return
        }

        val model = launcher.field(MODEL_FIELD)
        if (model == null) {
            context.logger.warn("The launcher's model is unavailable; a Mode applies on the next launcher start")
            return
        }

        val handler = Handler(Looper.getMainLooper())

        // Reading the Modes is slow enough to be seen, so it is never done on
        // the thread that is drawing. See FocusRefresher for what that cost is.
        val refresher = FocusRefresher(
            read = focus::change,
            apply = { change, waitForHomeTransition ->
                applyChange(change, model, context, reveal, waitForHomeTransition)
            },
            main = Executor { handler.post(it) },
        )

        // The activity is remembered from its own hook rather than looked up.
        // The static route to the model is a Dagger singleton the shrinker
        // rewrites; the activity that is running holds the same model in a field
        // that has kept its name.
        context.hookAfter(launcher, ON_RESUME) { activity, _ ->
            if (activity != null) current = WeakReference(activity)
            if (activity is android.app.Activity) {
                activity.window.decorView.postDelayed(
                    { previews.capture(activity) },
                    PREVIEW_CAPTURE_DELAY_MS,
                )
            }
        }

        // Opening Modes from the shade takes window focus without pausing the
        // launcher. Regaining focus is therefore the foreground signal; an
        // onResume-only check misses Modes such as Driving that do not alter DND.
        val baseActivity = context.findClass(BASE_ACTIVITY)
        if (baseActivity == null) {
            context.logger.warn(
                "The launcher's window focus is unavailable; some Mode changes apply on the next reload",
            )
        } else {
            context.hookAfter(
                baseActivity,
                ON_WINDOW_FOCUS_CHANGED,
                Boolean::class.javaPrimitiveType!!,
            ) { activity, args ->
                if (activity === current?.get() && args.firstOrNull() == true) {
                    reveal.onWindowFocused()
                    refresher.request(waitForHomeTransition = true)
                }
            }
        }

        trackCommittedPages(context, workspace, toArray, focus, model, handler)

        watcher = FocusWatcher(context.appContext) {
            refresher.request(waitForHomeTransition = false)
        }.also { it.start(handler) }
    }

    /** Reloads for a change already read off the UI thread. Called on it. */
    private fun applyChange(
        change: FocusChange,
        model: Field,
        context: FeatureContext,
        reveal: FocusPageReveal,
        waitForHomeTransition: Boolean,
    ) {
        // Hide before asking the model to bind. Starting the clip only after
        // the bind lets the completed page draw for a frame first, which makes
        // the transition look instant—most visibly when a Mode turns off.
        if (change.modeChanged) reveal.request(waitForHomeTransition)
        if (!rebuild(model, context) && change.modeChanged) reveal.cancel()
    }

    /**
     * Uses the last stable workspace operation in a complete model bind.
     *
     * Pixel Launcher's named finish-binding callback is currently in an inline
     * generated class. The workspace cleanup it ends with is a stable launcher
     * method, and [FocusPageReveal] ignores every call unless a Mode reload is
     * pending.
     */
    private fun revealAfterBinding(
        context: FeatureContext,
        workspace: Class<*>,
        reveal: FocusPageReveal,
    ) {
        val bindingEnd = workspace.declaredMethods.firstOrNull {
            it.name == REMOVE_EXTRA_EMPTY_SCREEN_DELAYED && it.parameterTypes.size == 3
        }
        if (bindingEnd == null) {
            context.logger.warn("The launcher's binding completion is unavailable; Focus page reveal is disabled")
            return
        }

        context.xposed.hook(bindingEnd).intercept { chain ->
            val result = chain.proceed()
            reveal.onWorkspaceBound(chain.thisObject as? View)
            result
        }
    }

    /**
     * Refreshes the page catalogue when a drag turns the launcher's temporary
     * empty page into a real one.
     *
     * This path mutates `mScreenOrder` directly. It does not call
     * `bindAddScreens`, so observing model callbacks alone misses the new page.
     */
    private fun trackCommittedPages(
        context: FeatureContext,
        workspace: Class<*>,
        toArray: Method,
        focus: FocusHome,
        model: Field,
        handler: Handler,
    ) {
        val commit = workspace.declaredMethods.firstOrNull {
            it.name == COMMIT_EMPTY_SCREENS && it.parameterTypes.isEmpty()
        }
        val screenOrder = workspace.field(SCREEN_ORDER)
        if (commit == null || screenOrder == null) {
            context.logger.warn("The launcher's page commit is unavailable; new pages need a reload to appear in Focus pages")
            return
        }

        context.xposed.hook(commit).intercept { chain ->
            val result = chain.proceed()
            val all = runCatching {
                (toArray.invoke(screenOrder.get(chain.thisObject)) as IntArray).toList()
            }.onFailure { error ->
                context.logger.warn("The new home screen page could not be recorded", error)
            }.getOrNull()

            if (all != null) {
                // The live workspace contains only currently visible pages;
                // merge its new id instead of erasing hidden Focus pages.
                FocusPages.include(all)
                // Finish the drop before asking the model to bind again.
                handler.post {
                    if (focus.hasActiveMode() || focus.hasChanged()) rebuild(model, context)
                }
            }
            result
        }
    }

    /** Asks the launcher's model to bind the workspace again, through the filter. */
    private fun rebuild(model: Field, context: FeatureContext): Boolean = runCatching {
        val activity = current?.get() ?: return false
        val launcherModel = model.get(activity) ?: return false

        launcherModel.javaClass
            .getMethod(FORCE_RELOAD, String::class.java)
            .invoke(launcherModel, RELOAD_REASON)
        true
    }
        .onFailure { context.logger.warn("The workspace could not be rebuilt for the new focus", it) }
        .getOrDefault(false)

    private fun Class<*>.method(name: String, vararg types: Class<*>): Method? =
        runCatching { getMethod(name, *types) }.getOrNull()
            ?: runCatching { getDeclaredMethod(name, *types) }.getOrNull()

    private fun Class<*>.field(name: String): Field? =
        runCatching { getField(name) }.getOrNull()
            ?: runCatching { getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    private companion object {
        const val MODEL_CALLBACKS = "com.android.launcher3.ModelCallbacks"
        const val INT_ARRAY = "com.android.launcher3.util.IntArray"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val WORKSPACE = "com.android.launcher3.Workspace"
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val BASE_ACTIVITY = "com.android.launcher3.BaseActivity"

        const val WORKSPACE_DATA = "com.android.launcher3.model.data.WorkspaceData\$MutableWorkspaceData"
        const val COLLECT_SCREENS = "collectWorkspaceScreens"
        const val BIND_COMPLETE = "bindCompleteModelAsync"
        const val VERSION = "version"
        const val MODIFICATION_ID = "modificationId"
        const val ITEMS = "itemsIdMap"
        const val BIND_SCREENS = "bindAddScreens"
        const val BIND_ITEMS = "bindItems"
        const val STRIP = "stripEmptyScreens"
        const val INSERT_SCREEN = "insertNewWorkspaceScreen"
        const val REMOVE_EXTRA_EMPTY_SCREEN_DELAYED = "removeExtraEmptyScreenDelayed"
        const val COMMIT_EMPTY_SCREENS = "commitExtraEmptyScreens"
        const val SCREEN_ORDER = "mScreenOrder"
        const val ON_RESUME = "onResume"
        const val ON_WINDOW_FOCUS_CHANGED = "onWindowFocusChanged"
        const val WRAP = "wrap"
        const val TO_ARRAY = "toArray"
        const val SCREEN_ID = "screenId"
        const val CONTAINER = "container"
        const val MODEL_FIELD = "mModel"
        const val FORCE_RELOAD = "forceReload"
        const val RELOAD_REASON = "pixel_launcher_evolved_focus"
        const val PREVIEW_CAPTURE_DELAY_MS = 500L
    }
}

/**
 * What the launcher should be showing, and what it was shown last.
 *
 * Reading the Modes starts this module's own app, so it is read when something
 * says to look rather than on every binding call, and the answer stands until
 * the next prompt.
 */
internal class FocusHome(
    private val isEnabled: () -> Boolean,
    private val store: FocusStore,
    private val source: FocusSource,
    private val logger: Logger,
) {

    @Volatile
    private var visible: Set<Int> = emptySet()

    @Volatile
    private var filtering = false

    @Volatile
    private var shownFor: String? = null

    @Volatile
    private var shownScreens: List<Int> = emptyList()

    /**
     * The Modes that were on at the last read, or null before any read at all.
     *
     * Asking the source costs a call into this module's own app, which answers
     * out of a root shell and starts itself first if it has to — between about
     * 130ms and 490ms, measured. Planning runs on every workspace bind, so
     * asking there put that whole cost on the thread doing the binding, and put
     * it there again for a change [change] had already read moments earlier off
     * a background thread. What is on is read once, by whoever asked to look,
     * and the planning works from what that read found.
     */
    @Volatile
    private var activeModes: Set<String>? = null

    /** The screens to bind, out of the ones the launcher would have bound. */
    fun screens(all: List<Int>): List<Int> {
        // Remembered before anything is filtered, because this is the only place
        // the unfiltered order is seen and settings numbers the pages from it.
        FocusPages.remember(all)

        return apply(plan(all), all)
    }

    /** Filters only genuinely new ids from an incremental screen callback. */
    fun addedScreens(added: List<Int>): List<Int> {
        val known = FocusPages.order.toHashSet()
        if (added.none { it >= 0 && it !in known }) return added

        val all = FocusPages.include(added)
        apply(plan(all), all)
        return added.filter { it < 0 || it in visible }
    }

    fun isFiltering(): Boolean = filtering

    fun hasActiveMode(): Boolean = shownFor != null

    /** Whether one screen is among those the Mode that is on shows. */
    fun shows(screen: Int): Boolean = screen in visible

    /**
     * Whether one bound item survives the filter.
     *
     * An item this cannot read is kept. Losing an icon because a field moved is
     * far worse than showing one on a page a Mode meant to hide.
     */
    fun keeps(item: Any, screenId: Field, container: Field): Boolean = runCatching {
        container.getInt(item) != CONTAINER_DESKTOP || screenId.getInt(item) in visible
    }.getOrDefault(true)

    /**
     * What changed since the last workspace bind.
     *
     * This is the one place the Modes are read, and it is called from a
     * background thread before every reload it asks for. See [activeModes].
     */
    fun change(): FocusChange = runCatching {
        read()
        val next = plan(FocusPages.order)
        val modeChanged = next.modeId != shownFor
        FocusChange(
            workspaceChanged = next.screens != shownScreens || modeChanged,
            modeChanged = modeChanged,
        )
    }
        .onFailure { logger.warn("The Mode that is on could not be read", it) }
        .getOrDefault(FocusChange.NONE)

    /** Whether settings, assignments, pages, or active Mode changed the result. */
    fun hasChanged(): Boolean = change().workspaceChanged

    private fun plan(all: List<Int>): FocusState {
        if (!isEnabled()) return FocusState(all, null)

        val assignments = store.assignments()
        val winner = winner(assignments)
        return FocusState(
            screens = FocusPlan.screens(all, assignments, store.priority(), setOfNotNull(winner)),
            modeId = winner,
        )
    }

    private fun apply(state: FocusState, all: List<Int>): List<Int> {
        visible = state.screens.toSet()
        filtering = state.screens.size != all.size
        shownFor = state.modeId
        shownScreens = state.screens
        return state.screens
    }

    private fun winner(assignments: Map<String, Set<Int>>): String? = FocusPlan.winner(
        assignments = assignments,
        priority = store.priority(),
        // Read here only when nothing has been read yet, which is the launcher's
        // first bind. Showing the ordinary home screen and correcting it a
        // moment later would be a worse first frame than one slow one.
        active = activeModes ?: read(),
    )

    /** Asks the source what is on now, and remembers it for the planning. */
    private fun read(): Set<String> =
        source.modes().filter { it.isActive }.map { it.id }.toSet().also { activeModes = it }

    private companion object {
        /**
         * `LauncherSettings.Favorites.CONTAINER_DESKTOP`.
         *
         * A compile-time constant, so the launcher's own copy is inlined
         * everywhere and no field is left to read it from.
         */
        const val CONTAINER_DESKTOP = -100
    }

    private data class FocusState(val screens: List<Int>, val modeId: String?)
}

internal data class FocusChange(
    val workspaceChanged: Boolean,
    val modeChanged: Boolean,
) {
    companion object {
        val NONE = FocusChange(workspaceChanged = false, modeChanged = false)
    }
}
