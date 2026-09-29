package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.ComponentName
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackState
import my.github.MrxSiN.pixellauncherevolved.icons.IconSource
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Draws app icons from an installed icon pack.
 *
 * The launcher already has one place where an app's artwork is decided and one
 * place where a stored icon's freshness is decided, and this feature is those
 * two methods and nothing else.
 *
 * ```
 * IconProvider.getIcon(PackageItemInfo, ApplicationInfo, int)   the artwork
 * IconProvider.getStateForApp(ApplicationInfo)                  what it is stored against
 * ```
 *
 * Replacing the drawable at the first of those and leaving everything after it
 * alone is what makes a pack icon a Pixel icon: the launcher's own normalizer,
 * adaptive wrapper, shape mask, shadow, work badge, colour extraction and bitmap
 * sizing all run on it exactly as they run on a stock icon, and the home screen,
 * the app drawer, search results, predictions, folder previews and the taskbar
 * all read the one bitmap the launcher cached, so the same app never wears
 * different artwork in two places.
 *
 * Adding this feature's generation to the second of those is what makes a change
 * reach a running launcher. The launcher compares the identifier it stored an
 * icon against with the one it now computes and regenerates the icons that
 * disagree; a new pack changes every app's identifier, one hand-picked icon
 * changes one app's. Nothing in the launcher's icon database is deleted or
 * rewritten by this module, so removing the module leaves nothing to undo.
 *
 * With the source set to System, [IconPackState.source] is null and every hook
 * is the launcher's own call, a volatile read and a return.
 *
 * Neither hook runs on the UI thread or in a drawing path: the launcher resolves
 * an app's artwork on its own icon worker, inside the miss path of its icon
 * cache. Scrolling the app drawer with a pack active reaches no code here.
 */
class IconPackFeature : LauncherFeature {

    override val compatibility = CompatibilityFeature.ICON_PACK

    override val id: String = "home_icon_pack"

    /** Installed whatever the setting says: the hooks are what make it live. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val provider = context.findClass(ICON_PROVIDER)
        val itemState = context.findClass(PERSISTED_STATE)
        val getIcon = provider?.let {
            Reflect.method(
                it,
                "getIcon",
                PackageItemInfo::class.java,
                ApplicationInfo::class.java,
                Int::class.javaPrimitiveType!!,
            )
        }
        val stateForApp = provider?.let { Reflect.method(it, "getStateForApp", ApplicationInfo::class.java) }
        val withValues = itemState?.let {
            Reflect.method(it, "withAdditionalValues", Array<String>::class.java)
        }

        if (getIcon == null || stateForApp == null || withValues == null) {
            context.logger.warn("Icons: this launcher build does not resolve app icons where this feature replaces them")
            return
        }

        val controller = IconPackController.of(context)
        val previews = IconPreviews(context, controller).also { it.install() }
        val providerContext = provider?.let { Reflect.field(it, CONTEXT) }

        /** The source [iconProvider] draws from: a preview's own, else the published one. */
        fun sourceOf(iconProvider: Any?): IconSource? {
            if (!previews.any) return IconPackState.source
            val choice = previews.choiceOf(iconProvider?.let { runCatching { providerContext?.get(it) }.getOrNull() })
            return if (choice != null) choice.source else IconPackState.source
        }

        context.xposed.hook(getIcon).intercept { chain ->
            val active = sourceOf(chain.thisObject) ?: return@intercept chain.proceed()
            // The pack first: an app it draws never has its own icon loaded only to be replaced.
            val pack = try {
                val info = chain.getArg(INFO_ARGUMENT) as? PackageItemInfo
                val application = chain.getArg(APPLICATION_ARGUMENT) as? ApplicationInfo
                if (info == null || application == null) {
                    null
                } else {
                    IconPackState.packIcon(active, info, application.uid, chain.getArg(DENSITY_ARGUMENT) as Int)
                }
            } catch (error: Throwable) {
                context.logger.warn("Icons: an app's icon could not be replaced", error)
                null
            }
            pack ?: chain.proceed()
        }

        context.xposed.hook(stateForApp).intercept { chain ->
            val stored = chain.proceed()
            val active = sourceOf(chain.thisObject) ?: return@intercept stored
            try {
                val application = chain.getArg(0) as? ApplicationInfo
                val token = application?.let { IconPackState.freshnessFor(active, it.packageName) }
                if (token == null || stored == null) {
                    stored
                } else {
                    withValues.invoke(stored, arrayOf(token))
                }
            } catch (error: Throwable) {
                context.logger.warn("Icons: an app's icon freshness could not be extended", error)
                stored
            }
        }

        installThemedPass(context, previews)
        val reveal = IconChangeReveal(context).also { it.install() }
        IconPackBridge(
            context.appContext,
            controller,
            LauncherIconRenderer.of(context.appContext, context.classLoader, context.logger),
            context,
            IconChangeNotice(context.appContext, context.moduleResources, context.logger),
            reveal,
            previews,
        ).install()

        controller.start()
        context.logger.info("Icons: app icons can be drawn from an installed icon pack")
    }

    /**
     * Keeps the launcher's themed-icon pass off pack artwork.
     *
     * With themed icons on, the launcher derives a monochrome layer for every
     * icon, including one it cannot theme well, and would draw a pack's artwork
     * as a tinted silhouette. A pack's artwork is its author's choice, so an app
     * the pack draws is given no themed layer and keeps its colours; an app the
     * pack does not map is themed exactly as before. Optional: without it a pack
     * still applies, and is themed like everything else.
     */
    private fun installThemedPass(context: FeatureContext, previews: IconPreviews) {
        val controller = context.findClass(MONO_THEME) ?: return
        val hint = context.findClass(SOURCE_HINT) ?: return
        val componentKey = context.findClass(COMPONENT_KEY) ?: return
        val themed = Reflect.method(
            controller,
            "createThemedBitmap",
            AdaptiveIconDrawable::class.java,
            context.findClass(BITMAP_INFO) ?: return,
            context.findClass(ICON_FACTORY) ?: return,
            hint,
        ) ?: return
        val key = Reflect.field(hint, "key") ?: return
        val component = Reflect.field(componentKey, "componentName") ?: return
        val user = Reflect.field(componentKey, "user") ?: return
        val factoryContext = context.findClass(ICON_FACTORY)?.let { Reflect.field(it, "context") }

        context.xposed.hook(themed).intercept { chain ->
            val choice = if (previews.any) {
                chain.getArg(FACTORY_ARGUMENT)?.let { factory -> previews.choiceOf(runCatching { factoryContext?.get(factory) }.getOrNull()) }
            } else {
                null
            }
            val active = (if (choice != null) choice.source else IconPackState.source) ?: return@intercept chain.proceed()
            val replaced = try {
                val identity = chain.getArg(HINT_ARGUMENT)?.let(key::get)
                val name = identity?.let(component::get) as? ComponentName
                val profile = identity?.let(user::get) as? UserHandle
                name != null && profile != null &&
                    // UserHandle.hashCode() is its user id; getIdentifier() is hidden API.
                    IconPackState.isReplaced(active, name.packageName, name.className, profile.hashCode())
            } catch (error: Throwable) {
                context.logger.warn("Icons: a pack icon could not be kept out of theming", error)
                false
            }
            if (replaced) null else chain.proceed()
        }
    }

    private companion object {
        const val ICON_PROVIDER = "com.android.launcher3.icons.IconProvider"
        const val PERSISTED_STATE = "com.android.launcher3.icons.PersistedItemState"
        const val MONO_THEME = "com.android.launcher3.icons.mono.MonoIconThemeController"
        const val SOURCE_HINT = "com.android.launcher3.icons.SourceHint"
        const val COMPONENT_KEY = "com.android.launcher3.util.ComponentKey"
        const val BITMAP_INFO = "com.android.launcher3.icons.BitmapInfo"
        const val ICON_FACTORY = "com.android.launcher3.icons.BaseIconFactory"

        /** `createThemedBitmap(AdaptiveIconDrawable, BitmapInfo, BaseIconFactory, SourceHint)`. */
        const val FACTORY_ARGUMENT = 2
        const val HINT_ARGUMENT = 3

        /** `IconProvider.mContext`: a preview's own context for a preview's provider. */
        const val CONTEXT = "mContext"

        /** `getIcon(PackageItemInfo info, ApplicationInfo appInfo, int density)`. */
        const val INFO_ARGUMENT = 0
        const val APPLICATION_ARGUMENT = 1
        const val DENSITY_ARGUMENT = 2
    }
}
