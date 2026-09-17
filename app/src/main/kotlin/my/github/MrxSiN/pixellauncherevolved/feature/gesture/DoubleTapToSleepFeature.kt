package my.github.MrxSiN.pixellauncherevolved.feature.gesture

import android.view.MotionEvent
import android.view.ViewConfiguration

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.lock.ScreenLock

/**
 * Turns the screen off when an empty part of the home screen is tapped twice.
 *
 * The launcher already decides, on every touch, whether it landed on empty
 * space with home at rest — that is what its long press on the wallpaper is
 * built on — and records the answer:
 *
 * ```java
 * // WorkspaceTouchListener.onTouch, ACTION_DOWN
 * if (isInNormalState && noFloatingViewOpen && workspaceRect.contains(x, y)) {
 *     mLongPressState = STATE_REQUESTED;   // 1
 * }
 * // …and on ACTION_UP or ACTION_CANCEL
 * mLongPressState = STATE_CANCELLED;       // 0
 * ```
 *
 * so the same field says which taps count. Only the second half is this
 * module's: two such taps inside the platform's own double-tap window and slop.
 *
 * The launcher's own `GestureDetector` would report a double tap for free, but
 * it reports every double tap the workspace sees, including the ones on an icon
 * and the ones taken while the launcher is not at rest. This asks the question
 * the launcher already answered instead.
 *
 * Turning the screen off is not something the launcher can do — see
 * [ScreenLock] — so the module app sends the power-key event through root.
 */
class DoubleTapToSleepFeature : ToggleFeature(Settings.HOME_DOUBLE_TAP_TO_SLEEP) {

    override val compatibility = CompatibilityFeature.DOUBLE_TAP_TO_SLEEP

    override fun install(context: FeatureContext) {
        val listener = context.findClass("com.android.launcher3.touch.WorkspaceTouchListener")
        if (listener == null) {
            context.logger.warn("The workspace does not report its touches in this launcher")
            return
        }

        val longPressState = Reflect.field(listener, "mLongPressState")
        if (longPressState == null) {
            context.logger.warn("The workspace does not say which touches land on empty space")
            return
        }

        val screen = ScreenOff(context)
        val taps = DoubleTap(ViewConfiguration.get(context.appContext))

        context.hookAfter(
            listener,
            "onTouch",
            android.view.View::class.java,
            MotionEvent::class.java,
        ) { host, args ->
            val event = args.getOrNull(1) as? MotionEvent ?: return@hookAfter
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return@hookAfter

            // Zero once the previous gesture ended, so this is about this tap.
            val onEmptySpace = longPressState.getInt(host) != 0

            if (!onEmptySpace || !isEnabled(context.settings)) {
                taps.reset()
                return@hookAfter
            }

            if (taps.isSecond(event)) screen.off()
        }

        context.logger.info("Home: double tap on an empty spot turns the screen off")
    }
}

/** The platform's own idea of what counts as one tap following another. */
private class DoubleTap(configuration: ViewConfiguration) {

    private val slop = configuration.scaledDoubleTapSlop.toFloat()
    private val timeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var lastTime = 0L
    private var lastX = 0f
    private var lastY = 0f

    /**
     * @return true when [event] completes a double tap, which also spends it:
     *   three taps are one double tap and one first tap, not two.
     */
    fun isSecond(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val time = event.eventTime

        val second = time - lastTime <= timeout &&
            Math.hypot((x - lastX).toDouble(), (y - lastY).toDouble()) <= slop

        lastTime = if (second) 0L else time
        lastX = x
        lastY = y

        return second
    }

    /** A non-empty or disabled tap breaks the sequence. */
    fun reset() {
        lastTime = 0L
    }
}

/**
 * Asks this module's own app to end the screen.
 *
 * On its own thread, because it is a binder call that may have to start that
 * app's process, and the gesture arrives on the one drawing the home screen.
 */
private class ScreenOff(private val context: FeatureContext) {

    private val uri by lazy { ScreenLock.uri(context.xposed.moduleApplicationInfo.packageName) }

    fun off() {
        val resolver = context.appContext.contentResolver
        val logger = context.logger

        Thread {
            val answer = runCatching { resolver.call(uri, ScreenLock.LOCK, null, null) }
                .onFailure { logger.warn("The screen could not be reached to turn off", it) }
                .getOrNull()

            when {
                answer == null ->
                    logger.warn("Double tap to sleep: the module's app did not answer")

                !answer.getBoolean(ScreenLock.LOCKED) ->
                    logger.warn("Double tap to sleep: root power-key command failed")

                else -> logger.info("Double tap to sleep: screen off")
            }
        }.start()
    }
}
