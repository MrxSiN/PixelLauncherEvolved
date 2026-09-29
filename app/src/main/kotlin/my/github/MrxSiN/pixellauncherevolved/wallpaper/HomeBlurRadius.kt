package my.github.MrxSiN.pixellauncherevolved.wallpaper

import android.content.Context
import android.view.WindowManager

/**
 * The radius home's wallpaper rests blurred at, in full-screen pixels, for
 * pictures of the home screen drawn outside it: Wallpaper & style's previews.
 *
 * The launcher blurs up to `max_depth_blur_radius_enhanced` as a state's depth
 * reaches [HomeBlurDepth.FULL_DEPTH], linearly, so home resting at a strength's
 * share of that depth rests at the same share of that radius. None where the
 * tweak is off or the platform has cross-window blurs switched off, as on the
 * home screen itself.
 */
object HomeBlurRadius {

    fun of(context: Context, enabled: Boolean, strength: Int): Int {
        if (!enabled) return 0
        if (context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled != true) return 0
        val id = context.resources.getIdentifier(MAX_RADIUS, "dimen", context.packageName)
        if (id == 0) return 0
        return HomeBlurDepth.restingRadius(context.resources.getDimensionPixelSize(id), strength).toInt()
    }

    private const val MAX_RADIUS = "max_depth_blur_radius_enhanced"
}
