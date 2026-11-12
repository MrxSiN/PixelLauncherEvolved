package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The fewest cells a Home screen item can shrink to, wherever this launcher keeps them.
 *
 * The latest launcher (`CP4A`) is asked first:
 * - a grid migration's `DbEntry` holds them in `resizeParams`, a `ResizeParams`;
 * - a model widget holds nothing, so they come from its provider, which
 *   `LauncherAppWidgetProviderInfo.fromProviderInfo` sizes for the current grid.
 *
 * The fallback, `CP3A` and earlier: `minSpanX`/`minSpanY` on every `ItemInfo`.
 *
 * Each item class is matched once, by which of those members it has, and the
 * match is kept. Anything that is not a widget takes one cell, as `ItemInfo`
 * always defaulted to.
 */
class MinSpans(private val classLoader: ClassLoader) {

    private fun interface Reader {
        /** The span packed: x in the high half, y in the low half. */
        fun read(context: Context?, item: Any, view: View?): Int
    }

    private val readers = ConcurrentHashMap<Class<*>, Reader>()

    /**
     * [item]'s smallest size, packed; read it with [x] and [y].
     *
     * A widget's [view], when there is one, answers from the provider it already
     * holds, with no call out of the process: drag paths pass it on every move.
     * Without one, [context] asks the widget service, which is only for cold paths.
     */
    fun of(context: Context?, item: Any, view: View? = null): Int =
        readers.getOrPut(item.javaClass) { readerFor(item.javaClass) }.read(context, item, view)

    private fun readerFor(type: Class<*>): Reader {
        Reflect.field(type, RESIZE_PARAMS)?.let { params ->
            pair(params.type)?.let { (x, y) ->
                return Reader { _, item, _ -> params.get(item)?.let { pack(x.getInt(it), y.getInt(it)) } ?: ONE }
            }
        }

        // An item that still carries its own (CP3A and earlier) is read directly.
        val own = pair(type)
        if (own != null) {
            val (x, y) = own
            return Reader { _, item, _ -> pack(x.getInt(item), y.getInt(item)) }
        }

        val widgetId = Reflect.field(type, APP_WIDGET_ID) ?: return Reader { _, _, _ -> ONE }
        val provider = providerSpans() ?: return Reader { _, _, _ -> ONE }
        return Reader { context, item, view ->
            val held = provider.ofView(view)
            if (held != NONE) held else context?.let { provider.ofId(it, widgetId.getInt(item)) } ?: ONE
        }
    }

    private class ProviderSpans(private val type: Class<*>, private val from: Method, private val x: Field, private val y: Field) {
        /** [NONE] unless [view] holds this launcher's sized provider. */
        fun ofView(view: View?): Int {
            val info = (view as? AppWidgetHostView)?.appWidgetInfo ?: return NONE
            return if (type.isInstance(info)) pack(x.getInt(info), y.getInt(info)) else NONE
        }

        fun ofId(context: Context, appWidgetId: Int): Int {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) ?: return ONE
            val sized = from.invoke(null, context, info) ?: return ONE
            return pack(x.getInt(sized), y.getInt(sized))
        }
    }

    private var provider: ProviderSpans? = null

    private fun providerSpans(): ProviderSpans? = provider ?: runCatching {
        val type = Host.clsOrThrow(classLoader, PROVIDER_INFO)
        val from = Reflect.method(type, FROM_PROVIDER_INFO, Context::class.java, AppWidgetProviderInfo::class.java)
        val (x, y) = pair(type) ?: return null
        from?.let { ProviderSpans(type, it, x, y) }
    }.getOrNull()?.also { provider = it }

    private fun pair(type: Class<*>): Pair<Field, Field>? {
        val x = Reflect.field(type, MIN_SPAN_X) ?: return null
        val y = Reflect.field(type, MIN_SPAN_Y) ?: return null
        return if (x.type == Int::class.javaPrimitiveType && y.type == Int::class.javaPrimitiveType) x to y else null
    }

    companion object {
        fun x(span: Int): Int = (span ushr 16).coerceAtLeast(1)

        fun y(span: Int): Int = (span and 0xFFFF).coerceAtLeast(1)

        // 15 bits each, so a packed span is never negative and NONE stays free.
        private fun pack(x: Int, y: Int): Int = (x.coerceIn(0, 0x7FFF) shl 16) or y.coerceIn(0, 0x7FFF)

        private val ONE = pack(1, 1)
        private const val NONE = -1

        const val MIN_SPAN_X = "minSpanX"
        const val MIN_SPAN_Y = "minSpanY"
        const val RESIZE_PARAMS = "resizeParams"
        const val APP_WIDGET_ID = "appWidgetId"
        const val PROVIDER_INFO = "com.android.launcher3.widget.LauncherAppWidgetProviderInfo"
        const val FROM_PROVIDER_INFO = "fromProviderInfo"
    }
}
