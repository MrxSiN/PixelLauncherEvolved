package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.app.smartspace.SmartspaceTarget
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    /** Each running source's "When to show" value ([GlanceWhen]); absent is always. */
    @Volatile
    private var whenOf: Map<String, String> = emptyMap()

    /** Each running source's Hide after minutes ([GlanceOptions]); absent is never. */
    @Volatile
    private var hideOf: Map<String, Int> = emptyMap()

    private val hideAfter = GlanceHideAfter()

    /** Main thread: when the one pending Hide after callback fires (elapsed realtime), or 0. */
    private var expiryAt = 0L

    private val expire = Runnable {
        expiryAt = 0L
        refresh()
    }

    private lateinit var conditions: GlanceConditions

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
        conditions = GlanceConditions(context.appContext, main, ::refresh)

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
        if (key == null) return
        if (key.startsWith(GlanceSources.KEY_PREFIX) || key.startsWith(GlanceWhen.KEY_PREFIX) || key == GlancePrefs.ORDER) {
            main.post { sync() }
        } else if (key.startsWith(GlanceOptions.KEY_PREFIX)) {
            main.post {
                sync()
                // Key ids may hold underscores, so every running source is told; each re-reads only its own.
                for (source in running.values) {
                    runCatching { source.onOptionsChanged() }.onFailure { host.logger.warn("At a Glance source ${source.id} failed on an option", it) }
                }
            }
        }
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
        active = GlancePrefs.ordered(running.values.toList(), GlancePrefs(preferences).order()) { it.id }
        whenOf = running.values.mapNotNull { source -> preferences.getString(GlanceWhen.key(source), null)?.let { source.id to it } }.toMap()
        conditions.update(whenOf.values)
        hideOf = running.values.mapNotNull { source ->
            GlanceOptions.hideMinutes(preferences.getString(GlanceOptions.key(source.id, GlanceOptions.HIDE_AFTER), null))
                .takeIf { it > 0 }?.let { source.id to it }
        }.toMap()
        hideAfter.retain(hideOf.keys)
        refresh()
    }

    /** Main thread: one callback for the soonest [at] (elapsed realtime) unless an earlier one is already pending. */
    private fun armExpiry(at: Long) {
        val now = SystemClock.elapsedRealtime()
        if (expiryAt > now && expiryAt <= at) return
        main.removeCallbacks(expire)
        expiryAt = at
        main.postDelayed(expire, (at - now).coerceAtLeast(0L))
    }

    private fun merge(given: List<*>): List<*> {
        val cards = ArrayList<SmartspaceTarget>()
        val extras = ArrayList<Pair<String, GlanceItem>>()
        val whens = whenOf
        val hides = hideOf
        val now = SystemClock.elapsedRealtime()
        var soonest = GlanceHideAfter.NEVER
        for (source in active) {
            if (!conditions.holds(whens[source.id])) continue
            // One source failing, in its own code or in the framework's builders, leaves out only its content.
            runCatching {
                val item = source.content()
                if (item == null) {
                    if (source.id in hides) hideAfter.forget(source.id)
                    return@runCatching
                }
                val minutes = hides[source.id] ?: 0
                if (minutes > 0) {
                    val expires = hideAfter.check(source.id, GlanceOptions.fingerprint(item), now, minutes)
                    if (expires <= now) return@runCatching
                    if (expires < soonest) soonest = expires
                }
                val id = ID_PREFIX + source.id
                if (source.kind == GlanceSource.Kind.CARD) cards += targets.card(id, item) else extras += id to item
            }.onFailure { host.logger.warn("At a Glance source ${source.id} failed", it) }
        }
        if (soonest != GlanceHideAfter.NEVER) main.post { armExpiry(soonest) }
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

        override fun option(source: String, name: String): String? = preferences.getString(GlanceOptions.key(source, name), null)
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
