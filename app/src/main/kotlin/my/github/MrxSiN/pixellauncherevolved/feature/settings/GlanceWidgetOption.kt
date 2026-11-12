package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.WidgetChoice

/**
 * The Widget card's option: lists the installed app widgets, and on a pick allocates and binds a
 * widget id under this module's host id ([WidgetChoice.hostId]), which the launcher's process may do
 * because it holds BIND_APPWIDGET. The id it replaces is deleted. The card hosts the id; this only
 * allocates and releases it.
 */
internal object GlanceWidgetOption {

    private class Entry(val component: String, val label: CharSequence)

    /** The label of the widget [value] names, or null when it is unset or the widget is gone. */
    fun label(context: Context, value: String?): CharSequence? {
        val id = WidgetChoice.decode(value)?.second ?: return null
        return runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.loadLabel(context.packageManager) }.getOrNull()
    }

    /** Lists the providers off the main thread, asks which, and stores the result through [store]. */
    fun choose(scope: RowScope, option: GlanceOption, current: String?, store: (String?) -> Unit) {
        val context = scope.context.applicationContext ?: scope.context
        FocusPagesDialog.readThen(scope.context, { providers(scope, context) }) { entries ->
            val none = listOf<Pair<String?, CharSequence>>(null to scope.string(option.defaultRes))
            FocusPagesDialog.choose(
                scope.context, scope.resources, scope.string(option.titleRes),
                none + entries.map { it.component to it.label }, WidgetChoice.decode(current)?.first,
            ) { component, _ ->
                runCatching { apply(scope, context, current, component, store) }
                    .onFailure { scope.environment.logger.warn("The At a Glance widget could not be chosen", it) }
            }
        }
    }

    private fun providers(scope: RowScope, context: Context): List<Entry> {
        val pm = context.packageManager
        return AppWidgetManager.getInstance(context).getInstalledProvidersForProfile(Process.myUserHandle()).map { info ->
            val app = runCatching { pm.getApplicationInfo(info.provider.packageName, 0).loadLabel(pm) }.getOrDefault(info.provider.packageName)
            Entry(info.provider.flattenToString(), scope.string(R.string.glance_widget_choice, info.loadLabel(pm), app))
        }.sortedBy { it.label.toString().lowercase() }
    }

    private fun apply(scope: RowScope, context: Context, current: String?, component: String?, store: (String?) -> Unit) {
        val old = WidgetChoice.decode(current)
        if (component != null && component == old?.first) return
        val host = AppWidgetHost(context, WidgetChoice.hostId(scope.environment.modulePackage))
        if (component != null) {
            val provider = ComponentName.unflattenFromString(component) ?: return
            val manager = AppWidgetManager.getInstance(context)
            val id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
                host.deleteAppWidgetId(id)
                scope.environment.logger.warn("The launcher may not bind the widget ${provider.flattenToShortString()}")
                return
            }
            store(WidgetChoice.encode(component, id))
            configure(scope, host, manager, id)
        } else {
            store(null)
        }
        if (old != null) runCatching { host.deleteAppWidgetId(old.second) }
    }

    /** Best effort: starts the widget's own setup screen, if it has one, and does not wait for its result. */
    private fun configure(scope: RowScope, host: AppWidgetHost, manager: AppWidgetManager, id: Int) {
        val configure = manager.getAppWidgetInfo(id)?.configure ?: return
        runCatching {
            val activity = scope.context as? Activity
            if (activity != null) {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, id, null)
            } else {
                scope.context.startActivity(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }.onFailure { scope.environment.logger.warn("The widget's setup screen could not be opened", it) }
    }
}
