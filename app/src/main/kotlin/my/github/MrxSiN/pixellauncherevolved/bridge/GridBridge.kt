package my.github.MrxSiN.pixellauncherevolved.bridge

/**
 * How Wallpaper & style asks the launcher about Grid & size, over the same
 * `grid_control` provider as [IconsBridge].
 *
 * The custom grid is one more grid option, [CUSTOM], beside Google's own: the
 * launcher lists it in `list_options` after them, Wallpaper & style draws its
 * tile, previews it and applies it the way it does any grid (`default_grid`),
 * and the launcher keeps it as its grid name (`idp_grid_name`). Its columns and
 * rows, the spacing and the icon size are this module's settings, read and written here.
 */
object GridBridge {

    /** The custom grid's name; Google's grid names never start with `ple_`. */
    const val CUSTOM: String = "ple_custom"

    /** Every method this module adds for Grid & size starts with this. */
    const val PREFIX: String = "ple_grid_"

    /** What can be chosen here and what is chosen: [COLUMNS], [ROWS], their ranges, [ICON_SIZE], [SPACING_X], [SPACING_Y], [CURRENT]. */
    const val STATE: String = "ple_grid_state"

    /** Sets [KEY] (a setting key) to [VALUE]; answers [OK] and, when refused, [MESSAGE]. */
    const val SET: String = "ple_grid_set"

    const val AVAILABLE = "available"
    const val COLUMNS = "columns"
    const val ROWS = "rows"
    const val COLUMNS_FROM = "columns_from"
    const val COLUMNS_TO = "columns_to"
    const val ROWS_FROM = "rows_from"
    const val ROWS_TO = "rows_to"
    const val ICON_SIZE = "icon_size"
    const val SPACING_X = "spacing_x"
    const val SPACING_Y = "spacing_y"
    const val CURRENT = "current"
    const val KEY = "key"
    const val VALUE = "value"
    const val OK = "ok"
    const val MESSAGE = "message"
}
