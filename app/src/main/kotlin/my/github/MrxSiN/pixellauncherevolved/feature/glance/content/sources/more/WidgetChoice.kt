package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

/** The pure rules behind the Widget card's stored choice; tested without a launcher. */
internal object WidgetChoice {

    const val OPTION = "widget"

    /** Left of the card's edge on each side, in dp: At a Glance's horizontal padding. */
    private const val MARGIN_DP = 16

    /** A home grid row is about one column wide, and the Pixel home grid has this many columns. */
    private const val COLUMNS = 4

    /** "component|id": the provider's flattened component and the widget id allocated for it. */
    fun encode(component: String, id: Int): String = "$component|$id"

    fun decode(value: String?): Pair<String, Int>? {
        val at = value?.lastIndexOf('|') ?: return null
        if (at <= 0) return null
        val id = value.substring(at + 1).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return value.substring(0, at) to id
    }

    /**
     * This module's own AppWidgetHost id. Bit 30 is set so it can never equal Launcher3's
     * (`LauncherWidgetHolder.APPWIDGET_HOST_ID`, 1024) or any small id, and the low 24 bits come
     * from the module package so another app's host does not share it either.
     */
    fun hostId(modulePackage: String): Int = 0x4000_0000 or (modulePackage.hashCode() and 0x00FF_FFFF)

    /** The size asked of the provider, in dp: the screen's width less the margins, one home grid row tall. */
    fun cardSize(widthPx: Int, density: Float): Pair<Int, Int> {
        val width = (widthPx / density).toInt() - 2 * MARGIN_DP
        return width to width / COLUMNS
    }
}
