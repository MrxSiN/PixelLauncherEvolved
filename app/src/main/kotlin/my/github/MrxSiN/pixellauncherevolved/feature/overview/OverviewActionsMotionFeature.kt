package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Gives the Overview action buttons an arrival of their own.
 *
 * The launcher fades the row holding Screenshot, Select and Clear all up over
 * the length of the Overview opening, so they are lit and in place while the
 * task cards are still moving — three labels that came up with the background
 * rather than a row of buttons that arrived. This replaces that fade with an
 * entrance run off the same progress, so the buttons come to rest on the frame
 * the cards do.
 *
 * The row's opacity is what says how far along the opening is, because it is
 * the launcher's own account of it. The hidden flags behind it are not: they
 * are rewritten every frame and read the same throughout, including while
 * Overview is closed.
 *
 * Coming to Overview from an app the launcher puts that row up already opaque,
 * with the app's shrink into its card already over, so there is no progress to
 * follow and the arrival runs on a clock instead.
 *
 * Nothing gates this feature. It hides nothing and removes nothing — the
 * buttons still arrive, within the same transition — so there is no question
 * for a person to answer about it.
 */
class OverviewActionsMotionFeature : LauncherFeature {

    override val id: String = "overview_actions_motion"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    /** When a task card last finished shrinking out of an app. */
    private val cards = CardArrival()

    override fun install(context: FeatureContext) {
        val actionsView = OverviewActionsRow.find(context)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        context.hookAfter(actionsView, "onFinishInflate") { view, _ ->
            watch(view as ViewGroup)
        }

        val taskView = context.findClass(TASK_VIEW_CLASS)
        if (taskView == null) {
            context.logger.warn("Overview task cards are not available; the arrival waits for the fade")
            return
        }

        context.hookAfter(taskView, SET_FULLSCREEN_PROGRESS, Float::class.javaPrimitiveType!!) { _, args ->
            (args.firstOrNull() as? Float)?.let(cards::onFullscreenProgress)
        }
    }

    /**
     * Follows one row's opacity for as long as it exists.
     *
     * A draw listener rather than a hook, because the opacity is written by the
     * launcher's own animator through several paths and this is the one place
     * that sees the result of all of them. What it costs is a float comparison
     * per frame, made only while Overview's own row is part of the window.
     */
    private fun watch(actionsView: ViewGroup) {
        val watcher = ShownWatcher(actionsView, cards)

        actionsView.viewTreeObserver.addOnPreDrawListener(watcher)
        actionsView.addOnAttachStateChangeListener(watcher)
    }

    /**
     * Drives the arrival from the launcher's own fade of the row.
     *
     * The launcher ramps that opacity from nothing to full across exactly the
     * transition the task cards are flying in on, so it is both the clock and
     * the finish line: placing the buttons from it puts the last of them at
     * rest on the frame Overview has arrived, at whatever speed the gesture
     * that opened it ran.
     *
     * Whether the row is being shown at all is asked of the buttons rather than
     * of the actions view, whose own opacity sits at one even while Overview is
     * closed and the view above it is simply not visible.
     */
    private class ShownWatcher(
        private val actionsView: ViewGroup,
        private val cards: CardArrival,
    ) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

        /**
         * The row of buttons, looked up once.
         *
         * This runs before every frame the launcher draws, so the lookup — a
         * resource name resolved to an id, then a search of the tree — is done
         * when it first succeeds and not again.
         */
        private var buttons: ViewGroup? = null
        private var state = State.AWAY

        /** The arrival of an opening that had no fade to be placed from. */
        private var timed: ValueAnimator? = null

        /** How long the row has been up and already opaque. */
        private var opaqueFrames = 0

        override fun onPreDraw(): Boolean {
            val row = buttons ?: findButtons()?.also { buttons = it } ?: return true

            when {
                !row.isShown -> away(row)
                row.alpha < ARRIVED_ALPHA -> follow(row)
                else -> arrive(row)
            }

            return true
        }

        /**
         * Places the buttons from the launcher's fade of the row.
         *
         * This is the opening seen from the home screen, and the fade is the
         * transition's own progress, so the last button lands on the frame the
         * task cards do.
         */
        private fun follow(row: ViewGroup) {
            opaqueFrames = 0
            cancelTimed()

            if (OverviewActionsEntrance.applyTo(row, visibleProgress(row.alpha))) {
                state = State.ARRIVING
            }
        }

        /**
         * The part of the fade an arrival can be seen in.
         *
         * A button's opacity is its own times the row's, so while the row is
         * nearly transparent nothing it does is visible: an arrival spread over
         * the whole fade spends its first half where no one can see it, and
         * what is left reads as a plain fade with no movement in it. Starting
         * once the row is showing puts the whole of the movement where a person
         * is watching, and it still ends where the fade ends.
         */
        private fun visibleProgress(alpha: Float): Float =
            ((alpha - VISIBLE_FROM) / (1f - VISIBLE_FROM)).coerceIn(0f, 1f)

        /**
         * Handles a row that is up and fully opaque.
         *
         * Which is two different things. After a fade it means the opening has
         * finished, and the buttons are simply left where that put them. With
         * no fade at all it means the launcher has put the row up finished —
         * what coming to Overview from an app does, the app having already
         * shrunk into its card — and then the arrival has to be run on a clock,
         * because there is nothing left moving to run it from.
         *
         * A few frames are given before deciding it is the second: from the
         * home screen the row is briefly opaque before the fade starts, and
         * starting a clock there would be a second arrival interrupting the
         * one the transition is about to ask for. The buttons are held out of
         * sight for that wait, which is where either arrival begins anyway.
         */
        private fun arrive(row: ViewGroup) {
            when (state) {
                State.ARRIVING -> {
                    OverviewActionsEntrance.settle(row)
                    state = State.ARRIVED
                }

                State.AWAY -> {
                    if (!OverviewActionsEntrance.applyTo(row, 0f)) return

                    // A card that has just landed says this is the opening from
                    // an app, which has no fade coming: there is nothing to wait
                    // for, and the buttons are already a moment behind the cards
                    // because the launcher puts this row up late.
                    if (cards.justArrived() || ++opaqueFrames >= FRAMES_BEFORE_CLOCK) {
                        timed = OverviewActionsEntrance.animator(row).also { it.start() }
                        state = State.ARRIVED
                    }
                }

                State.ARRIVED -> Unit
            }
        }

        /** Gives the row back as the launcher would have it. */
        private fun away(row: ViewGroup) {
            opaqueFrames = 0
            cancelTimed()

            if (state != State.AWAY) OverviewActionsEntrance.settle(row)
            state = State.AWAY
        }

        private fun cancelTimed() {
            timed?.cancel()
            timed = null
        }

        override fun onViewAttachedToWindow(view: View) {
            // A re-attach gives the row a different observer to be registered
            // with; removing first keeps one listener rather than two.
            view.viewTreeObserver.removeOnPreDrawListener(this)
            view.viewTreeObserver.addOnPreDrawListener(this)
        }

        override fun onViewDetachedFromWindow(view: View) {
            cancelTimed()
            opaqueFrames = 0
            state = State.AWAY
        }

        private fun findButtons(): ViewGroup? =
            actionsView.findViewById(LauncherResources(actionsView.context).id(BUTTON_ROW_ID))

        /** Where the buttons are in their arrival. */
        private enum class State { AWAY, ARRIVING, ARRIVED }

        private companion object {
            /** At this the launcher's fade of the row is over, or never ran. */
            const val ARRIVED_ALPHA = 0.99f

            /** Below this the row is too faint for an arrival to be seen in. */
            const val VISIBLE_FROM = 0.3f

            /** Frames of an opaque row before the arrival is put on a clock. */
            const val FRAMES_BEFORE_CLOCK = 4

            const val BUTTON_ROW_ID = "action_buttons"
        }
    }

    private companion object {
        const val TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView"

        /** `setFullscreenProgress(float)`: 1 while the card fills the screen. */
        const val SET_FULLSCREEN_PROGRESS = "setFullscreenProgress"
    }
}

/**
 * When a task card last finished shrinking out of an app.
 *
 * Coming to Overview from an app, the launcher shrinks the app into its card
 * and only then puts the action row up — measured at about 215ms after the card
 * has landed on a Pixel 8 Pro. The buttons are already behind the rest of the
 * screen by then, so the arrival starts on the frame the row can first be drawn
 * rather than waiting to find out whether a fade is coming.
 *
 * A card is landing whenever it is told a progress on its way to zero; it has
 * landed when that progress arrives at zero. The launcher sets the same
 * progress to zero over and over on cards that are simply sitting there, which
 * is why the fall is what counts rather than the value.
 */
private class CardArrival {

    private var wasShrinking = false
    private var arrivedAt = 0L

    fun onFullscreenProgress(progress: Float) {
        if (progress > SETTLED) {
            wasShrinking = true
            return
        }

        if (wasShrinking) {
            wasShrinking = false
            arrivedAt = SystemClock.uptimeMillis()
        }
    }

    /** Whether a card landed recently enough for this opening to be that one. */
    fun justArrived(): Boolean = SystemClock.uptimeMillis() - arrivedAt < RECENTLY_MS

    private companion object {
        const val SETTLED = 0.01f

        /**
         * How long after a card lands the row going up still counts as the same
         * opening. Comfortably past the launcher's own delay, and far short of
         * anything a person would read as a separate action.
         */
        const val RECENTLY_MS = 1_000L
    }
}
