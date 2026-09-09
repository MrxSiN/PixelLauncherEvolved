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
    val HOME_SEARCH_OPENS_DRAWER = BoolSetting("home_search_opens_drawer", default = false)

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
    val OVERVIEW_HIDE_SCREENSHOT = BoolSetting("overview_hide_screenshot", default = false)
    val OVERVIEW_HIDE_SELECT = BoolSetting("overview_hide_select", default = false)
    val OVERVIEW_CLEAR_ALL_IN_ACTIONS = BoolSetting("overview_clear_all_in_actions", default = false)
}
