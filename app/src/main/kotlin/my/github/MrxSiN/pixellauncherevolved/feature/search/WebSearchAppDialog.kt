package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * Asks which app opens a tapped Web Search result.
 *
 * The first row is the launcher's own answer, so the choice can always be
 * given back; the rest are what this device offers. A single-choice list
 * rather than a set of switches, because exactly one app opens the result.
 */
internal object WebSearchAppDialog {

    fun show(
        context: Context,
        resources: Resources,
        store: WebSearchAppStore,
        apps: WebSearchApps,
        onChosen: () -> Unit,
    ) {
        val installed = apps.installed()
        if (installed.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(resources.getString(R.string.feature_app_drawer_search_web_app_title))
                .setMessage(resources.getString(R.string.feature_app_drawer_search_web_app_none))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        // The launcher's own answer is the first row and the null choice, so
        // the list reads as one question with one answer already selected.
        val choices = listOf(null) + installed
        val labels = choices.map { app ->
            app?.label ?: resources.getString(R.string.feature_app_drawer_search_web_app_default_label)
        }
        val chosen = store.chosen()
        val selected = choices.indexOfFirst { it?.packageName == chosen }
            .takeIf { chosen != null && it >= 0 } ?: 0

        AlertDialog.Builder(context)
            .setTitle(resources.getString(R.string.feature_app_drawer_search_web_app_title))
            .setSingleChoiceItems(labels.map { it.toString() }.toTypedArray(), selected) { dialog, index ->
                store.choose(choices.getOrNull(index)?.packageName)
                onChosen()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
