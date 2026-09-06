package my.github.MrxSiN.pixellauncherevolved.catalog

/**
 * Every preference key in one place.
 *
 * Keys are stable strings: renaming one silently resets that tweak on every
 * device that already stored it.
 */
object Settings {

    val OVERVIEW_BUBBLE_BUTTON = BoolSetting("overview_bubble_button", default = true)
    val OVERVIEW_HIDE_SCREENSHOT = BoolSetting("overview_hide_screenshot", default = false)
    val OVERVIEW_HIDE_SELECT = BoolSetting("overview_hide_select", default = false)
    val OVERVIEW_HIDE_CLEAR_ALL = BoolSetting("overview_hide_clear_all", default = false)
    val OVERVIEW_CLEAR_ALL_IN_MENU = BoolSetting("overview_clear_all_in_menu", default = false)

    /**
     * Not a setting but a signal.
     *
     * The settings app increments this to ask the launcher to restart itself.
     * It travels on the preference channel because that is the one route into
     * the launcher process that needs no permission and no exported receiver.
     */
    val RESTART_REQUEST = IntSetting("restart_request", default = 0, range = 0..Int.MAX_VALUE)
}
