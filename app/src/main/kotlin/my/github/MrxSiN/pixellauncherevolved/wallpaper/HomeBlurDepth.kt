package my.github.MrxSiN.pixellauncherevolved.wallpaper

import java.util.WeakHashMap

/**
 * How deep the wallpaper sits while the launcher is on its home state.
 *
 * The launcher already blurs the wallpaper behind the app drawer and Recents,
 * through one number each state reports: zero for home, higher as a state takes
 * over the screen. That number drives both the background blur put on the
 * wallpaper surface and the zoom out that goes with it, which together are the
 * effect this tweak is asked for. So this changes what the home state reports
 * and lets the launcher draw the result.
 *
 * Changing the state's own answer, rather than the depth the launcher ends up
 * applying, is the whole design. The launcher animates a state change from the
 * depth it believes it is at to the depth it is going to, so a floor held under
 * the applied depth alone leaves the launcher animating away from zero — the
 * blur drops out at the start of every transition, and comes back only once the
 * next state has settled. Told what home is, the launcher animates from there
 * and nothing drops out.
 *
 * There is no Android in here, so what it decides can be reasoned about on its
 * own.
 */
class HomeBlurDepth {

    /**
     * The controllers that have applied a state, and the state each applied.
     *
     * Kept so that a change in Home settings reaches the home screen at once,
     * rather than on the next state change. The controllers are held weakly:
     * this outlives none of them.
     */
    private val applied = WeakHashMap<Any, Any>()

    /** Whether the tweak is switched on. */
    @Volatile
    var isEnabled: Boolean = false

    /** How strong the blur is, as the stored percentage. */
    @Volatile
    var strength: Int = DEFAULT_STRENGTH

    /** The blur radius home rests at, for a launcher whose deepest is [maxRadius]. */
    fun radiusForHome(maxRadius: Int): Float = radiusFor(strength, maxRadius)

    /**
     * The depth the home state reports, for the [asked] one it reports itself.
     *
     * Never below what the launcher asked for: a launcher that one day rests
     * home deeper than this keeps its own answer.
     */
    fun depthForHome(asked: Float): Float =
        if (isEnabled) maxOf(asked, depthFor(strength)) else asked

    fun remember(controller: Any, state: Any) {
        applied[controller] = state
    }

    /** Every controller and the state to apply again, to reach the screen now. */
    fun remembered(): List<Pair<Any, Any>> = applied.entries.map { it.key to it.value }

    companion object {

        /**
         * The depth at which the launcher's blur reaches its full radius.
         *
         * `BaseDepthControllerImpl.mapDepthToBlur` is
         * `clampToProgress(LINEAR, depth, 0f, 0.3f)`, so the blur grows
         * linearly up to this and no further; past it only the wallpaper's zoom
         * out keeps going. It is therefore the whole useful range, and the
         * strongest end of the slider.
         */
        const val FULL_DEPTH: Float = 0.3f

        /**
         * The middle, which is what this tweak did before it could be changed.
         *
         * Half of [FULL_DEPTH] reads as a wallpaper pushed back behind the
         * icons rather than as the app drawer's frost.
         */
        const val DEFAULT_STRENGTH: Int = 50

        /** [strength] as a share of [FULL_DEPTH]. */
        fun depthFor(strength: Int): Float =
            FULL_DEPTH * strength.coerceIn(0, 100) / 100f

        /**
         * The same depth as a blur radius, for a launcher whose deepest blur is
         * [maxRadius] pixels.
         *
         * `mapDepthToBlur` scales linearly to the full radius at [FULL_DEPTH],
         * and a strength is a share of that depth, so a strength is the same
         * share of the radius.
         */
        fun radiusFor(strength: Int, maxRadius: Int): Float =
            maxRadius * strength.coerceIn(0, 100) / 100f
    }
}
