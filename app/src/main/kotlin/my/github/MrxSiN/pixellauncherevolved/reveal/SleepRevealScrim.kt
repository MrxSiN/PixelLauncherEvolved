package my.github.MrxSiN.pixellauncherevolved.reveal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View

import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Draws this module's screen off around the point it was asked for, inside
 * SystemUI.
 *
 * SystemUI reveals and unreveals the screen through one scrim, driven by an
 * effect that says where the light is at each step. A tap on the always-on
 * display wakes the screen with a circle opening out of the point tapped; a
 * screen off gets the lift effect, which has no point in it. The two are the
 * same drawing surface, so the gesture that ended the screen can be drawn the
 * way the tap that starts it is, in reverse, by answering the lift effect's own
 * step with a circle while a point from the launcher is standing.
 *
 * Nothing here reads the module's settings: a point is only ever announced by
 * a gesture that is switched on, and with no point this does nothing at all.
 * The point may come from the launcher's home screen or from the status bar
 * gesture next door; both leave it in the same [SleepRevealOrigin].
 */
class SleepRevealScrim(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val context: Context,
    private val logger: Logger,
    private val origin: SleepRevealOrigin,
) {

    fun install() {
        val scrim = findClass(SCRIM) ?: return
        val lift = findClass(LIFT) ?: return

        val float = Float::class.javaPrimitiveType!!
        val step = method(lift, "setRevealAmountOnScrim", float, scrim) ?: return
        val bounds = method(scrim, "setRevealGradientBounds", float, float, float, float) ?: return
        val alpha = method(scrim, "setRevealGradientEndColorAlpha", float) ?: return

        listen()
        draw(step, bounds, alpha)

        logger.info("SystemUI: a screen off this module asks for opens where the tap was")
    }

    /** Takes the point the launcher announces, on the main thread. */
    private fun listen() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val x = intent.getIntExtra(Bridge.EXTRA_X, MISSING)
                val y = intent.getIntExtra(Bridge.EXTRA_Y, MISSING)

                if (x == MISSING || y == MISSING) {
                    logger.warn("A screen off was announced without a point")
                    return
                }

                origin.remember(x, y)
            }
        }

        runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(Bridge.SLEEP_FROM),
                Bridge.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED,
            )
        }.onFailure { logger.warn("No point can be taken from the launcher", it) }
    }

    /**
     * Answers each step of the lift effect with the circle instead.
     *
     * The original is not called: it would set the same two values on the same
     * scrim straight after, and the last one written is the one drawn.
     *
     * This runs on every frame of every reveal in the device, so a step that
     * cannot be drawn around a point gives the lift effect its step back, and
     * one that fails gives the point up rather than failing again at 60Hz.
     */
    private fun draw(step: Method, bounds: Method, alpha: Method) {
        xposed.hook(step).intercept { chain ->
            val drawn = runCatching { drawCircle(chain.getArg(0), chain.getArg(1), bounds, alpha) }
                .onFailure {
                    logger.warn("The screen off could not be drawn around its point", it)
                    origin.forget()
                }
                .getOrDefault(false)

            if (drawn) null else chain.proceed()
        }
    }

    /**
     * @return true when this step was drawn as a circle, false to leave it to
     *   the lift effect: no point standing, or a scrim with no size to fit one
     *   to.
     */
    private fun drawCircle(amount: Any?, scrim: Any?, bounds: Method, alpha: Method): Boolean {
        val point = origin.current() ?: return false
        if (amount !is Float || scrim !is View) return false
        if (scrim.width <= 0 || scrim.height <= 0) return false

        val circle = CircleReveal.aroundPoint(point.x, point.y, scrim.width, scrim.height)

        alpha.invoke(scrim, circle.endColorAlpha(amount))
        circle.bounds(amount).let { (left, top, right, bottom) ->
            bounds.invoke(scrim, left, top, right, bottom)
        }

        // The screen is dark: this gesture has had its reveal.
        if (amount <= 0f) origin.forget()

        return true
    }

    private fun findClass(name: String): Class<*>? =
        runCatching { Host.clsOrThrow(classLoader, name) }
            .onFailure { logger.warn("SystemUI does not have $name; the screen off keeps its own reveal", it) }
            .getOrNull()

    private fun method(owner: Class<*>, name: String, vararg types: Class<*>): Method? =
        runCatching { Reflect.declaredMethod(owner, name, *types) }
            .onFailure { logger.warn("SystemUI does not have ${owner.simpleName}.$name", it) }
            .getOrNull()

    private companion object {
        const val SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
        const val LIFT = "com.android.systemui.statusbar.LiftReveal"
        const val MISSING = -1
    }
}
