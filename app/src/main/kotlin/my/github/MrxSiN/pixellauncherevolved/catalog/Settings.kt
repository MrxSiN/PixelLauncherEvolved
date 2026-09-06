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
}
