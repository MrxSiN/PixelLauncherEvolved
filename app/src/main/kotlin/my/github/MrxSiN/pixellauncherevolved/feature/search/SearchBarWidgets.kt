package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.view.View

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Recognises the search bar among the launcher's widget hosts.
 *
 * ```
 * com.android.launcher3.qsb.OseWidgetController
 *   public static void applyTo(
 *       com.android.launcher3.widget.LauncherAppWidgetHostView, boolean)
 * ```
 *
 * `CP4A.261105.040` gives the bar a host view class of its own again,
 * `com.android.launcher3.qsb.OseWidgetView`, as `CP2A.260805.005` did, and an
 * `isInstance` check is enough. `CP3A.260905.009` builds it as a plain
 * `LauncherAppWidgetHostView` like any other widget, so there is no type that
 * says which one it is. What is left there is the launcher's own setup call: the bar is built in two
 * places — `OseCustomWidget.createView` for the one on the workspace and
 * `QsbWidgetFactory.createView` for the one in the hotseat — and both hand the
 * host view to this single call, which nothing else is handed to. So the views
 * that pass through it are noted as they are set up.
 *
 * Views are held weakly, so a bar removed from the home screen is collected as
 * it would be otherwise. Both the setup call and the touch events that ask
 * [holds] run on the main thread, which is the only reason a plain weak set is
 * enough here.
 */
class SearchBarWidgets private constructor(
    private val bars: MutableSet<View>?,
    private val barType: Class<*>?,
) {

    /** Whether [view] is a search bar this launcher built. */
    fun holds(view: View?): Boolean {
        if (view == null) return false
        val type = barType
        return if (type != null) type.isInstance(view) else bars!!.contains(view)
    }

    companion object {

        /**
         * Null when the launcher has no such call, which leaves the feature
         * uninstalled rather than watching every widget on the home screen.
         *
         * The bar's own host class is asked for first, and only a subclass of
         * the widget host counts: a class of that name that is anything else
         * is not the bar. A launcher without it (`CP3A`) is asked for the call.
         */
        operator fun invoke(context: FeatureContext): SearchBarWidgets? {
            val hostView = context.findClass(HOST_VIEW) ?: return null
            barView(context, hostView)?.let { return SearchBarWidgets(null, it) }
            val controller = context.findClass(WIDGET_CONTROLLER) ?: return null
            val applyTo = Reflect.method(
                controller,
                APPLY_TO,
                hostView,
                Boolean::class.javaPrimitiveType!!,
            ) ?: return null

            val bars = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

            context.xposed.hook(applyTo).intercept { chain ->
                (chain.args.firstOrNull() as? View)?.let(bars::add)
                chain.proceed()
            }

            return SearchBarWidgets(bars, null)
        }

        /**
         * `com.android.launcher3.qsb.OseWidgetView`, the bar's own host view on
         * `CP4A.261105.040` and `CP2A.260805.005` (v0.0.7, verified on that build).
         */
        private fun barView(context: FeatureContext, hostView: Class<*>): Class<*>? =
            Reflect.exactClass(context.classLoader, BAR_VIEW)
                ?.takeIf { hostView.isAssignableFrom(it) && it != hostView }

        private const val WIDGET_CONTROLLER = "com.android.launcher3.qsb.OseWidgetController"
        private const val HOST_VIEW = "com.android.launcher3.widget.LauncherAppWidgetHostView"
        private const val APPLY_TO = "applyTo"
        private const val BAR_VIEW = "com.android.launcher3.qsb.OseWidgetView"
    }
}
