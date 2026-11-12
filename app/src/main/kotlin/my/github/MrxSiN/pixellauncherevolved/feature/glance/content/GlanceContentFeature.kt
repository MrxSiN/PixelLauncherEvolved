package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.app.smartspace.SmartspaceTarget
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.View

import java.lang.reflect.Method
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Adds this module's own At a Glance content: information cards and extra information rewritten
 * from Smartspacer's built-ins ([GlanceSources]), on the home screen only.
 *
 * ```
 * com.google.android.systemui.smartspace.BcSmartspaceView
 *   onSmartspaceTargetsUpdated(List, Runnable)     // every At a Glance view, copies included
 * ```
 * Each view is handed the system Smartspace session's targets; the hook hands it those plus the
 * switched-on sources' content ([GlanceTargets.merge]). The list each view was last given is kept
 * (weakly, per view), so a source changing hands the same views their list again through the
 * public one-argument overload. The lock screen draws its own view in SystemUI and is untouched.
 */
class GlanceContentFeature : LauncherFeature, SharedPreferences.OnSharedPreferenceChangeListener {

    override val id: String = "glance_content"

    override val compatibility = CompatibilityFeature.AT_A_GLANCE

    override fun isEnabled(settings: SettingsSource): Boolean = true

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Sources switched on now, in [GlanceSources.all] order. Written on the main thread only. */
    private val running = LinkedHashMap<String, GlanceSource>()

    /**
     * [running] as the hook reads it. The launcher hands its views their lists from a background
     * thread as well as the main one, so the hook only ever reads this immutable copy.
     */
    @Volatile
    private var active: List<GlanceSource> = emptyList()

    /** The list each live view was last handed by the launcher. Guarded by itself. */
    private val handed = WeakHashMap<View, List<*>>()

    private lateinit var targets: GlanceTargets
    private lateinit var update: Method
    private lateinit var preferences: SharedPreferences
    private lateinit var host: GlanceHost

    private var refreshPosted = false

    override fun install(context: FeatureContext) {
        val view = context.findClass(SMARTSPACE_VIEW)
        val updated = view?.let { Reflect.declared(it, UPDATED) { m -> m.parameterTypes.size == 2 && m.parameterTypes[0] == List::class.java } }
        val publicUpdate = view?.let { Reflect.declared(it, UPDATED) { m -> m.parameterTypes.size == 1 && m.parameterTypes[0] == List::class.java } }
        if (updated == null || publicUpdate == null) {
            context.logger.warn("The launcher's At a Glance view is unavailable; At a Glance content is not installed")
            return
        }
        update = publicUpdate
        targets = GlanceTargets(ComponentName(context.appContext.packageName, COMPONENT))
        preferences = LauncherSettings.preferences(context.appContext)
        host = Host(context.appContext, context.moduleResources, context.xposed.moduleApplicationInfo.packageName, main, context.logger)

        context.xposed.hook(updated).intercept { chain ->
            val owner = chain.thisObject as? View
            val given = chain.args[0] as? List<*>
            if (owner != null && given != null) synchronized(handed) { handed[owner] = given }
            if (owner == null || given == null || active.isEmpty()) return@intercept chain.proceed()
            val merged = runCatching { merge(given) }
                .onFailure { context.logger.warn("At a Glance content could not be added", it) }
                .getOrNull() ?: given
            chain.proceed(chain.args.toTypedArray().also { it[0] = merged })
        }

        main.post { sync() }
        preferences.registerOnSharedPreferenceChangeListener(this)
        context.logger.info("At a Glance content ready")
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key != null && key.startsWith(GlanceSources.KEY_PREFIX)) main.post { sync() }
    }

    /** Starts the sources switched on and stops the ones switched off, then refreshes. */
    private fun sync() {
        for (source in GlanceSources.all) {
            val on = preferences.getBoolean(GlanceSources.key(source), false)
            val isRunning = source.id in running
            if (on && !isRunning) {
                source.host = host
                runCatching { source.start() }
                    .onSuccess { running[source.id] = source }
                    .onFailure { host.logger.warn("At a Glance source ${source.id} could not start", it) }
            } else if (!on && isRunning) {
                running.remove(source.id)
                runCatching { source.stop() }.onFailure { host.logger.warn("At a Glance source ${source.id} could not stop", it) }
            }
        }
        active = running.values.toList()
        refresh()
    }

    private fun merge(given: List<*>): List<*> {
        val cards = ArrayList<SmartspaceTarget>()
        val extras = ArrayList<Pair<String, GlanceItem>>()
        for (source in active) {
            val item = runCatching { source.content() }
                .onFailure { host.logger.warn("At a Glance source ${source.id} failed", it) }
                .getOrNull() ?: continue
            val id = ID_PREFIX + source.id
            if (source.kind == GlanceSource.Kind.CARD) cards += targets.card(id, item) else extras += id to item
        }
        return targets.merge(given, cards, extras)
    }

    /** Hands every live At a Glance view its last list again, once per burst of changes. */
    private fun refresh() {
        if (refreshPosted) return
        refreshPosted = true
        main.post {
            refreshPosted = false
            val views = synchronized(handed) { handed.entries.map { it.key to it.value } }
            for ((view, given) in views) {
                runCatching { update.invoke(view, given) }
                    .onFailure { host.logger.warn("At a Glance could not be refreshed", it) }
            }
        }
    }

    private inner class Host(
        override val context: Context,
        override val resources: Resources?,
        override val modulePackage: String,
        override val main: Handler,
        override val logger: Logger,
    ) : GlanceHost {
        override fun changed() = refreshLater()
    }

    private fun refreshLater() {
        main.post { refresh() }
    }

    private companion object {
        const val SMARTSPACE_VIEW = "com.google.android.systemui.smartspace.BcSmartspaceView"
        const val UPDATED = "onSmartspaceTargetsUpdated"
        const val COMPONENT = "my.github.MrxSiN.pixellauncherevolved.glance"
        /** Prefixes every id this module adds, so none can equal a system target's. */
        const val ID_PREFIX = "ple_glance_"
    }
}
