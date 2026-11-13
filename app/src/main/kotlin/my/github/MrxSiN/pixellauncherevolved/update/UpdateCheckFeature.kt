package my.github.MrxSiN.pixellauncherevolved.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Check for updates: once each time Pixel Launcher starts, asks this module's app ([UpdateProvider],
 * which can reach the Internet) for the newest release on GitHub. A newer one than the module
 * installed is offered in a dialog the next time the home screen comes up: **Update** opens the
 * release page, **Later** closes it. Nothing else happens; a failure is only logged.
 *
 * No hook. The request waits a little after start so it never competes with the first bind, runs on
 * one short-lived thread, and the launcher only ever hears back one small answer.
 */
class UpdateCheckFeature : ToggleFeature(Settings.UPDATE_CHECK) {

    override fun install(context: FeatureContext) {
        val app = context.appContext
        val resources = context.moduleResources ?: return
        val module = context.xposed.moduleApplicationInfo.packageName
        val logger = context.logger
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({
            Thread({ check(app, resources, module, logger, main) }, THREAD).apply { isDaemon = true }.start()
        }, START_DELAY_MS)
    }

    private fun check(app: Context, resources: Resources, module: String, logger: Logger, main: Handler) {
        runCatching {
            val current = app.packageManager.getPackageInfo(module, 0).versionName ?: return
            val answer = app.contentResolver.call(Uri.parse("content://" + UpdateWire.authority(module)), UpdateWire.METHOD_LATEST, null, null)
            UpdateWire.errorOf(answer)?.let { error("the module app could not reach GitHub: $it") }
            val page = UpdateWire.locationOf(answer) ?: error("no answer from the module app")
            val tag = UpdateWire.tagOf(page) ?: error("not a release page: $page")
            logger.info("Update check: newest $tag, installed $current")
            if (UpdateWire.isNewer(tag, current)) main.post { offerOnNextHome(app, resources, tag, current, page, logger) }
        }.onFailure { logger.warn("Update check failed: ${it.message}") }
    }

    /** Waits for the home screen to come up, then offers the update once. */
    private fun offerOnNextHome(app: Context, resources: Resources, tag: String, current: String, page: String, logger: Logger) {
        val application = app.applicationContext as? Application ?: return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity.intent?.hasCategory(Intent.CATEGORY_HOME) != true) return
                application.unregisterActivityLifecycleCallbacks(this)
                runCatching { offer(activity, resources, tag, current, page) }
                    .onFailure { logger.warn("The update could not be offered", it) }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun offer(activity: Activity, resources: Resources, tag: String, current: String, page: String) {
        ExpressiveDialog(activity)
            .title(resources.getString(R.string.update_dialog_title))
            .message(resources.getString(R.string.update_dialog_message, tag.removePrefix("v"), current))
            .confirm(resources.getString(R.string.update_dialog_update)) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .dismiss(resources.getString(R.string.update_dialog_later))
            .show()
    }

    private companion object {
        const val THREAD = "ple-update-check"
        /** After the first bind and the first frames, so the check never competes with start. */
        const val START_DELAY_MS = 15_000L
    }
}
