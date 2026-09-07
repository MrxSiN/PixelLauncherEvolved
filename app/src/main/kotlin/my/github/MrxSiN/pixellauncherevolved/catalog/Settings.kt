package my.github.MrxSiN.pixellauncherevolved.catalog

/**
 * Every preference key in one place.
 *
 * Keys are stable strings: renaming one silently resets that tweak on every
 * device that already stored it.
 *
 * Blur Wallpaper is not here. Its switch is in Wallpaper & Style rather than in
 * Home settings, and the two apps cannot see this module's store, so it keeps
 * its key with the setting itself in
 * [my.github.MrxSiN.pixellauncherevolved.wallpaper.WallpaperBlur].
 */
object Settings {

    val TASKBAR_ONLY = BoolSetting("taskbar_only", default = false)
    val TABLET_MODE = BoolSetting("tablet_mode", default = false)
    val HOME_DOUBLE_TAP_TO_SLEEP = BoolSetting("home_double_tap_to_sleep", default = false)
    val HOME_SEARCH_OPENS_DRAWER = BoolSetting("home_search_opens_drawer", default = false)
    val OVERVIEW_HIDE_TASKBAR_ALL_APPS =
        BoolSetting("overview_hide_taskbar_all_apps", default = false)

    val OVERVIEW_BUBBLE_BUTTON = BoolSetting("overview_bubble_button", default = true)
    val OVERVIEW_HIDE_SCREENSHOT = BoolSetting("overview_hide_screenshot", default = false)
    val OVERVIEW_HIDE_SELECT = BoolSetting("overview_hide_select", default = false)
    val OVERVIEW_CLEAR_ALL_IN_ACTIONS = BoolSetting("overview_clear_all_in_actions", default = false)
}
