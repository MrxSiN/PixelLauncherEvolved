package my.github.MrxSiN.pixellauncherevolved.feature.dock

import android.content.SharedPreferences
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.grid.Grid
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Hides the dock and gives its row to the Home screen, and sets how many icons
 * the dock holds. Both apply live.
 *
 * Hiding makes the icon container INVISIBLE, which takes it out of drawing,
 * touch and accessibility, and which the launcher's own
 * `Hotseat.isValidDropTarget` already reads as "nothing may be dropped here".
 * The search bar is a sibling of that container and stays. The icon row
 * becomes one more Home screen row: `InvariantDeviceProfile.initGrid` gets one
 * more `numRows`, and each `DeviceProfileBuilder.build` gives the dock's
 * reserved height (`HotseatProfile.barSizePx`) and the workspace's bottom
 * padding back by the icon row. `DeviceGridState` is kept on the grid's own
 * row count, so the launcher does not run its grid migration, which would move
 * every item down a row. Showing the dock again first moves anything in that
 * row, on every page, to the rows above or to a new page.
 *
 * The count is written into each profile's copy of the grid spec
 * (`DisplayOptionSpec.mapTypeIndex`) and the dock's database capacity raised
 * after `initGrid` for a count above the grid's; see [DockIcons]. A lower count
 * moves the pinned apps it leaves out to the Home screen, or, with Move to Home
 * screen off, keeps their slots in the capacity so the launcher never deletes them.
 *
 * Every change goes through `InvariantDeviceProfile.onConfigChanged`, the
 * launcher's own path for a grid picked in Wallpaper & style. Nothing is
 * hidden while a taskbar is present, because the taskbar mirrors the dock.
 * Every hook here runs once per grid build, profile build, dock layout or bind,
 * never per frame.
 */
class DockFeature : LauncherFeature, SharedPreferences.OnSharedPreferenceChangeListener {

    override val id: String = "home_dock"
    override val compatibility = CompatibilityFeature.DOCK

    /** Read by [onSharedPreferenceChanged], so it stays a field the shrinker keeps. */
    private var launcher: FeatureContext? = null

    /** The grid's own rows and dock capacity, per `InvariantDeviceProfile` (previews build their own). */
    private val stockRowsOf = WeakHashMap<Any, Int>()

    override fun isEnabled(settings: SettingsSource): Boolean = settings[Settings.DOCK_HIDDEN]

    override fun install(context: FeatureContext) {
        launcher = context
        val hotseat = requireNotNull(context.findClass(Dock.HOTSEAT))
        val icons = context.analysis.isAvailable(CompatibilityFeature.DOCK_ICONS)

        context.xposed.hook(Reflect.declaredMethod(hotseat, "setInsets", Rect::class.java)).intercept { chain ->
            chain.proceed().also {
                val view = chain.thisObject as ViewGroup
                Dock.remember(view)
                apply(context, view)
                if (icons && context.settings[Settings.DOCK_ICONS] != 0) view.post { fitDock(context) }
            }
        }
        // A rebuilt grid lays the dock out again, which shows its icons.
        context.xposed.hook(Reflect.declaredMethod(hotseat, "resetLayout", Boolean::class.javaPrimitiveType!!)).intercept { chain ->
            chain.proceed().also { apply(context, chain.thisObject as ViewGroup) }
        }
        LauncherSettings.preferences(context.appContext).registerOnSharedPreferenceChangeListener(this)

        installAlpha(context)
        installGrid(context, icons)
        installGridState(context)
        installProfile(context)
        if (icons) {
            installCount(context)
            DockIconSize.install(context)
        }

        val chosen = context.settings[Settings.DOCK_ICONS]
        context.logger.info("Dock: hidden=${context.settings[Settings.DOCK_HIDDEN]}, icons=${if (chosen == 0) "system" else chosen}")
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        val context = launcher ?: return
        val hotseat = Dock.hotseat() ?: return
        when (key) {
            Settings.DOCK_HIDDEN.key -> hotseat.post {
                if (!context.settings[Settings.DOCK_HIDDEN]) moveRowOut(context, hotseat)
                apply(context, hotseat)
                rebuild(context, hotseat)
            }
            Settings.DOCK_ICONS.key -> hotseat.post {
                leaveOut(context, hotseat)
                rebuild(context, hotseat)
            }
        }
    }

    private fun rebuild(context: FeatureContext, hotseat: ViewGroup) {
        runCatching { Dock.rebuildGrid(hotseat) }
            .onFailure { context.logger.warn("Unable to rebuild the grid live; it changes on the next launcher start", it) }
        // The rebuilt dock may be a new view; suggested apps are re-bound, not re-added.
        hotseat.post { fitDock(context) }
    }

    private fun fitDock(context: FeatureContext) {
        val hotseat = Dock.hotseat() ?: return
        runCatching { DockIconSize.fitAll(hotseat) }
            .onFailure { context.logger.warn("Unable to size the dock's icons", it) }
    }

    /**
     * Shows or hides the dock's icons.
     *
     * The icon container's alpha and visibility belong to the launcher's
     * `Hotseat.mIconsAlphaChannels`, a `MultiValueAlpha` that sets both on every
     * `apply` (state changes, grid rebuilds, the home animation). A hidden dock
     * therefore answers 0 through that same `apply` ([installAlpha]), which
     * makes the launcher itself keep the container INVISIBLE; setting it here
     * only covers the time until its next `apply`.
     */
    private fun apply(context: FeatureContext, hotseat: ViewGroup) {
        runCatching {
            val icons = Dock.icons(hotseat) ?: return
            val hidden = context.settings[Settings.DOCK_HIDDEN] && !Dock.hasTaskbar(hotseat)
            if (hidden) {
                Dock.hiddenIcons = icons
                icons.alpha = 0f
                icons.visibility = View.INVISIBLE
            } else {
                if (Dock.hiddenIcons === icons) {
                    icons.alpha = 1f
                    icons.visibility = View.VISIBLE
                }
                if (Dock.hiddenIcons === icons || Dock.hiddenIcons?.isAttachedToWindow != true) Dock.hiddenIcons = null
            }
        }.onFailure { context.logger.warn("Unable to show or hide the dock", it) }
    }

    /**
     * Holds a hidden dock's icons at alpha 0 through the launcher's own (its view is
     * `MultiPropertyFactory.mTarget`)
     * `MultiValueAlpha.apply(float)`, which then sets them INVISIBLE itself.
     *
     * Every `MultiValueAlpha` in the launcher goes through this call, on frames
     * of state animations too, so a shown dock costs one volatile read and the
     * original call.
     */
    private fun installAlpha(context: FeatureContext) {
        val alpha = requireNotNull(Host.cls(context.classLoader, MULTI_VALUE_ALPHA))
        val targetOf = requireNotNull(Reflect.field(alpha, "mTarget"))
        val apply = Reflect.declaredMethod(alpha, "apply", Float::class.javaPrimitiveType!!)
        context.xposed.hook(apply).intercept { chain ->
            val hidden = Dock.hiddenIcons
            if (hidden == null || targetOf.get(chain.thisObject) !== hidden) chain.proceed() else chain.proceed(HIDDEN_ALPHA)
        }
    }

    /** Before the dock comes back, moves what is in the row it gave the Home screen. */
    private fun moveRowOut(context: FeatureContext, hotseat: ViewGroup) {
        runCatching {
            val rows = Dock.stockRows.takeIf { it > 0 } ?: return
            if ((Dock.rows(hotseat) ?: rows) <= rows) return
            val below = Dock.homeItemsBelow(hotseat, rows)
            if (below.isEmpty()) return
            if (Dock.moveToHome(hotseat, below, rows)) {
                context.logger.info("Dock: moved ${below.size} item(s) out of the dock's row")
            } else {
                context.logger.warn("Dock: the launcher could not be reached to move ${below.size} item(s) out of the dock's row")
            }
        }.onFailure { context.logger.warn("Unable to move items out of the dock's row", it) }
    }

    /** Moves the pinned apps a lower count leaves out, or keeps their slots. */
    private fun leaveOut(context: FeatureContext, hotseat: ViewGroup) {
        runCatching {
            val stock = Dock.stockIcons(hotseat) ?: return
            val count = DockIcons.shown(context.settings[Settings.DOCK_ICONS], stock)
            if (count >= context.settings[Settings.DOCK_KEPT]) context.settings.put(Settings.DOCK_KEPT, 0)
            val left = Dock.pinnedFrom(hotseat, count)
            if (left.isEmpty()) return

            if (context.settings[Settings.DOCK_MOVE_TO_HOME]) {
                val rows = Dock.rows(hotseat) ?: return
                Dock.moveToHome(hotseat, left, rows)
                context.logger.info("Dock: moved ${left.size} app(s) past slot $count to the Home screen")
            } else {
                val kept = left.maxOf(Dock::rankOf) + 1
                context.settings.put(Settings.DOCK_KEPT, maxOf(kept, context.settings[Settings.DOCK_KEPT]))
                context.logger.info("Dock: kept ${left.size} app(s) past slot $count pinned and out of sight")
            }
        }.onFailure { context.logger.warn("Unable to handle the dock's apps past the new count", it) }
    }

    /**
     * Records the grid's own rows and dock capacity, then adds the dock's row
     * while it is hidden and raises the capacity for a bigger dock.
     *
     * `initGrid` sets `numRows` from the grid option and then builds every
     * device profile itself (`newDPBuilder`, then `build`), so the row is added
     * at the first `newDPBuilder` of each `initGrid`, before any profile is
     * measured; after `initGrid` for a grid that built none.
     */
    private fun installGrid(context: FeatureContext, icons: Boolean) {
        val grid = requireNotNull(Host.cls(context.classLoader, INVARIANT_DEVICE_PROFILE))
        val rowsOf = Reflect.declaredField(grid, "numRows")
        val capacityOf = Reflect.declaredField(grid, "numDatabaseHotseatIcons")
        val fixedLandscapeOf = Reflect.field(grid, "isFixedLandscape")

        fun adjust(profile: Any) {
            if (stockRowsOf.containsKey(profile)) return
            runCatching {
                val rows = rowsOf.getInt(profile)
                stockRowsOf[profile] = rows
                // A Wallpaper & style preview's grid is not the launcher's dock.
                val own = !Grid.isPreview(profile)
                if (own) Dock.stockRows = rows
                // Landscape mode puts the search bar in the dock's row, so
                // there is no row to give; the icons are only hidden.
                val give = context.settings[Settings.DOCK_HIDDEN] && fixedLandscapeOf?.getBoolean(profile) != true
                rowsOf.setInt(profile, DockIcons.rows(rows, give))

                if (icons) {
                    val stock = capacityOf.getInt(profile)
                    if (own) Dock.stock = stock
                    val capacity = DockIcons.capacity(
                        context.settings[Settings.DOCK_ICONS],
                        stock,
                        context.settings[Settings.DOCK_KEPT],
                    )
                    capacityOf.setInt(profile, capacity)
                }
            }.onFailure { context.logger.warn("Unable to set the grid for the dock", it) }
        }

        context.xposed.hook(Reflect.declaredMethod(grid, "initGrid", String::class.java)).intercept { chain ->
            stockRowsOf.remove(chain.thisObject)
            chain.proceed().also { adjust(chain.thisObject) }
        }
        context.xposed.hook(requireNotNull(Reflect.declared(grid, "newDPBuilder") { it.parameterTypes.size == 1 })).intercept { chain ->
            adjust(chain.thisObject)
            chain.proceed()
        }
    }

    /**
     * Keeps the grid state the launcher compares for a migration on the grid's
     * own rows.
     *
     * `DeviceGridState(InvariantDeviceProfile)` records `"columns,rows"` in
     * `mGridSizeString`; a taller grid than the one written down makes the
     * launcher move every Home screen item down a row. The extra row is this
     * module's, so it is left out of that record.
     */
    private fun installGridState(context: FeatureContext) {
        val grid = requireNotNull(Host.cls(context.classLoader, INVARIANT_DEVICE_PROFILE))
        val state = requireNotNull(Host.cls(context.classLoader, DEVICE_GRID_STATE))
        val sizeOf = Reflect.declaredField(state, "mGridSizeString")
        val columnsOf = Reflect.declaredField(grid, "numColumns")
        val rowsOf = Reflect.declaredField(grid, "numRows")

        context.xposed.hook(state.getDeclaredConstructor(grid)).intercept { chain ->
            chain.proceed().also {
                runCatching {
                    val profile = chain.args[0] ?: return@runCatching
                    val stock = stockRowsOf[profile] ?: return@runCatching
                    if (rowsOf.getInt(profile) != stock) {
                        sizeOf.set(chain.thisObject, "${columnsOf.getInt(profile)},$stock")
                    }
                }.onFailure { context.logger.warn("Unable to keep the grid state on the grid's own rows", it) }
            }
        }
    }

    /**
     * Adjusts each profile built for the dock: whole cells between icons for a
     * dock past what fits, and, with the dock hidden and no taskbar, its icon
     * row given to the Home screen.
     *
     * `DeviceProfileBuilder.build` spreads the icons across `HotseatProfile.widthPx`
     * and answers the room between them as `borderSpace`, which goes negative
     * past what fits (-7px at 6 icons on a 1149px dock); it is held at
     * [DockIconSize.GAP_DP] and [DockIconSize] shrinks the icons into the cells.
     * The hidden dock's row is its `cellHeightPx`: taken off `barSizePx`, which
     * sizes the dock's view (the search bar is placed from the bottom), and off
     * the workspace's bottom padding, which the Home screen pages grow into.
     */
    private fun installProfile(context: FeatureContext) {
        val builder = requireNotNull(Host.cls(context.classLoader, BUILDER))
        val gap = DockIconSize.gapPx()
        context.xposed.hook(Reflect.declaredMethod(builder, "build")).intercept { chain ->
            chain.proceed()?.also { profile ->
                runCatching {
                    val dock = Reflect.field(profile.javaClass, "hotseatProfile")?.get(profile) ?: return@runCatching
                    if (context.settings[Settings.DOCK_ICONS] != 0) {
                        val space = Reflect.field(dock.javaClass, "borderSpace")
                        if (space != null && space.getInt(dock) < gap) space.setInt(dock, gap)
                    }
                    if (context.settings[Settings.DOCK_HIDDEN] && !Dock.hasTaskbar(profile)) giveRow(profile, dock)
                }.onFailure { context.logger.warn("Unable to lay the dock out", it) }
            }
        }
    }

    private fun giveRow(profile: Any, dock: Any) {
        // A dock down the side of a landscape phone has no row to give; its
        // bar is a width there, and the Home screen's bottom padding is not its.
        val properties = Reflect.field(profile.javaClass, "deviceProperties")?.get(profile) ?: return
        if (Reflect.method(properties.javaClass, "isVerticalBarLayout")?.invoke(properties) != false) return
        if (Reflect.field(dock.javaClass, "isQsbInline")?.getBoolean(dock) != false) return
        val grid = Reflect.field(profile.javaClass, "inv")?.get(profile) ?: return
        val stock = stockRowsOf[grid] ?: return
        if ((Reflect.field(grid.javaClass, "numRows")?.getInt(grid) ?: stock) <= stock) return

        val row = Reflect.field(dock.javaClass, "cellHeightPx")?.getInt(dock) ?: return
        val bar = Reflect.field(dock.javaClass, "barSizePx") ?: return
        val workspace = Reflect.field(profile.javaClass, "workspaceProfile")?.get(profile) ?: return
        val padding = Reflect.field(workspace.javaClass, "workspacePadding")?.get(workspace) as? Rect ?: return
        val height = Reflect.field(workspace.javaClass, "cellLayoutHeightSpecification")

        bar.setInt(dock, bar.getInt(dock) - row)
        padding.bottom -= row
        height?.let { it.setInt(workspace, it.getInt(workspace) + row) }
    }

    private fun installCount(context: FeatureContext) {
        val spec = requireNotNull(Host.cls(context.classLoader, DISPLAY_OPTION_SPEC))
        val shownOf = Reflect.declaredField(spec, "numShownHotseatIcons")
        val map = Reflect.declaredMethod(
            spec,
            "mapTypeIndex",
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
        )

        context.xposed.hook(map).intercept { chain ->
            chain.proceed()?.also { copy ->
                runCatching {
                    val stock = shownOf.getInt(copy)
                    shownOf.setInt(copy, DockIcons.shown(context.settings[Settings.DOCK_ICONS], stock))
                }.onFailure { context.logger.warn("Unable to set the dock's icon count", it) }
            }
        }
    }

    private companion object {
        const val BUILDER = "com.android.launcher3.deviceprofile.DeviceProfileBuilder"
        const val DISPLAY_OPTION_SPEC = "com.android.launcher3.deviceprofile.parser.DisplayOptionSpec"
        const val INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile"
        const val DEVICE_GRID_STATE = "com.android.launcher3.model.DeviceGridState"
        const val MULTI_VALUE_ALPHA = "com.android.launcher3.util.MultiValueAlpha"
        val HIDDEN_ALPHA = arrayOf<Any?>(0f)
    }
}
