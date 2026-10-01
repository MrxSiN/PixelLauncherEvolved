package my.github.MrxSiN.pixellauncherevolved.feature.grid

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Point
import android.graphics.Rect

import java.util.concurrent.ConcurrentHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.dock.Dock
import my.github.MrxSiN.pixellauncherevolved.feature.layout.LiveLayout
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * The launcher's grid as Grid & size sees it: what the launcher's own grid is,
 * what this screen can take, and what the Home screen holds.
 *
 * ```
 * InvariantDeviceProfile.INSTANCE.get(context)
 *   int numRows, numColumns; List<DeviceProfile> supportedProfiles
 * DeviceProfile.workspaceProfile -> WorkspaceProfile
 *   int cellLayoutWidthSpecification, cellLayoutHeightSpecification, iconTextSizePx
 *   Rect cellLayoutPaddingPx; Point cellLayoutBorderSpacePx
 * DeviceProfile.allAppsProfile -> AllAppsProfile
 *   int cellWidthPx, numShownAllAppsColumns; Point borderSpacePx
 * LauncherAppState.INSTANCE.get(context).model -> LauncherModel
 * ```
 *
 * The fields are written by [GridFeature]'s grid hook, once per grid the
 * launcher reads. Everything else runs on a settings change, never per frame.
 */
internal object Grid {

    /**
     * Whether [idp] is a Wallpaper & style preview's `InvariantDeviceProfile`
     * rather than the launcher's own: a preview's `PreviewContext` gives it
     * `ProxyPrefs`, a copy of the launcher's prefs it writes to alone.
     *
     * The fields here describe the launcher's own grid, so only its own
     * profile records them; a preview of XL would otherwise leave the launcher
     * looking like a 2 x 2 grid.
     */
    fun isPreview(idp: Any?): Boolean {
        idp ?: return false
        val prefs = runCatching { Reflect.field(idp.javaClass, "mPrefs")?.get(idp) }.getOrNull() ?: return false
        return prefs.javaClass.name == PROXY_PREFS
    }

    /** Whether the `initGrid` running on this thread is the launcher's own; null outside one. */
    @JvmField val ownInit = ThreadLocal<Boolean>()

    /**
     * The launcher's own phone grid, recorded each time it reads one of
     * Google's; 0 until then. Started on the custom grid, the launcher's pick
     * for it stands in.
     */
    @Volatile @JvmField var stockColumns = 0
    @Volatile @JvmField var stockRows = 0
    @Volatile @JvmField var stockAppsColumns = 0
    // ponytail: unknown until the launcher reads one of Google's grids; started on the custom
    // one, it is parsed from the launcher's own pick for it, as before.
    @Volatile @JvmField var stockName: String? = null

    /** The columns and rows the grid has after Grid & size, before the dock's row is added. */
    @Volatile @JvmField var appliedColumns = 0
    @Volatile @JvmField var appliedRows = 0

    /** Whether the launcher's own grid is the custom one ([my.github.MrxSiN.pixellauncherevolved.bridge.GridBridge.CUSTOM]). */
    @Volatile @JvmField var custom = false

    /** Why the launcher's grid is not one Grid & size changes; [Support.PHONE] when it is. */
    @Volatile @JvmField var support = Support.UNKNOWN

    enum class Support { UNKNOWN, PHONE, LANDSCAPE, OTHER }

    /**
     * Sizes this module made databases for, packed by [pack]. The launcher only
     * migrates between the sizes it ships, so these are let through ([GridFeature]).
     */
    private val ownSizes: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun pack(columns: Int, rows: Int): Int = columns * PACK + rows

    fun addOwnSize(columns: Int, rows: Int) {
        ownSizes += pack(columns, rows)
    }

    fun isOwnSize(columns: Int, rows: Int): Boolean = pack(columns, rows) in ownSizes

    /** The columns and rows [settings] ask of a launcher grid of [columns] by [rows]. */
    fun wanted(settings: SettingsSource, columns: Int, rows: Int): Point = Point(
        GridSpec.count(settings[Settings.GRID_COLUMNS], columns),
        GridSpec.count(settings[Settings.GRID_ROWS], rows),
    )

    /** What this screen can hold, measured on the launcher's portrait profile. */
    class Room(val columns: Int, val rows: Int, val appsColumns: Int)

    /**
     * How many columns, rows and All apps columns fit, each cell keeping a 48dp
     * touch target (and, down, one line of label), with the gaps as they are
     * now; spacing moves cells, never resizes them. Measured once per page;
     * null when the launcher has no phone profile to measure.
     */
    fun room(context: Context, classLoader: ClassLoader): Room? {
        val idp = LiveLayout.singleton(classLoader, IDP, context) ?: return null
        val profiles = field(idp, "supportedProfiles") as? List<*> ?: return null
        val profile = profiles.firstOrNull { it != null && !isLandscape(it) } ?: return null
        val workspace = field(profile, "workspaceProfile") ?: return null
        val padding = field(workspace, "cellLayoutPaddingPx") as? Rect ?: return null
        val gaps = field(workspace, "cellLayoutBorderSpacePx") as? Point ?: return null
        val width = int(workspace, "cellLayoutWidthSpecification") - padding.left - padding.right
        val height = int(workspace, "cellLayoutHeightSpecification") - padding.top - padding.bottom
        val label = int(workspace, "iconTextSizePx")

        val density = context.resources.displayMetrics.density
        val touch = TOUCH_DP * density
        val gapX = gaps.x.toFloat()
        val gapY = gaps.y.toFloat()
        // A hidden dock gives its row to the grid; that row is the dock's, not one to offer.
        val extra = (int(idp, "numRows") - appliedRows).coerceAtLeast(0)

        val apps = field(profile, "allAppsProfile")
        val appsColumns = apps?.let {
            val shown = int(it, "numShownAllAppsColumns")
            val gap = (field(it, "borderSpacePx") as? Point)?.x ?: 0
            GridSpec.fits(int(it, "cellWidthPx") * shown + gap * (shown - 1), touch, gap.toFloat())
        } ?: GridSpec.FEWEST

        return Room(
            columns = GridSpec.fits(width, touch, gapX),
            rows = (GridSpec.fits(height, touch + label * LABEL_LINE, gapY) - extra).coerceAtLeast(GridSpec.FEWEST),
            appsColumns = appsColumns,
        )
    }

    /**
     * Every Home screen item, on every page including ones a Mode hides, as
     * far as fitting it into a grid goes. Null when the launcher's model cannot
     * be reached, which refuses a smaller grid rather than risk one.
     */
    fun homeItems(context: Context, classLoader: ClassLoader): List<GridSpec.Item>? {
        val state = LiveLayout.singleton(classLoader, APP_STATE, context) ?: return null
        val model = field(state, "model") ?: return null
        val widgets = AppWidgetManager.getInstance(context)
        return Dock.modelItems(model).filter { int(it, "container") == Dock.CONTAINER_DESKTOP }.map { item ->
            val id = Reflect.field(item.javaClass, "appWidgetId")?.getInt(item)
            val title = field(item, "title") as? CharSequence
                ?: id?.let { runCatching { widgets.getAppWidgetInfo(it)?.loadLabel(context.packageManager) }.getOrNull() }
                ?: ""
            GridSpec.Item(title, int(item, "minSpanX").coerceAtLeast(1), int(item, "minSpanY").coerceAtLeast(1))
        }
    }

    private fun isLandscape(profile: Any): Boolean {
        val properties = field(profile, "deviceProperties") ?: return false
        return Reflect.field(properties.javaClass, "isLandscape")?.getBoolean(properties) == true
    }

    // ponytail: uncached lookups, every caller is a settings page or a settings change.
    private fun field(owner: Any, name: String): Any? = Reflect.field(owner.javaClass, name)?.get(owner)

    private fun int(owner: Any, name: String): Int = Reflect.field(owner.javaClass, name)?.getInt(owner) ?: 0

    private const val PROXY_PREFS = "com.android.launcher3.ProxyPrefs"
    private const val PACK = 100
    private const val TOUCH_DP = 48f

    /** One line of label, in label sizes, under the icon's touch target. */
    private const val LABEL_LINE = 1.25f

    private const val IDP = "com.android.launcher3.InvariantDeviceProfile"
    private const val APP_STATE = "com.android.launcher3.LauncherAppState"
}
