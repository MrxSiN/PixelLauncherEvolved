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
    val HOME_DOUBLE_TAP_TO_SLEEP = BoolSetting("home_double_tap_to_sleep", default = false)
    val HOME_SEARCH_OPENS_DRAWER = BoolSetting("home_search_opens_drawer", default = false)

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
    val OVERVIEW_HIDE_SCREENSHOT = BoolSetting("overview_hide_screenshot", default = false)
    val OVERVIEW_HIDE_SELECT = BoolSetting("overview_hide_select", default = false)
    val OVERVIEW_CLEAR_ALL_IN_ACTIONS = BoolSetting("overview_clear_all_in_actions", default = false)
}
