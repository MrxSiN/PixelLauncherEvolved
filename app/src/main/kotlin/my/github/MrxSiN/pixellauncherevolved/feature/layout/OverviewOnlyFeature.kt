package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.view.View

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

import kotlin.math.roundToInt

/**
 * Gives the launcher the tablet Recents grid without the tablet grid or a taskbar.
 *
 * Recents is laid out as a grid on a large screen and as a single row of cards
 * on a phone, and the launcher decides which from one boolean,
 * `DeviceProperties.isLargeScreen`. That boolean is also what the workspace,
 * the app drawer and the taskbar are measured from — but they are measured once,
 * while `DeviceProfile` is being built, and Recents reads it again on every
 * layout. Flipping it after the profile is finished therefore reaches Recents
 * and nothing that was already sized.
 *
 * Four things have to hold, and each is one class below: the launcher has to
 * answer large screen once the profile is built, the Overview dimensions have to
 * be the ones a large screen resolves rather than the phone's, Recents has to
 * agree that it is a grid at rest as well as mid-gesture, and the handful of
 * home-screen surfaces that read the same boolean at runtime have to keep
 * reading the phone's answer.
 */
class OverviewOnlyFeature : ToggleFeature(Settings.OVERVIEW_ONLY) {

    override val compatibility = CompatibilityFeature.OVERVIEW_ONLY
    // Device profiles are built once at startup. Both transitions need a restart.
    override val isLive: Boolean = false

    override fun install(context: FeatureContext) {
        val classification = LargeScreenClassification(context)

        GridOverviewProfiles(context, classification, OverviewGridMetrics(context)).install()
        PhoneSurfaces(context, classification).install()
        PhoneActionRow(context).install()
        GridCardAppChip(context).install()
        GridAtRest(context).install()
    }
}

/**
 * The one boolean the whole tablet layout is decided from, and who is reading it.
 *
 * ```
 * com.android.launcher3.deviceprofile.DeviceProperties
 *   public final boolean isLargeScreen
 * ```
 *
 * The launcher reaches it by direct field access from everywhere, so it cannot
 * be answered per caller the way a getter could. What can be done is to hold it
 * true and put it back for the length of a call that should not see it, which
 * is what [asPhone] is for.
 *
 * Every profile the process builds is remembered, because the launcher builds
 * one for its own window and another for the taskbar's, and a call that steps
 * back to the phone answer has to step both back or the two disagree.
 */
private class LargeScreenClassification(private val context: FeatureContext) {

    private val isLargeScreen = requireNotNull(
        Reflect.field(
            requireNotNull(context.findClass(DEVICE_PROPERTIES)),
            "isLargeScreen",
        ),
    )

    /**
     * Held weakly: a configuration change replaces a profile, and nothing here
     * should be the reason the old one stays alive.
     */
    private val enabled = java.util.WeakHashMap<Any, Unit>()

    /** How many [asPhone] calls are in progress, so nesting restores once. */
    private var phoneDepth = 0

    fun enableOn(properties: Any) {
        synchronized(this) {
            enabled[properties] = Unit
            // A profile built while a home surface is mid-call joins it there.
            isLargeScreen.setBoolean(properties, phoneDepth == 0)
        }
    }

    /**
     * Runs [body] with every known profile answering phone again.
     *
     * Only the launcher's own home surfaces are wrapped, and those all run on
     * the UI thread, so the window in which the answer is the phone's is a
     * single call on a single thread. Recents reads the boolean from the UI
     * thread too while it is laid out, and from the gesture thread while a
     * swipe is in flight — neither overlaps a home surface measuring itself.
     */
    fun asPhone(body: () -> Unit) {
        synchronized(this) {
            if (phoneDepth++ == 0) setAll(false)
        }

        try {
            body()
        } finally {
            synchronized(this) {
                if (--phoneDepth == 0) setAll(true)
            }
        }
    }

    private fun setAll(value: Boolean) {
        for (properties in enabled.keys) {
            runCatching { isLargeScreen.setBoolean(properties, value) }
                .onFailure { context.logger.warn("Unable to set the launcher's screen classification", it) }
        }
    }

    private companion object {
        const val DEVICE_PROPERTIES = "com.android.launcher3.deviceprofile.DeviceProperties"
    }
}

/**
 * Answers large screen on every profile, once that profile is finished.
 *
 * ```
 * com.android.launcher3.DeviceProfile$Builder
 *   public DeviceProfile build()
 * com.android.launcher3.DeviceProfile
 *   public DeviceProperties deviceProperties
 *   public OverviewProfile overviewProfile
 * ```
 *
 * `build()` reads `LauncherDisplayInfo.isLargeScreen(WindowBounds)` itself and
 * derives the workspace, app drawer, hotseat and taskbar dimensions from the
 * answer before it returns. Leaving that call alone — which is what separates
 * this from tablet mode — and rewriting the stored boolean afterwards keeps
 * every measured dimension on the phone's numbers while everything read later
 * sees the large screen Recents is laid out for.
 */
private class GridOverviewProfiles(
    private val context: FeatureContext,
    private val classification: LargeScreenClassification,
    private val metrics: OverviewGridMetrics,
) {

    fun install() {
        val profile = requireNotNull(DeviceProfiles.profile(context))
        val builder = requireNotNull(DeviceProfiles.builder(context))
        val propertiesOf = requireNotNull(Reflect.field(profile, "deviceProperties"))
        val overviewOf = requireNotNull(Reflect.field(profile, "overviewProfile"))

        context.xposed.hook(builder.getDeclaredMethod(DeviceProfiles.BUILD)).intercept { chain ->
            chain.proceed()?.also { built ->
                runCatching {
                    metrics.applyTo(overviewOf.get(built))
                    classification.enableOn(propertiesOf.get(built))
                }.onFailure { context.logger.warn("Unable to lay Recents out as a grid", it) }
            }
        }

        context.logger.info("Overview only: large-screen Recents reported on every device profile")
    }
}

/**
 * Makes Recents agree that it is a grid when no gesture is in flight.
 *
 * ```java
 * // com.android.quickstep.views.RecentsView
 * public boolean showAsGrid() {
 *     return mOverviewGridEnabled
 *         || (mCurrentGestureEndTarget != null
 *             && stateFromGestureEndTarget(mCurrentGestureEndTarget)
 *                    .displayOverviewTasksAsGrid(getDeviceProfile()));
 * }
 * ```
 *
 * Two answers, and only the second one is ours. `mOverviewGridEnabled` is a flag
 * the launcher turns **off** — `LauncherRecentsView.onStateTransitionComplete`
 * clears it when the state is not a grid, and nothing outside the tablet path
 * ever sets it — so with Overview only the answer depends on whether a gesture
 * is in flight.
 *
 * That splits the page scrolls in two. `PagedView.getPageScrolls` consults
 * `showAsGrid()`, so a layout during the swipe computes the phone's scrolls and
 * `RecentsView.onGestureAnimationEnd` — through `updateOrientationHandler` and
 * `setCurrentPage` — computes the grid's, and the pager is jumped from one to
 * the other on the frame the gesture finishes. Caught in the act:
 *
 * ```
 * SCROLLPROBE d=84 from=5472 to=5556 ::
 *   PagedView.updateCurrentPageScroll | PagedView.setCurrentPage
 *   | RecentsView.updateOrientationHandler | RecentsView.onGestureAnimationEnd
 *   | AbsSwipeUpHandler.setupLauncherUiAfterSwipeUpToRecentsAnimation
 * ```
 *
 * Answering the question once removes the disagreement. Recents is a grid for
 * as long as this tweak is on, which is the whole of what the tweak says, so the
 * answer is the same whoever is asking and whenever.
 */
private class GridAtRest(private val context: FeatureContext) {

    fun install() {
        val recents = requireNotNull(context.findClass("com.android.quickstep.views.RecentsView"))

        context.xposed.hook(requireNotNull(Reflect.method(recents, "showAsGrid"))).intercept { true }

        context.logger.info("Overview only: Recents laid out as a grid whether or not a gesture is running")
    }
}

/**
 * Gives Overview the dimensions a large screen resolves.
 *
 * ```
 * com.android.launcher3.deviceprofile.OverviewProfile
 *   public OverviewProfile(int taskMarginPx, int taskIconSizePx,
 *       int taskIconDrawableSizePx, int taskIconDrawableSizeGridPx,
 *       int actionsHeight, int actionsTopMarginPx, int pageSpacing,
 *       int rowSpacing, int gridSideMargin)
 * ```
 *
 * `DeviceProfile$Builder.build()` fills it from nine dimension resources, read
 * through a configuration context it builds for the window being measured. Five
 * of those resources are qualified, and on a phone the grid ones resolve to
 * nothing at all:
 *
 * | Dimension | phone | `sw600dp` | `sw720dp` |
 * |---|---|---|---|
 * | `overview_grid_side_margin` | **0dp** | 64dp | — |
 * | `overview_grid_row_spacing` | **0dp** | 28dp | 36dp |
 * | `task_thumbnail_icon_drawable_size_grid` | **0dp** | 44dp | — |
 * | `overview_page_spacing` | 16dp | 36dp | 44dp |
 * | `overview_task_margin` | 16dp | 12dp | 16dp |
 *
 * So a grid laid out on the phone's numbers has no space between its rows, no
 * margin to sit inside, and no icon on a card — the cards are drawn at the size
 * a single row of full-height cards is measured for, stacked with nothing
 * between them. Resolving the same nine against a `smallestScreenWidthDp` of
 * 600 — the width the launcher's own `LauncherDisplayInfo.isLargeScreen` calls
 * large — is what makes the card previews the size the grid expects.
 *
 * Only that one value is overridden. Density and orientation come from the
 * configuration the launcher is actually running in, so a rotation still picks
 * the landscape answer where a dimension has one.
 */
private class OverviewGridMetrics(private val context: FeatureContext) {

    fun applyTo(overview: Any) {
        val large = largeScreenResources()

        for ((field, dimension) in DIMENSIONS) {
            runCatching {
                val id = large.getIdentifier(dimension, "dimen", context.appContext.packageName)
                check(id != 0) { "The launcher has no dimen/$dimension" }

                requireNotNull(Reflect.field(overview.javaClass, field))
                    .setInt(overview, large.getDimensionPixelSize(id))
            }.onFailure {
                context.logger.warn("Unable to size Overview's $field for a large screen", it)
            }
        }

        context.logger.info("Overview only: Overview measured with the large-screen dimensions")
    }

    private fun largeScreenResources(): Resources {
        val configuration = Configuration(context.appContext.resources.configuration).apply {
            smallestScreenWidthDp = LARGE_SCREEN_WIDTH_DP
        }

        return context.appContext.createConfigurationContext(configuration).resources
    }

    private companion object {
        /** What `LauncherDisplayInfo.isLargeScreen` calls large, in dp. */
        const val LARGE_SCREEN_WIDTH_DP = 600

        /** Each field of `OverviewProfile`, and the dimension it is read from. */
        val DIMENSIONS = listOf(
            "taskMarginPx" to "overview_task_margin",
            "taskIconSizePx" to "task_thumbnail_icon_menu_drawable_touch_size",
            "taskIconDrawableSizePx" to "task_thumbnail_icon_drawable_size",
            "taskIconDrawableSizeGridPx" to "task_thumbnail_icon_drawable_size_grid",
            "actionsHeight" to "overview_actions_height",
            "actionsTopMarginPx" to "overview_actions_top_margin",
            "pageSpacing" to "overview_page_spacing",
            "rowSpacing" to "overview_grid_row_spacing",
            "gridSideMargin" to "overview_grid_side_margin",
        )
    }
}

/**
 * Keeps the launcher's own surfaces on the phone's answer.
 *
 * Recents is not the only thing that reads `isLargeScreen` while the launcher
 * runs. The rest of what does is this list, and none of it is Overview: it is
 * the home screen, the app drawer, the widget picker and the drag surfaces,
 * which this tweak is not about. Each is put back on the phone's answer for the
 * length of its own call, so what they measure is what they measured before.
 *
 * Read off the launcher's own dex, as every reader of that field outside
 * `com.android.quickstep` and Overview's own states. The setup wizard,
 * the gesture tutorial and the test handler read it too and are left alone:
 * none of them is reached from a launcher this module is running inside.
 *
 * A signature that has moved is reported and skipped rather than throwing. The
 * tweak is still worth having with one home surface reading large screen; it is
 * not worth taking Recents down for.
 */
private class PhoneSurfaces(
    private val context: FeatureContext,
    private val classification: LargeScreenClassification,
) {

    fun install() {
        for (surface in SURFACES) {
            val owner = context.findClass(surface.owner)
            if (owner == null) {
                context.logger.warn("Overview only: ${surface.owner} is not in this launcher")
                continue
            }

            val method = Reflect.method(
                owner,
                surface.method,
                *surface.parameterTypes.map { parameterType(it) }.toTypedArray(),
            )
            if (method == null) {
                context.logger.warn("Overview only: ${surface.owner}.${surface.method} has moved")
                continue
            }

            context.xposed.hook(method).intercept { chain ->
                var result: Any? = null
                classification.asPhone { result = chain.proceed() }
                result
            }
        }

        context.logger.info("Overview only: the launcher's own surfaces kept on phone measurements")
    }

    /** A parameter named as the launcher declares it, primitives included. */
    private fun parameterType(name: String): Class<*> = when (name) {
        "boolean" -> Boolean::class.javaPrimitiveType!!
        "int" -> Int::class.javaPrimitiveType!!
        "float" -> Float::class.javaPrimitiveType!!
        else -> requireNotNull(context.findClass(name)) { "No class $name" }
    }

    private data class Surface(
        val owner: String,
        val method: String,
        val parameterTypes: List<String> = emptyList(),
    )

    private companion object {
        val SURFACES = listOf(
            Surface(
                "com.android.launcher3.Launcher",
                "initDeviceProfile",
                listOf("com.android.launcher3.InvariantDeviceProfile"),
            ),
            Surface("com.android.launcher3.DropTargetBar", "setInsets", listOf("android.graphics.Rect")),
            Surface("com.android.launcher3.Workspace", "isSignificantMove", listOf("float", "int")),
            Surface(
                "com.android.launcher3.QuickstepTransitionManager",
                "getLauncherContentAnimator",
                listOf("int", "boolean", "boolean"),
            ),
            Surface(
                "com.android.launcher3.allapps.ActivityAllAppsContainerView",
                "setInsets",
                listOf("android.graphics.Rect"),
            ),
            Surface(
                "com.android.launcher3.model.data.AppPairInfo",
                "isLaunchable",
                listOf("android.content.Context"),
            ),
            Surface(
                "com.android.launcher3.states.RotationHelper",
                "onDeviceProfileChanged",
                listOf("com.android.launcher3.DeviceProfile"),
            ),
            Surface(
                "com.android.launcher3.touch.AbstractStateChangeTouchController",
                "onDragEnd",
                listOf("float"),
            ),
            Surface(
                "com.android.launcher3.uioverrides.QuickstepLauncher",
                "getSupportedShortcuts",
                listOf("com.android.launcher3.model.data.ItemInfo"),
            ),
            Surface("com.android.launcher3.views.AbstractSlideInView", "onDragEnd", listOf("float")),
            // CP3A inlined getDismissTimeout(ActivityContext) into show, which
            // now reads the field itself to pick how long the snackbar stays.
            Surface(
                "com.android.launcher3.views.Snackbar",
                "show",
                listOf(
                    "com.android.launcher3.views.ActivityContext",
                    "java.lang.CharSequence",
                    "int",
                    "java.lang.Runnable",
                    "java.lang.Runnable",
                ),
            ),
            Surface(
                "com.android.launcher3.widget.LauncherAppWidgetProviderInfo",
                "initSpans",
                listOf("android.content.Context", "com.android.launcher3.InvariantDeviceProfile"),
            ),
        )
    }
}

/**
 * Fits the app chip to the card it is drawn on, at the moment it is inflated.
 *
 * ```
 * com.android.quickstep.views.IconAppChipView   // R.id.icon on a task card
 *   protected void onFinishInflate()
 *   public void setMaxWidth(int)
 *   private void updateChipSize()
 *   private int maxWidth                 // starts at Integer.MAX_VALUE
 *   private int collapsedMenuDefaultWidth
 *   private int minWidthAllowed
 *   private int appIconSize
 * com.android.quickstep.views.RecentsViewContainer
 *   public static Context containerFromContext(Context)
 * com.android.quickstep.LauncherActivityInterface
 *   public static final DaggerSingletonObject INSTANCE
 * com.android.quickstep.BaseContainerInterface
 *   private void calculateLargeTileSize(Context, DeviceProfile, Rect)
 * ```
 *
 * The chip — the app's icon, its name and the chevron that opens the task menu
 * — is laid out from unqualified dimensions, so it is the same size on a grid
 * card as on a full-height one. A tablet gets away with that because its grid
 * cards are wide. Measured here, a grid card is **451x1005** and the chip
 * **439x156**: 97% of the card's width, against 45% of a full-height card's.
 *
 * The collapsed width is
 *
 * ```java
 * getCollapsedBackgroundWidth()
 *     = min(maxWidth, collapsedMenuDefaultWidth) + backgroundMarginTopStart
 * ```
 *
 * so `maxWidth` decides nothing until it is lowered below the default, which is
 * what `setMaxWidth` is for and what the launcher itself calls for the two
 * halves of a split card. Dividing `collapsedMenuDefaultWidth` by the ratio a
 * grid card stands to a full one brings the chip to **210x156**, 47% of the
 * card.
 *
 * ### Why none of this happens while a card is on screen
 *
 * Earlier versions did it from `TaskView.updateTaskSize`, which is where the
 * launcher works that ratio out — and that is inside the pager's own layout.
 * `updateChipSize` ends in `setLayoutParams`, and a `requestLayout` inside a
 * `PagedView` sends it back through its page scrolls, so the task cards snapped
 * sideways as the app-to-Overview gesture finished. Capping fewer times, and
 * then writing the widths in place without `setLayoutParams`, each made it
 * smaller without making it go away: any change to a chip's measurement while
 * its card is being laid out is a change the pager answers.
 *
 * So the chip is capped once, in `onFinishInflate` — and without asking for a
 * layout even there. `onFinishInflate` is **not** outside the transition:
 * `RecentsView` inflates a task card when it binds a task, which is part of
 * Overview opening, so an `updateChipSize` there is a `requestLayout` in the
 * middle of the gesture like any other. Measured off a recording of the real
 * fling, both card rows still moved **+84px in the frame after the animation
 * came to rest**, while the action row and the status bar did not move at all
 * and nothing moved vertically — a pager scroll correction, and nothing else.
 *
 * A view that has not been laid out yet has no layout to invalidate, though, so
 * the two widths `updateChipSize` would set are written straight onto the layout
 * parameters the chip and its title already carry, and neither `setLayoutParams`
 * nor `updateChipSize` is called:
 *
 * ```java
 * // IconAppChipView.updateChipSize(), collapsed
 * appTitle.getLayoutParams().width =
 *     calculateCollapsedTextWidth(getCollapsedBackgroundLtrBounds().width());
 * appTitle.setLayoutParams(lp);        // <- not called
 * getLayoutParams().width = getChipWidth();
 * setLayoutParams(lp);                 // <- not called
 * ```
 *
 * The chip's own first measure reads them, so it is the right size from the
 * first frame it is drawn and nothing is ever laid out twice. Both widths are
 * still the launcher's own, asked of `getChipWidth`,
 * `getCollapsedBackgroundLtrBounds` and `calculateCollapsedTextWidth`. Only the
 * collapsed widths are written: a chip is inflated collapsed, and the launcher
 * runs its own `updateChipSize` when the task menu opens.
 *
 * ### Where the ratio comes from without a card to measure
 *
 * `getNonGridScale()` is not available at inflation — there is no card size yet
 * — so the ratio is derived from the device profile instead. A grid card is half
 * of the large tile, less the row spacing, and both keep the screen's aspect:
 *
 * ```
 * gridHeight = (largeTileHeight - rowSpacing) / 2
 * ratio      = largeTileWidth / gridWidth = 2 * largeTileHeight
 *                                           / (largeTileHeight - rowSpacing)
 * ```
 *
 * which needs one number the launcher has to work out — the large tile — and
 * `BaseContainerInterface.calculateLargeTileSize` is asked for it directly,
 * through the size strategy `LauncherActivityInterface.INSTANCE` hands out. It
 * is asked once and the answer kept, because it is a property of the profile
 * rather than of any card.
 *
 * A launcher that no longer offers any of that leaves the chip the size it was:
 * a wide chip is worth less than a snapping transition.
 */
private class GridCardAppChip(private val context: FeatureContext) {

    /** The ratio a full-height card stands to a grid one, once worked out. */
    @Volatile
    private var ratio: Float = 0f

    fun install() {
        val chipView = requireNotNull(context.findClass("com.android.quickstep.views.IconAppChipView"))

        val onFinishInflate = requireNotNull(Reflect.method(chipView, "onFinishInflate"))
        val setMaxWidth = requireNotNull(
            Reflect.method(chipView, "setMaxWidth", Int::class.javaPrimitiveType!!),
        )
        val widths = ChipWidths(chipView)
        val layout = ChipLayout(chipView)
        val profiles = LargeTile(context)

        context.xposed.hook(onFinishInflate).intercept { chain ->
            chain.proceed().also {
                runCatching {
                    val chip = chain.thisObject as View
                    val known = ratio.takeIf { it > 1f } ?: profiles.ratioFor(chip).also { ratio = it }
                    val wanted = widths.wantedOn(chip, known)

                    if (wanted != null && wanted < widths.current(chip)) {
                        setMaxWidth.invoke(chip, wanted)
                        layout.applyTo(chip)
                    }
                }.onFailure { context.logger.warn("Unable to fit the app chip to its card", it) }
            }
        }

        context.logger.info("Overview only: the app chip fitted to the card it sits on")
    }

    /** Asks the launcher how big a full-height card is, and what a grid one is of it. */
    private class LargeTile(private val context: FeatureContext) {

        private val profile = requireNotNull(DeviceProfiles.profile(context))
        private val overviewOf = requireNotNull(Reflect.field(profile, "overviewProfile"))
        private val rowSpacing = requireNotNull(
            Reflect.field(
                requireNotNull(context.findClass("com.android.launcher3.deviceprofile.OverviewProfile")),
                "rowSpacing",
            ),
        )

        private val containerFromContext = requireNotNull(
            Reflect.method(
                requireNotNull(context.findClass("com.android.quickstep.views.RecentsViewContainer")),
                "containerFromContext",
                Context::class.java,
            ),
        )
        private val deviceProfileOf = requireNotNull(
            Reflect.method(
                requireNotNull(context.findClass("com.android.launcher3.views.ActivityContext")),
                "getDeviceProfile",
            ),
        )

        private val strategyHolder = requireNotNull(
            Reflect.field(
                requireNotNull(context.findClass("com.android.quickstep.LauncherActivityInterface")),
                "INSTANCE",
            ),
        )
        private val singletonGet = requireNotNull(
            Reflect.method(
                requireNotNull(context.findClass("com.android.launcher3.util.DaggerSingletonObject")),
                "get",
                Context::class.java,
            ),
        )
        private val largeTileSize = requireNotNull(
            Reflect.method(
                requireNotNull(context.findClass("com.android.quickstep.BaseContainerInterface")),
                "calculateLargeTileSize",
                Context::class.java,
                profile,
                Rect::class.java,
            ),
        )

        fun ratioFor(chip: View): Float {
            val container = requireNotNull(containerFromContext.invoke(null, chip.context))
            val deviceProfile = requireNotNull(deviceProfileOf.invoke(container))
            val strategy = requireNotNull(singletonGet.invoke(strategyHolder.get(null), chip.context))

            val tile = Rect()
            largeTileSize.invoke(strategy, chip.context, deviceProfile, tile)

            val spacing = rowSpacing.getInt(overviewOf.get(deviceProfile))
            val rows = tile.height() - spacing
            check(rows > 0) { "A large tile of ${tile.height()} leaves no room for two rows" }

            return 2f * tile.height() / rows
        }
    }

    /**
     * Writes the widths `updateChipSize` would write, without the layout it
     * asks for.
     *
     * Safe only because the chip has just been inflated and has never been
     * measured: there is no layout to invalidate, and its first measure reads
     * these. Doing the same to a chip already on screen would not be — that is
     * what the pager answers by re-running its page scrolls.
     */
    private class ChipLayout(chipView: Class<*>) {

        private val appTitleOf = requireNotNull(Reflect.field(chipView, "appTitle"))
        private val chipWidth = requireNotNull(Reflect.method(chipView, "getChipWidth"))
        private val collapsedBounds = requireNotNull(
            Reflect.method(chipView, "getCollapsedBackgroundLtrBounds"),
        )
        private val collapsedTextWidth = requireNotNull(
            Reflect.method(chipView, "calculateCollapsedTextWidth", Int::class.javaPrimitiveType!!),
        )

        fun applyTo(chip: View) {
            val title = appTitleOf.get(chip) as View
            val bounds = collapsedBounds.invoke(chip) as Rect

            title.layoutParams?.width = collapsedTextWidth.invoke(chip, bounds.width()) as Int
            chip.layoutParams?.width = chipWidth.invoke(chip) as Int
        }
    }

    /** The chip's own widths, and the cap they add up to at one ratio. */
    private class ChipWidths(chipView: Class<*>) {

        private val defaultWidth = requireNotNull(Reflect.field(chipView, "collapsedMenuDefaultWidth"))
        private val minimum = requireNotNull(Reflect.field(chipView, "minWidthAllowed"))
        private val appIconSize = requireNotNull(Reflect.field(chipView, "appIconSize"))
        private val cap = requireNotNull(Reflect.field(chipView, "maxWidth"))

        fun current(chip: View): Int = cap.getInt(chip)

        /**
         * The cap for a card at this ratio, or none when there is no ratio to
         * go on — a chip left alone is a chip the size the launcher drew it.
         *
         * What the cap takes from is the app's name —
         * `calculateCollapsedTextWidth` gives the title whatever is left above
         * `minWidthAllowed` — and on a card this narrow there is barely any
         * left: measured here, 14px, which draws as one clipped character. A
         * name too short to read is worse than no name, so a title that would
         * come out narrower than the app icon beside it is dropped and the chip
         * is given the launcher's own minimum instead. A wider grid card — a
         * landscape one, or a foldable's inner display — still keeps its name.
         */
        fun wantedOn(chip: View, ratio: Float): Int? {
            if (ratio <= 1f) return null

            val minimum = minimum.getInt(chip)
            val scaled = (defaultWidth.getInt(chip) / ratio).roundToInt()

            return if (scaled - minimum < appIconSize.getInt(chip)) minimum else scaled
        }
    }
}

/**
 * Keeps the Overview action row, where a phone keeps it.
 *
 * ```
 * com.android.quickstep.views.RecentsView.setInsets(Rect)
 *   mActionsView.updateHiddenFlags(HIDDEN_LARGE_SCREEN, isLargeScreen);
 * com.android.quickstep.views.OverviewActionsView
 *   public void updateHiddenFlags(int flags, boolean hidden)
 *   public int getBottomMargin()
 *   private DeviceProfile mDp
 *   private Rect mTaskSize
 * com.android.quickstep.BaseContainerInterface
 *   public void calculateGridSize(DeviceProfile, Rect)
 * ```
 *
 * A tablet has no Screenshot, Select or Clear all under Overview — it offers
 * those from the task menu — so the launcher hides the row, places what would
 * have been there against the taskbar, and lets the cards have the space. This
 * module's Overview page offers all three, so three answers have to change: the
 * row is shown, it is placed where a phone places it, and the cards give it its
 * room back.
 *
 * A fourth answer used to be needed, because a tablet put a Split button in the
 * row that a phone's width has no space for. Android 17 `CP3A.260905.009` took
 * that button out of the row on every device — there is no `action_split` left
 * in `overview_actions_container`, and no `updateSplitButtonHiddenFlags` to
 * tell it apart — so there is nothing left to keep out.
 *
 * Each is answered directly rather than by stepping the screen classification
 * back for the length of the call, the way [PhoneSurfaces] does. Those are home
 * surfaces, which are never measuring while Overview is. These three are
 * Overview itself: `setInsets` is called as the app window animates, and the
 * cards are laid out from the same field on the gesture thread, so a window in
 * which that field reads phone is a frame of the app-to-Overview transition
 * measured as one — which is what it looked like.
 */
private class PhoneActionRow(private val context: FeatureContext) {

    fun install() {
        val actionsView = requireNotNull(
            context.findClass("com.android.quickstep.views.OverviewActionsView"),
        )
        val profile = requireNotNull(DeviceProfiles.profile(context))

        keepShown(actionsView)
        keepPlaced(actionsView, profile)
        keepRoom(profile)

        context.logger.info("Overview only: the Overview action row kept where a phone keeps it")
    }

    /**
     * Clears the one flag that takes the row away on a large screen.
     *
     * The launcher sets and clears half a dozen flags through this method for
     * reasons of its own — a task gone modal, a split selection in progress —
     * and every one of those still applies. Only [HIDDEN_LARGE_SCREEN] is
     * answered, and only ever by clearing it.
     */
    private fun keepShown(actionsView: Class<*>) {
        val updateHiddenFlags = requireNotNull(
            Reflect.method(
                actionsView,
                "updateHiddenFlags",
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            ),
        )

        context.xposed.hook(updateHiddenFlags).intercept { chain ->
            if (chain.args.getOrNull(0) == HIDDEN_LARGE_SCREEN) {
                chain.proceed(arrayOf<Any?>(HIDDEN_LARGE_SCREEN, false))
            } else {
                chain.proceed()
            }
        }
    }

    /**
     * Answers with the margin a phone measures:
     *
     * ```java
     * heightPx - mTaskSize.bottom - actionsTopMarginPx - actionsHeight
     * ```
     *
     * The large-screen answer is the taskbar's height plus the top margin, and
     * Overview only has no taskbar, so it came to almost nothing and put the row
     * 266px below the stock one, against the gesture bar. This is the launcher's
     * own arithmetic rather than a position of this module's.
     */
    private fun keepPlaced(actionsView: Class<*>, profile: Class<*>) {
        val getBottomMargin = requireNotNull(Reflect.method(actionsView, "getBottomMargin"))
        val profileOf = requireNotNull(Reflect.field(actionsView, "mDp"))
        val taskSizeOf = requireNotNull(Reflect.field(actionsView, "mTaskSize"))
        val metrics = OverviewMetrics(context, profile)

        context.xposed.hook(getBottomMargin).intercept { chain ->
            runCatching {
                val deviceProfile = requireNotNull(profileOf.get(chain.thisObject))
                val taskSize = taskSizeOf.get(chain.thisObject) as Rect

                metrics.heightOf(deviceProfile) - taskSize.bottom - metrics.actionsSpaceOf(deviceProfile)
            }.getOrElse {
                context.logger.warn("Unable to place the Overview action row; keeping the launcher's own", it)
                chain.proceed()
            }
        }
    }

    /**
     * Gives the cards their bottom edge back above the row.
     *
     * `calculateGridSize` is the rectangle Overview lays everything out inside,
     * and it claims nothing below for the row on a large screen. Every card size
     * is derived from it — `calculateLargeTileSize` insets this rectangle and a
     * grid card is half of that — so claiming the room here is the one place
     * that moves the cards off the row, in the same arithmetic, on every call.
     *
     * The bottom edge is rewritten rather than the rectangle inset again,
     * because what the launcher applied is a `max` against the window inset and
     * insetting twice would take the larger of the two away twice over.
     */
    private fun keepRoom(profile: Class<*>) {
        val calculateGridSize = requireNotNull(
            Reflect.method(
                requireNotNull(context.findClass("com.android.quickstep.BaseContainerInterface")),
                "calculateGridSize",
                profile,
                Rect::class.java,
            ),
        )
        val claimedBelow = requireNotNull(
            Reflect.method(profile, "getOverviewActionsClaimedSpaceBelow"),
        )
        val metrics = OverviewMetrics(context, profile)

        context.xposed.hook(calculateGridSize).intercept { chain ->
            chain.proceed().also {
                runCatching {
                    val deviceProfile = requireNotNull(chain.args.getOrNull(0))
                    val out = chain.args.getOrNull(1) as Rect
                    val below = claimedBelow.invoke(deviceProfile) as Int

                    out.bottom = metrics.heightOf(deviceProfile) -
                        maxOf(metrics.bottomInsetOf(deviceProfile), below + metrics.actionsSpaceOf(deviceProfile))
                }.onFailure {
                    context.logger.warn("Unable to leave the Overview action row its room", it)
                }
            }
        }
    }

    /**
     * The few numbers those answers are built from.
     *
     * Every field is looked up once. Two of these answers are on the path the
     * app-to-Overview gesture recomputes on, `calculateGridSize` by way of
     * `calculateLargeTileSize`, and a `getDeclaredField` walk per call is not
     * something to put on the gesture thread.
     */
    private class OverviewMetrics(context: FeatureContext, profile: Class<*>) {

        private val propertiesOf = requireNotNull(Reflect.field(profile, "deviceProperties"))
        private val overviewOf = requireNotNull(Reflect.field(profile, "overviewProfile"))

        private val properties = requireNotNull(
            context.findClass("com.android.launcher3.deviceprofile.DeviceProperties"),
        )
        private val overview = requireNotNull(
            context.findClass("com.android.launcher3.deviceprofile.OverviewProfile"),
        )

        private val heightPx = requireNotNull(Reflect.field(properties, "heightPx"))
        private val insets = requireNotNull(Reflect.field(properties, "insets"))
        private val actionsTopMarginPx = requireNotNull(Reflect.field(overview, "actionsTopMarginPx"))
        private val actionsHeight = requireNotNull(Reflect.field(overview, "actionsHeight"))

        fun heightOf(deviceProfile: Any): Int = heightPx.getInt(propertiesOf.get(deviceProfile))

        fun bottomInsetOf(deviceProfile: Any): Int =
            (insets.get(propertiesOf.get(deviceProfile)) as Rect).bottom

        /** The row itself, and the gap the launcher leaves above it. */
        fun actionsSpaceOf(deviceProfile: Any): Int {
            val profile = overviewOf.get(deviceProfile)

            return actionsTopMarginPx.getInt(profile) + actionsHeight.getInt(profile)
        }
    }

    private companion object {
        /** `OverviewActionsView.HIDDEN_LARGE_SCREEN`, read off the launcher's dex. */
        const val HIDDEN_LARGE_SCREEN = 32
    }
}
