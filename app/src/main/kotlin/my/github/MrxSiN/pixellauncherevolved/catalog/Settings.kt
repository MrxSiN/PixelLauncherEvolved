package my.github.MrxSiN.pixellauncherevolved.catalog

/**
 * Every preference key in one place.
 *
 * Keys are stable strings: renaming one silently resets that tweak on every
 * device that already stored it.

 */
object Settings {

    val TASKBAR_ONLY = BoolSetting("taskbar_only", default = false)
    val TABLET_MODE = BoolSetting("tablet_mode", default = false)

    /**
     * Lays Recents out as the grid a tablet shows, while the workspace keeps
     * its phone grid and no taskbar appears. One of the answers in
     * [my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode].
     */
    val OVERVIEW_ONLY = BoolSetting("overview_only", default = false)

    /** Blurs the wallpaper while the home screen is the state the launcher is in. */
    val HOME_BLUR_WALLPAPER = BoolSetting("home_blur_wallpaper", default = false)

    /**
     * How strong that blur is, as a percentage of the deepest the launcher's
     * own blur goes. The middle is what the tweak did before it could be
     * changed. The range is the one the launcher's slider row is built with;
     * see [my.github.MrxSiN.pixellauncherevolved.feature.settings.PreferenceApi].
     */
    val HOME_BLUR_STRENGTH = IntSetting("home_blur_strength", default = 50, range = 0..100)
    val HOME_DOUBLE_TAP_TO_SLEEP = BoolSetting("home_double_tap_to_sleep", default = false)

    /**
     * Whether app icons come from an installed icon pack rather than from
     * Google's own icon pipeline.
     *
     * Stored as a switch because there are two answers and one of them is the
     * stock behaviour, the same way the layout modes are stored. Which pack, and
     * which apps carry an icon of their own, are not switches and live in
     * [my.github.MrxSiN.pixellauncherevolved.icons.IconSourceSettings] and
     * [my.github.MrxSiN.pixellauncherevolved.icons.IconOverrideStore], in this
     * same preference file.
     */
    val ICONS_USE_PACK = BoolSetting("home_icons_use_pack", default = false)

    /**
     * Two taps on the status bar, which is SystemUI's surface rather than the
     * launcher's. The switch is here with the home screen one because that is
     * where a person looks for it; the gesture is watched in SystemUI.
     */
    val STATUS_BAR_DOUBLE_TAP_TO_SLEEP =
        BoolSetting("status_bar_double_tap_to_sleep", default = false)
    val HOME_SEARCH_OPENS_DRAWER = BoolSetting("home_search_opens_drawer", default = false)

    /**
     * Hides the dock's icons on the home screen. The pinned apps stay in the
     * launcher's database, so showing the dock again brings them back.
     */
    val DOCK_HIDDEN = BoolSetting("home_dock_hidden", default = false)

    /**
     * How many icons the dock shows; 0 is the launcher's own count. Only a
     * count below the launcher's is applied, so the dock's database capacity
     * never changes and no pinned app can be dropped by a grid migration. See
     * [my.github.MrxSiN.pixellauncherevolved.feature.dock.DockIcons].
     */
    val DOCK_ICONS = IntSetting("home_dock_icons", default = 0, range = 0..16)

    /**
     * Whether a lower dock count moves the pinned apps it leaves out to the Home
     * screen. Off, they stay pinned in dock slots the dock no longer shows, and
     * [DOCK_KEPT] keeps those slots from being dropped.
     */
    val DOCK_MOVE_TO_HOME = BoolSetting("home_dock_move_to_home", default = true)

    /**
     * Dock slots still holding apps a lower count hid, so the dock's database
     * capacity is not lowered below them. This device's own dock state: not
     * backed up and not reset.
     */
    val DOCK_KEPT = IntSetting("home_dock_kept", default = 0, range = 0..16)

    /**
     * Grid & size. Each is a logical value, never pixels, and 0 (or 100 for
     * the icon size) is the launcher's own, so a phone that cannot take a
     * value falls back to stock rather than to a broken grid. See
     * [my.github.MrxSiN.pixellauncherevolved.feature.grid.GridSpec].
     */
    val GRID_COLUMNS = IntSetting("home_grid_columns", default = 0, range = 0..10)
    val GRID_ROWS = IntSetting("home_grid_rows", default = 0, range = 0..10)

    /** The Home screen icon size as a percentage of the launcher's own; All apps, the dock and folders follow it. */
    val GRID_ICON_SIZE = IntSetting("home_grid_icon_size", default = 100, range = 0..200)

    /** The gap between Home screen cells across and down: -1 Compact, 0 Default, 1 Relaxed. */
    val GRID_SPACING_X = IntSetting("home_grid_spacing_x", default = 0, range = -1..1)
    val GRID_SPACING_Y = IntSetting("home_grid_spacing_y", default = 0, range = -1..1)

    /** Columns in All apps; 0 is the launcher's own. */
    val APP_DRAWER_COLUMNS = IntSetting("app_drawer_columns", default = 0, range = 0..10)

    /** Every Grid & size setting, for reset, Safe Mode and the live rebuild. */
    val GRID: List<IntSetting> = listOf(GRID_COLUMNS, GRID_ROWS, GRID_ICON_SIZE, GRID_SPACING_X, GRID_SPACING_Y, APP_DRAWER_COLUMNS)

    /**
     * Parts of the app drawer's search results a person can switch off. Each
     * one names a group the platform's search service returns; hiding a group
     * hides its heading with it.
     */
    val APP_DRAWER_SEARCH_HIDE_WEB = BoolSetting("app_drawer_search_hide_web", default = false)
    val APP_DRAWER_SEARCH_HIDE_PLAY_STORE =
        BoolSetting("app_drawer_search_hide_play_store", default = false)
    val APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS =
        BoolSetting("app_drawer_search_hide_search_in_apps", default = false)

    /**
     * Which pages each Mode shows is not here, because it is neither a switch
     * nor one key. It lives in
     * [my.github.MrxSiN.pixellauncherevolved.focus.FocusStore], in the same
     * preference file.
     */
    val FOCUS_HOME_SCREENS = BoolSetting("focus_home_screens", default = false)
    val OVERVIEW_HIDE_TASKBAR_ALL_APPS =
        BoolSetting("overview_hide_taskbar_all_apps", default = false)

    val OVERVIEW_BUBBLE_BUTTON = BoolSetting("overview_bubble_button", default = true)

    /**
     * Starts the launcher's own split selection from a task card. It shares the
     * thumbnail's corner with [OVERVIEW_BUBBLE_BUTTON], sitting to the left of
     * that one while both are on.
     */
    val OVERVIEW_SPLIT_BUTTON = BoolSetting("overview_split_button", default = true)
    val OVERVIEW_HIDE_SCREENSHOT = BoolSetting("overview_hide_screenshot", default = false)
    val OVERVIEW_HIDE_SELECT = BoolSetting("overview_hide_select", default = false)
    val OVERVIEW_CLEAR_ALL_IN_ACTIONS = BoolSetting("overview_clear_all_in_actions", default = false)
}
