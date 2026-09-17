package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.content.res.Configuration

/**
 * The Material 3 colour roles this module draws its own surfaces with.
 *
 * Read from the platform's own palette — the colours the settings app is drawn
 * from — rather than from a copy kept here, so everything follows the wallpaper
 * and the dark theme without knowing what either of them currently is.
 *
 * The palette is published as two sets of resources rather than as one that
 * follows the night setting, so the set is chosen here. These are framework
 * resources, so they read the same in the launcher's process as anywhere else.
 */
enum class ExpressiveRole(private val light: Int, private val dark: Int) {
    PRIMARY(android.R.color.system_primary_light, android.R.color.system_primary_dark),
    ON_PRIMARY(android.R.color.system_on_primary_light, android.R.color.system_on_primary_dark),
    PRIMARY_CONTAINER(
        android.R.color.system_primary_container_light,
        android.R.color.system_primary_container_dark,
    ),
    ON_PRIMARY_CONTAINER(
        android.R.color.system_on_primary_container_light,
        android.R.color.system_on_primary_container_dark,
    ),
    SECONDARY(android.R.color.system_secondary_light, android.R.color.system_secondary_dark),
    TERTIARY(android.R.color.system_tertiary_light, android.R.color.system_tertiary_dark),
    ERROR(android.R.color.system_error_light, android.R.color.system_error_dark),
    ON_SURFACE(android.R.color.system_on_surface_light, android.R.color.system_on_surface_dark),
    ON_SURFACE_VARIANT(
        android.R.color.system_on_surface_variant_light,
        android.R.color.system_on_surface_variant_dark,
    ),
    OUTLINE_VARIANT(android.R.color.system_outline_variant_light, android.R.color.system_outline_variant_dark),

    /**
     * What a settings card is filled with.
     *
     * Read off a card in Display & touch on a Pixel running Android 17, it is
     * `#2F2B27`, and so is this role in that device's palette. The container
     * roles are all darker, and leave a card barely distinguishable from the
     * screen behind it.
     */
    SURFACE_BRIGHT(android.R.color.system_surface_bright_light, android.R.color.system_surface_bright_dark),

    /** What a settings screen is drawn on, behind its cards. */
    SURFACE_CONTAINER(
        android.R.color.system_surface_container_light,
        android.R.color.system_surface_container_dark,
    ),

    /** A dialog's own surface, and a settings screen's back button. */
    SURFACE_CONTAINER_HIGH(
        android.R.color.system_surface_container_high_light,
        android.R.color.system_surface_container_high_dark,
    );

    fun of(context: Context): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return context.resources.getColor(if (night) dark else light, context.theme)
    }
}

fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)
