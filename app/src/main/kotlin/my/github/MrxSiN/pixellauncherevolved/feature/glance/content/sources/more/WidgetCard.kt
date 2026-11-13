package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * One chosen app widget inside At a Glance. Rewritten from Smartspacer's `WidgetTarget`.
 *
 * The widget is hosted by an [AppWidgetHost] of this module's own id ([WidgetChoice.hostId]); each
 * `updateAppWidget(RemoteViews)` its host view receives is kept and handed to At a Glance as the
 * card's remote views, which the launcher draws. The choice is stored as "component|id"; the id was
 * allocated and bound by the options dialog and is released there when the choice changes, and
 * here when the source is switched off.
 */
class WidgetCard : GlanceSource("widget", Kind.CARD, R.string.glance_widget_title, R.string.glance_widget_description) {

    override val options = listOf(GlanceOption(WidgetChoice.OPTION, R.string.glance_widget_option_title, GlanceOption.Type.WIDGET, R.string.glance_widget_none))

    @Volatile private var views: RemoteViews? = null

    /** [views] inside this module's frame, padded clear of the page dots; built when either changes, not per list. */
    @Volatile private var framed: RemoteViews? = null
    @Volatile private var widgetId = -1

    // Main thread only.
    private var appHost: AppWidgetHost? = null
    private var bound: String? = null

    /** Keeps what the provider sends instead of drawing it; the launcher draws it in At a Glance. */
    private inner class Capture(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            if (remoteViews == null || appWidgetId != widgetId) return
            views = remoteViews
            frame()
            host.changed()
        }
    }

    override fun start() {
        val context = host.context
        appHost = object : AppWidgetHost(context, WidgetChoice.hostId(host.modulePackage)) {
            override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = Capture(context)
        }.also { it.startListening() }
        bind()
    }

    override fun onOptionsChanged() = bind()

    /** Hosts the widget the option names now, unless it is the one already hosted. */
    private fun bind() {
        val value = option(WidgetChoice.OPTION)
        if (value == bound) return
        bound = value
        views = null
        framed = null
        widgetId = -1
        val id = WidgetChoice.decode(value)?.second
        val hosted = appHost
        if (id != null && hosted != null) {
            runCatching {
                val context = host.context
                val manager = AppWidgetManager.getInstance(context)
                val info = manager.getAppWidgetInfo(id) ?: return@runCatching
                widgetId = id
                size(manager, id)
                // Delivers what the service already holds for it; later updates arrive through startListening.
                hosted.createView(context, id, info)
            }.onFailure { host.logger.warn("The At a Glance widget could not be hosted", it) }
        }
        host.changed()
    }

    /** The card's measured size changed: tell the provider, which answers with views for it. */
    override fun onCardSizeChanged() {
        frame()
        host.changed()
        val id = widgetId
        if (id < 0) return
        runCatching { size(AppWidgetManager.getInstance(host.context), id) }.onFailure { host.logger.warn("The At a Glance widget could not be resized", it) }
    }

    /** Asks the provider for the card's size: measured on a live view, or estimated before one is laid out. */
    private fun size(manager: AppWidgetManager, id: Int) {
        val measured = host.cardSize
        val metrics = host.context.resources.displayMetrics
        val (width, height) = if (measured != null) measured.width to measured.height else WidgetChoice.cardSize(metrics.widthPixels, metrics.density)
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
        })
    }

    override fun stop() {
        val hosted = appHost
        val id = WidgetChoice.decode(option(WidgetChoice.OPTION))?.second
        runCatching { hosted?.stopListening() }
        if (hosted != null && id != null) runCatching { hosted.deleteAppWidgetId(id) }
        // A released id is no use stored; switching back on asks for a widget again.
        runCatching { LauncherSettings.preferences(host.context).edit().remove(GlanceOptions.key(this.id, WidgetChoice.OPTION)).apply() }
        appHost = null
        bound = null
        widgetId = -1
        views = null
        framed = null
    }

    /**
     * The launcher stretches a card's remote views over the whole page, under the page dots, so the
     * widget goes inside a frame of this module's whose bottom padding is the dots' height.
     */
    private fun frame() {
        val shown = views
        framed = if (shown == null) null else runCatching {
            RemoteViews(host.modulePackage, R.layout.glance_widget_frame).apply {
                addView(R.id.glance_widget_frame, shown)
                setViewPadding(R.id.glance_widget_frame, 0, 0, 0, host.cardInset)
            }
        }.onFailure { host.logger.warn("The At a Glance widget could not be framed", it) }.getOrNull() ?: shown
    }

    override fun content(): GlanceItem? {
        val shown = framed ?: views ?: return null
        return GlanceItem(title = host.string(R.string.glance_widget_title), remoteViews = shown)
    }
}
