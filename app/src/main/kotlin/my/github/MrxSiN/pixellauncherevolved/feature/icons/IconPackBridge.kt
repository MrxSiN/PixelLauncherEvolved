package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process

import java.util.concurrent.TimeUnit

import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.icons.IconOverride
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackDrawables
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackState
import my.github.MrxSiN.pixellauncherevolved.icons.IconPacks
import my.github.MrxSiN.pixellauncherevolved.icons.LaunchableApp
import my.github.MrxSiN.pixellauncherevolved.icons.LaunchableApps
import my.github.MrxSiN.pixellauncherevolved.wallpaper.HomeBlurRadius

/**
 * Answers Wallpaper & style's icon pack questions ([IconsBridge]).
 *
 * ```
 * ContentProviderProxy.call(String, String, Bundle)   for LauncherCustomizationProvider (grid_control)
 * ```
 *
 * Only methods starting [IconsBridge.PREFIX] are answered here; every other
 * call is the launcher's own. A caller that is not Wallpaper & style, the
 * launcher or the shell is refused, because these change what the home screen
 * shows.
 *
 * Everything runs on the Binder thread the picker called on: package manager
 * reads, drawable loads and rendering, never the launcher's UI thread. An apply
 * waits for the launcher's reload so the picker can redraw its preview after it.
 */
internal class IconPackBridge(
    private val context: Context,
    private val controller: IconPackController,
    private val renderer: LauncherIconRenderer?,
    private val feature: FeatureContext,
    private val notice: IconChangeNotice,
    private val reveal: IconChangeReveal,
    private val previews: IconPreviews,
) {

    private val density = context.resources.displayMetrics.densityDpi
    private val tile = (TILE_DP * context.resources.displayMetrics.density).toInt()

    /** The chosen pack's drawable list, read once per pack while the picker browses it. */
    @Volatile
    private var drawables: IconPackDrawables? = null

    /** The launchable apps, as last listed for the picker. */
    @Volatile
    private var apps: List<LaunchableApp> = emptyList()

    fun install() {
        val proxy = feature.findClass(PROXY) ?: return warn("the launcher's customization provider is gone")
        val call = Reflect.method(proxy, CALL, String::class.java, String::class.java, Bundle::class.java)
            ?: return warn("the launcher's customization provider takes no calls")
        val provider = feature.findClass(PROVIDER)

        feature.xposed.hook(call).intercept { chain ->
            val method = chain.getArg(0) as? String
            if (method == GET_PREVIEW_BITMAP && (chain.getArg(2) as? Bundle)?.containsKey(IconsBridge.PREVIEW_PACK) == true) {
                return@intercept previews.render { chain.proceed() }
            }
            if (method == null || !method.startsWith(IconsBridge.PREFIX)) return@intercept chain.proceed()
            if (provider != null && !provider.isInstance(chain.thisObject)) return@intercept chain.proceed()
            if (!callerAllowed()) throw SecurityException("$method is only for Wallpaper & style")

            try {
                answer(method, chain.getArg(1) as? String, chain.getArg(2) as? Bundle)
            } catch (error: Throwable) {
                feature.logger.warn("Icons: Wallpaper & style's $method failed", error)
                Bundle()
            }
        }
        feature.logger.info("Icons: Wallpaper & style can choose icon packs")
    }

    private fun warn(what: String) = feature.logger.warn("Icons: $what; Wallpaper & style cannot choose icon packs")

    private fun callerAllowed(): Boolean = IconsBridge.callerAllowed(context)

    private fun answer(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        IconsBridge.STATE -> state()
        IconsBridge.APPLY -> apply(arg)
        IconsBridge.APPS -> apps()
        IconsBridge.APP_ICONS -> appIcons(extras?.getStringArray(IconsBridge.KEYS)?.filterNotNull().orEmpty())
        IconsBridge.DRAWABLES -> drawables(arg.orEmpty(), extras?.getInt(IconsBridge.OFFSET) ?: 0)
        IconsBridge.OVERRIDE -> override(extras?.getString(IconsBridge.KEY), extras?.getString(IconsBridge.CHOICE))
        IconsBridge.RESET -> reset()
        else -> Bundle()
    }

    private fun state(): Bundle {
        val packs = IconPacks.installed(context)
        controller.prewarm(packs.map { it.packageName })
        val chosen = controller.settings.pack()

        return Bundle().apply {
            putBoolean(IconsBridge.AVAILABLE, true)
            putBoolean(IconsBridge.USES_PACK, controller.settings.usesPack() && chosen != null)
            putString(IconsBridge.PACK, chosen)
            putString(IconsBridge.PACK_LABEL, chosen?.let { IconPacks.labelOf(context.packageManager, it)?.toString() })
            putStringArray(IconsBridge.PACKAGES, packs.map { it.packageName }.toTypedArray())
            putStringArray(IconsBridge.LABELS, packs.map { it.label.toString() }.toTypedArray())
            putInt(IconsBridge.OVERRIDES, controller.overrides.overrides().size)
            putInt(
                IconsBridge.BLUR_RADIUS,
                HomeBlurRadius.of(context, feature.settings[Settings.HOME_BLUR_WALLPAPER], feature.settings[Settings.HOME_BLUR_STRENGTH]),
            )
            // Pictures stop well short of a Binder transaction; a pack past that shows its name alone.
            var bytes = 0
            for (pack in packs) {
                if (bytes > TILE_BUDGET_BYTES && pack.packageName != chosen) continue
                val icon = runCatching { context.packageManager.getApplicationIcon(pack.packageName) }.getOrNull() ?: continue
                render(icon)?.let {
                    bytes += it.size
                    putByteArray(IconsBridge.TILE_PREFIX + pack.packageName, it)
                }
            }
        }
    }

    private fun apply(pack: String?): Bundle {
        val wanted = pack?.takeIf { it.isNotEmpty() }
        if (wanted != null && !IconPacks.isUsable(context, wanted)) return Bundle()
        drawables = null
        val label = wanted?.let { IconPacks.labelOf(context.packageManager, it)?.toString() ?: it }
        notice.changing(label)
        // "Changed" once the new pictures have reached the home screen, not when the reload returns.
        waitFor({ notice.changed(label) }) { controller.select(wanted) }
        return Bundle().apply { putBoolean(IconsBridge.OK, true) }
    }

    private fun apps(): Bundle {
        val listed = LaunchableApps.of(context)
        apps = listed
        val chosen = controller.overrides.overrides().associateBy { IconsBridge.keyOf(it.packageName, it.className, it.userId) }
        val pack = controller.settings.pack()
        return Bundle().apply {
            putStringArray(IconsBridge.KEYS, listed.map { IconsBridge.keyOf(it.packageName, it.className, it.userId) }.toTypedArray())
            putStringArray(IconsBridge.LABELS, listed.map { it.label }.toTypedArray())
            putStringArray(
                IconsBridge.CHOICES,
                listed.map { app ->
                    val override = chosen[IconsBridge.keyOf(app.packageName, app.className, app.userId)]
                    when {
                        override == null -> IconsBridge.CHOICE_PACK
                        override.drawable == null -> IconsBridge.CHOICE_SYSTEM
                        override.pack != pack -> IconsBridge.CHOICE_PACK
                        else -> override.drawable
                    }
                }.toTypedArray(),
            )
        }
    }

    /** Each app as the home screen draws it now: the published source's answer, else the stock icon. */
    private fun appIcons(keys: List<String>): Bundle {
        val byKey = (apps.takeIf { it.isNotEmpty() } ?: LaunchableApps.of(context).also { apps = it })
            .associateBy { IconsBridge.keyOf(it.packageName, it.className, it.userId) }
        return Bundle().apply {
            for (key in keys.take(IconsBridge.PAGE)) {
                val app = byKey[key] ?: continue
                val stock = runCatching { app.info.getIcon(density) }.getOrNull() ?: continue
                val icon = IconPackState.iconFor(app.info.activityInfo, app.info.applicationInfo.uid, density, stock) ?: stock
                render(icon, app)?.let { putByteArray(IconsBridge.ICON_PREFIX + key, it) }
            }
        }
    }

    private fun drawables(query: String, offset: Int): Bundle {
        val pack = controller.settings.pack() ?: return Bundle()
        val list = drawables?.takeIf { it.packageName == pack }
            ?: IconPackDrawables.of(context, pack)?.also { drawables = it }
            ?: return Bundle()
        val page = list.search(query, offset + IconsBridge.PAGE).drop(offset)
        return Bundle().apply {
            putStringArray(IconsBridge.NAMES, page.toTypedArray())
            for (name in page) {
                list.drawableFor(name, density)?.let { render(IconPackState.adaptive(it)) }
                    ?.let { putByteArray(IconsBridge.ICON_PREFIX + name, it) }
            }
        }
    }

    private fun override(key: String?, choice: String?): Bundle {
        val (userId, component) = key?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return Bundle()
        val (packageName, className) = component.split('/', limit = 2).takeIf { it.size == 2 } ?: return Bundle()
        val user = userId.toIntOrNull() ?: return Bundle()

        val keep = controller.overrides.overrides().filterNot {
            it.packageName == packageName && it.className == className && it.userId == user
        }
        val chosen = when (choice) {
            null, IconsBridge.CHOICE_PACK -> null
            IconsBridge.CHOICE_SYSTEM -> IconOverride(packageName, className, user, null)
            else -> IconOverride(packageName, className, user, choice, controller.settings.pack())
        }
        controller.overrides.replace(keep + listOfNotNull(chosen))
        waitFor { controller.apply("per-app icon") }
        return Bundle().apply { putBoolean(IconsBridge.OK, true) }
    }

    private fun reset(): Bundle {
        controller.overrides.clear()
        waitFor { controller.apply("overrides reset") }
        return Bundle().apply { putBoolean(IconsBridge.OK, true) }
    }

    /** Applies a change and waits for the launcher to reload, readying the home screen's reveal of it. */
    private fun waitFor(whenLanded: () -> Unit = {}, change: () -> java.util.concurrent.Future<Unit>) {
        reveal.expect(whenLanded)
        runCatching { change().get(APPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        // Reloaded: what the home screen redraws now follows within moments, or not at all.
        reveal.reloaded()
    }

    private fun render(icon: Drawable, app: LaunchableApp? = null): ByteArray? =
        renderer?.let {
            runCatching { it.png(icon, tile, app?.user?.takeIf { app.userId != MY_USER }) }
                .onFailure { error -> feature.logger.warn("Icons: an icon could not be drawn for Wallpaper & style", error) }
                .getOrNull()
        }

    private companion object {
        const val PROXY = "com.android.launcher3.util.ContentProviderProxy"
        const val PROVIDER = "com.android.launcher3.graphics.LauncherCustomizationProvider"
        const val CALL = "call"
        const val GET_PREVIEW_BITMAP = "get_preview_bitmap"

        /** Tiles and list icons, as large as the picker draws them. */
        const val TILE_DP = 64f
        const val APPLY_TIMEOUT_SECONDS = 5L
        const val TILE_BUDGET_BYTES = 512 * 1024
        val MY_USER = Process.myUserHandle().hashCode()
    }
}
