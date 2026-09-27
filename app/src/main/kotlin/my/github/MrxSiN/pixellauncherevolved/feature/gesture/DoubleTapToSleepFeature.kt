package my.github.MrxSiN.pixellauncherevolved.feature.gesture

import android.view.MotionEvent
import android.view.ViewConfiguration

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.DoubleTap
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.lock.ScreenLock
import my.github.MrxSiN.pixellauncherevolved.reveal.SleepRevealAnnouncer
import my.github.MrxSiN.pixellauncherevolved.reveal.SleepRevealScrim

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
 * [ScreenLock] — so the module app sends the sleep key through root.
 *
 * Drawing it is not the launcher's either. Where the tap landed is announced
 * to SystemUI first, which opens the screen off around that point rather than
 * from nowhere — see [SleepRevealScrim].
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
        val reveal = SleepRevealAnnouncer(context.appContext, context.logger)
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

            if (taps.isSecond(event)) {
                // Announced first: the reveal is drawn by the time root answers.
                reveal.announce(event.rawX.toInt(), event.rawY.toInt())
                screen.off()
            }
        }

        context.logger.info("Home: double tap on an empty spot turns the screen off")
    }
}

/**
 * Asks this module's own app to end the screen.
 *
 * Off the main thread, because it is a binder call that may have to start that
 * app's process, and the gesture arrives on the one drawing the home screen.
 *
 * One thread is kept once the first tap has made it, so a later tap is handed
 * to a thread already waiting rather than one being started for it; a second
 * tap while the first is still waiting gets a thread of its own, as it always
 * did, and that one goes away again after a minute.
 */
private class ScreenOff(private val context: FeatureContext) {

    private val uri by lazy { ScreenLock.uri(context.xposed.moduleApplicationInfo.packageName) }

    fun off() {
        val resolver = context.appContext.contentResolver
        val logger = context.logger

        worker.execute {
            val answer = runCatching { resolver.call(uri, ScreenLock.LOCK, null, null) }
                .onFailure { logger.warn("The screen could not be reached to turn off", it) }
                .getOrNull()

            when {
                answer == null ->
                    logger.warn("Double tap to sleep: the module's app did not answer")

                !answer.getBoolean(ScreenLock.LOCKED) ->
                    logger.warn("Double tap to sleep: root sleep-key command failed")

                else -> logger.info("Double tap to sleep: screen off")
            }
        }
    }

    private val worker by lazy {
        ThreadPoolExecutor(1, Int.MAX_VALUE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, SynchronousQueue()) {
            Thread(it, "PixelLauncherEvolved-sleep").apply { isDaemon = true }
        }
    }

    private companion object {
        const val KEEP_ALIVE_SECONDS = 60L
    }
}
