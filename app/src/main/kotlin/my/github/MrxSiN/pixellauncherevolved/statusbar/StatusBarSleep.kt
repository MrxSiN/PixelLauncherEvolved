package my.github.MrxSiN.pixellauncherevolved.statusbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.ViewConfiguration

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.core.DoubleTap
import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.lock.ScreenLocker
import my.github.MrxSiN.pixellauncherevolved.lock.SystemUiScreenLocker
import my.github.MrxSiN.pixellauncherevolved.reveal.SleepRevealOrigin

/**
 * Turns the screen off when the status bar is tapped twice.
 *
 * The status bar is SystemUI's own view, not the launcher's, so this is the
 * one gesture in the module that the launcher never sees. It costs no root
 * either: SystemUI can end the screen itself ([SystemUiScreenLocker]).
 *
 * `onTouchEvent` rather than `dispatchTouchEvent`, so the taps that count are
 * the ones no child of the status bar wanted — the same rule the home screen
 * gesture uses, where only an empty spot counts. A double tap on the clock or
 * on a chip does what that chip does.
 *
 * The point tapped is left where the screen off's reveal will look for it, so
 * this gesture closes the screen around the finger exactly as the home screen
 * one does.
 *
 * The switch behind it is the launcher's, in the launcher's own settings file,
 * which this process cannot read — so it is told, and asks to be told again
 * when it starts. Until it has been told, the gesture is off.
 */
class StatusBarSleep(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val context: Context,
    private val logger: Logger,
    private val origin: SleepRevealOrigin,
    private val screen: ScreenLocker = SystemUiScreenLocker(context, logger),
) {

    private var isOn = false

    fun install() {
        val statusBar = findClass(STATUS_BAR_VIEW) ?: return

        val onTouch = runCatching { Reflect.declaredMethod(statusBar, "onTouchEvent", MotionEvent::class.java) }
            .onFailure { logger.warn("SystemUI's status bar does not report its touches", it) }
            .getOrNull() ?: return

        listen()
        watch(onTouch)
        ask()

        logger.info("SystemUI: two taps on the status bar turn the screen off, when switched on")
    }

    /** Takes the switch from the launcher, on the main thread. */
    private fun listen() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isOn = intent.getBooleanExtra(Bridge.EXTRA_ON, false)
                logger.info("Status bar double tap to sleep is ${if (isOn) "on" else "off"}")
            }
        }

        runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(Bridge.STATUS_BAR_SLEEP),
                Bridge.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED,
            )
        }.onFailure { logger.warn("The status bar gesture cannot be switched on from settings", it) }
    }

    private fun watch(onTouch: java.lang.reflect.Method) {
        val taps = DoubleTap(ViewConfiguration.get(context))

        xposed.hook(onTouch).intercept { chain ->
            val result = chain.proceed()

            runCatching {
                val event = chain.getArg(0) as? MotionEvent

                if (event != null && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (!isOn) taps.reset() else if (taps.isSecond(event)) sleep(event)
                }
            }.onFailure { logger.warn("A touch on the status bar could not be read", it) }

            result
        }
    }

    /** The point is left first, so the reveal has it before the screen goes. */
    private fun sleep(event: MotionEvent) {
        origin.remember(event.rawX.toInt(), event.rawY.toInt())

        if (!screen.lock()) origin.forget()
    }

    /**
     * Asks the launcher to say whether the gesture is on.
     *
     * SystemUI can be restarted on its own, and comes back knowing nothing.
     * The launcher is running — it is the home screen — and owns the answer.
     */
    private fun ask() {
        val intent = Intent(Bridge.ASK).setPackage(Bridge.LAUNCHER_PACKAGE)

        runCatching { context.sendBroadcast(intent) }
            .onFailure { logger.warn("The launcher could not be asked for the status bar switch", it) }
    }

    private fun findClass(name: String): Class<*>? =
        runCatching { Host.clsOrThrow(classLoader, name) }
            .onFailure { logger.warn("SystemUI does not have $name; the status bar gesture is not installed", it) }
            .getOrNull()

    private companion object {
        const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
    }
}
