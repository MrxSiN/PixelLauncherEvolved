package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.icons.IconOverrideStore
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackIndex
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackIndexer
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackState
import my.github.MrxSiN.pixellauncherevolved.icons.IconPacks
import my.github.MrxSiN.pixellauncherevolved.icons.IconSource
import my.github.MrxSiN.pixellauncherevolved.icons.IconSourceFactory
import my.github.MrxSiN.pixellauncherevolved.icons.IconSourceSettings
import my.github.MrxSiN.pixellauncherevolved.icons.SharedPreferencesIconOverrideStore
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Keeps the published icon source in step with the device.
 *
 * One of these exists per launcher process, because the Wallpaper & style
 * bridge ([IconPackBridge]) needs the same one: a choice made there has to
 * publish a new source and reload the launcher, and both happen in this
 * process.
 *
 * It watches two things. A package change, because the chosen pack may have been
 * updated — a new version means a new component map — or uninstalled, which falls
 * the source back to System rather than leaving a selection that resolves to
 * nothing. And a date change, because a pack's calendar icons are numbered by the
 * day of the month. Changes made in Wallpaper & style come in by a direct call.
 *
 * Every pack is indexed ahead of being chosen: when it is installed or updated,
 * and when the picker lists the packs. Compiling a large pack takes about a
 * second, and choosing one should cost only the launcher's own redraw.
 */
internal class IconPackController private constructor(
    private val context: Context,
    val settings: IconSourceSettings,
    val overrides: IconOverrideStore,
    private val indexer: IconPackIndexer,
    private val factory: IconSourceFactory,
    private val reloader: IconReloader,
    private val logger: Logger,
) {

    /**
     * One thread for everything this feature does off the UI thread: compiling
     * a pack's map, reading packages for settings, reacting to broadcasts.
     *
     * Its own rather than the launcher's model thread, because compiling a large
     * pack takes seconds and the model thread is what installs, updates and
     * loads the home screen. One thread, so a change and the reload it asks for
     * are applied in the order they were made.
     */
    private val worker: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-icons").apply { isDaemon = true }
    }

    /**
     * Publishes the stored source and starts watching.
     *
     * A map already on disk is read here, a few milliseconds for the largest
     * packs, so the launcher's first icons are already the pack's. A pack whose
     * map is not compiled yet (the first start after it updated) stays on stock
     * icons until the worker has compiled it, then the launcher is reloaded.
     */
    fun start() {
        watch()
        val pack = factory.wantedPack() ?: return

        val started = SystemClock.elapsedRealtime()
        val cached = indexer.cachedIndexOf(pack)
        if (cached != null) {
            factory.publish(cached)
            logger.info("Icons: $pack published from its index in ${SystemClock.elapsedRealtime() - started} ms")
            return
        }
        worker.execute {
            runCatching {
                factory.publish()
                reloader.reload("pack indexed", SystemClock.elapsedRealtime(), null)
            }.onFailure { logger.warn("Icons: the chosen pack could not be indexed", it) }
        }
    }

    /**
     * Makes [pack] the icon source, or the system when it is null, and applies it.
     *
     * The pack chosen before is kept when switching to System, as it always was,
     * so choosing Icon pack again comes back to it.
     */
    fun select(pack: String?): Future<Unit> {
        if (pack == null) {
            settings.usePack(false)
        } else {
            settings.choose(pack)
            settings.usePack(true)
        }
        return apply(if (pack == null) "source: system" else "pack chosen")
    }

    /** Compiles, on the worker, the index of every pack in [packs] that has none on disk yet. */
    fun prewarm(packs: Collection<String>) = worker.execute {
        for (pack in packs) {
            runCatching { if (indexer.cachedIndexOf(pack) == null) indexer.indexOf(pack) }
                .onFailure { logger.warn("Icons: $pack could not be indexed ahead of time", it) }
        }
    }

    /**
     * Applies a change made in settings to the running launcher.
     *
     * The generation is raised first, so the launcher's freshness check sees a
     * different answer for every app whose icon this could change. The source
     * is published, map included, before the reload asks for the first icon.
     */
    fun apply(reason: String): Future<Unit> {
        val asked = SystemClock.elapsedRealtime()
        val done = CompletableFuture<Unit>()
        settings.bump()
        worker.execute { applyNow(reason, asked, done) }
        return done
    }

    /** [apply] for a caller already on the worker, so the next broadcast sees the result. */
    private fun applyNow(
        reason: String,
        asked: Long = SystemClock.elapsedRealtime(),
        done: CompletableFuture<Unit>? = null,
    ) {
        runCatching {
            factory.publish()
            logger.info("Icons: $reason published ${SystemClock.elapsedRealtime() - asked} ms after it was asked for")
            reloader.reload(reason, asked, done)
        }.onFailure {
            logger.warn("Icons: $reason was not applied", it)
            done?.complete(Unit)
        }
    }

    /** The component map already on disk for [packageName], without compiling one. */
    fun cachedIndexOf(packageName: String): IconPackIndex? = indexer.cachedIndexOf(packageName)

    /** [pack]'s icons for a home screen preview, without choosing it. */
    fun preview(pack: String): IconSource? = factory.preview(pack)

    /**
     * Runs [work] on this feature's worker, for the settings screens: asking
     * the package manager for every launchable app, reading a pack's drawables.
     */
    fun onWorker(work: () -> Unit) = worker.execute {
        runCatching(work).onFailure { logger.warn("Icons: settings work failed", it) }
    }

    private fun watch() {
        register(
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        ) { intent ->
            // An update is REMOVED then ADDED then REPLACED; only the last two matter.
            val replacing = intent?.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (!replacing) onPackageChanged(intent?.data?.schemeSpecificPart)
        }

        register(
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
        ) { onDateChanged() }
    }

    private fun register(filter: IntentFilter, onReceive: (Intent?) -> Unit) {
        runCatching {
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(received: Context?, intent: Intent?) {
                        // Package manager work is Binder work: off the UI thread.
                        worker.execute {
                            runCatching { onReceive(intent) }
                                .onFailure { logger.warn("Icons: a package or date change was not handled", it) }
                        }
                    }
                },
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { logger.warn("Icons: package and date changes cannot be watched", it) }
    }

    private fun onPackageChanged(packageName: String?) {
        val chosen = settings.pack()
        if (packageName != null && packageName != chosen) {
            // Any other pack is indexed now, so choosing it later is immediate.
            if (IconPacks.isPack(context, packageName)) prewarm(listOf(packageName))
            return
        }
        chosen ?: return

        if (!settings.usesPack()) return

        val current = IconPackState.source
        if (!IconPacks.isUsable(context, chosen)) {
            indexer.forget(chosen)
            if (current == null) return

            // The selection itself is kept, so reinstalling the pack brings it
            // back; settings says in plain words why icons are stock meanwhile.
            logger.warn("Icons: $chosen is no longer a usable icon pack; using system icons")
            settings.bump()
            applyNow("pack removed")
            return
        }

        if (current != null && current.packVersion == IconPacks.versionOf(context, chosen)) return

        logger.info("Icons: $chosen changed; its icons are drawn again")
        settings.bump()
        applyNow("pack updated")
    }

    private fun onDateChanged() {
        val day = factory.today()
        if (IconPackState.dayIndex == day) return

        IconPackState.dayIndex = day
        val index = IconPackState.source?.index ?: return
        if (index.calendarCount() == 0) return

        // The date is part of the freshness of a calendar app's icon, so the
        // launcher's own comparison picks out exactly those.
        reloader.reload("calendar rollover", SystemClock.elapsedRealtime(), null)
    }

    companion object {

        @Volatile
        private var installed: IconPackController? = null

        /** The one controller for this launcher process, built on first use. */
        fun of(context: FeatureContext): IconPackController {
            installed?.let { return it }

            val preferences = LauncherSettings.preferences(context.appContext)
            val settings = IconSourceSettings(preferences)
            val overrides = SharedPreferencesIconOverrideStore(preferences)
            val indexer = IconPackIndexer(context.appContext, context.logger)
            val built = IconPackController(
                context = context.appContext,
                settings = settings,
                overrides = overrides,
                indexer = indexer,
                factory = IconSourceFactory(context.appContext, settings, overrides, indexer, context.logger),
                reloader = IconReloader(context.appContext, context.logger),
                logger = context.logger,
            )
            installed = built
            return built
        }

        /** The controller, for the Wallpaper & style bridge; null before the feature installed. */
        fun current(): IconPackController? = installed
    }
}
