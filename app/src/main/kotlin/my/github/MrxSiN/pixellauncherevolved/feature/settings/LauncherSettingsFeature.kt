package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.backup.DocumentRequests
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/**
 * Puts this module's settings in the launcher's own Home settings.
 *
 * They belong where a person already goes to change how home behaves — long
 * press an empty part of the home screen, then Home settings — rather than in a
 * separate app they have to remember exists. The section is appended, so it
 * lands at the bottom of the screen, under the launcher's own landscape switch.
 *
 * Nothing gates this feature: it is the settings screen, so it is installed
 * whatever the settings say.
 */
class LauncherSettingsFeature : LauncherFeature {

    override val id: String = "launcher_settings"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val fragment = context.findClass(SETTINGS_FRAGMENT)
        if (fragment == null) {
            context.logger.warn("Home settings is not available in this launcher")
            return
        }

        val api = PreferenceApi(context.classLoader)
        if (!api.isUsable) {
            context.logger.warn("Home settings does not expose the preference classes this build needs")
            return
        }

        val resources = context.moduleResources
        if (resources == null) {
            context.logger.warn("Module resources are unreachable; the settings section is not added")
            return
        }

        val screenOf = Reflect.method(fragment, "getPreferenceScreen")
        val createPreferences = Reflect.method(
            fragment,
            "onCreatePreferences",
            Bundle::class.java,
            String::class.java,
        )
        if (screenOf == null || createPreferences == null) {
            context.logger.warn("Home settings does not expose its preference screen")
            return
        }

        val environment = SettingsEnvironment(
            logger = context.logger,
            modulePackage = context.xposed.moduleApplicationInfo.packageName,
            analysis = { context.analysis },
            xposedApiVersion = context.xposed.apiVersion,
            frameworkName = context.xposed.frameworkName,
            frameworkVersion = context.xposed.frameworkVersion,
        ) {
            context.logger.info("Restart requested from Home settings; ending the launcher process")
            Process.killProcess(Process.myPid())
        }
        val section = SettingsSection(api, resources, context.settings, environment)

        installExpressiveSlider(context, api)
        DocumentRequests.install(context)
        installExpressiveRows(context, api, section)

        context.xposed.hook(createPreferences).intercept { chain ->
            val rootKey = chain.args.getOrNull(ROOT_KEY_ARGUMENT) as? String
            val page = SettingsPages.byRootKey(rootKey)

            if (page != null) {
                // These roots exist only at runtime, so the launcher's XML
                // resolver cannot create them. Own the call before it tries.
                runCatching { section.showPage(requireNotNull(chain.thisObject), page) }
                    .onFailure { context.logger.warn("Unable to draw ${page.key} settings", it) }
                null
            } else {
                chain.proceed().also {
                    if (rootKey == null) {
                        runCatching { screenOf.invoke(chain.thisObject)?.let(section::appendTo) }
                            .onFailure { error ->
                                context.logger.warn("Unable to add module Home settings", error)
                            }
                    }
                }
            }
        }

        context.logger.info("Home settings: module section added under the launcher's own settings")
    }

    /**
     * Draws this module's slider rows the way Material 3 Expressive draws one.
     *
     * The bar only exists once a row is bound, so the restyling happens there.
     * Only this module's own rows are touched, recognised by the key prefix
     * every row here is built with: a slider the launcher adds of its own is
     * left as the launcher drew it.
     */
    private fun installExpressiveSlider(context: FeatureContext, api: PreferenceApi) {
        val bind = api.sliderBind
        if (!api.hasSlider || bind == null) return

        context.xposed.hook(bind).intercept { chain ->
            chain.proceed().also {
                val row = chain.thisObject
                if (row != null && api.keyOf(row)?.startsWith(SettingsKeys.PREFIX) == true) {
                    runCatching { api.sliderViewOf(row)?.let(ExpressiveSlider::applyTo) }
                        .onFailure { context.logger.warn("Unable to draw the slider", it) }
                }
            }
        }
    }

    /**
     * Draws this module's own pages as the cards Android 17 settings is made
     * of.
     *
     * Only the rows of a page this module builds are drawn this way. The
     * launcher's own rows keep the look the launcher gives them, and so does
     * this module's section in Home settings, which is a few rows among the
     * launcher's on a screen the launcher lays out.
     *
     * A row only has a view once it is bound, and the bind is the one place
     * that knows which view a recycled row ended up with, so the drawing
     * happens there. Every preference in the screen passes through this one
     * method — a subclass that overrides it calls up to this one first — which
     * is also what lets a view handed on from one of this module's rows to one
     * of the launcher's be given back the way the launcher drew it.
     */
    private fun installExpressiveRows(context: FeatureContext, api: PreferenceApi, section: SettingsSection) {
        val bind = api.rowBind
        if (!api.hasRowPlacement || bind == null) {
            context.logger.warn("Home settings does not expose its rows; they keep the launcher's own look")
            return
        }

        val placements = ExpressiveRowPlacements(api, SettingsKeys.PAGE_PREFIX)

        context.xposed.hook(bind).intercept { chain ->
            chain.proceed().also {
                val row = chain.thisObject
                val holder = chain.args.getOrNull(HOLDER_ARGUMENT)
                if (row != null && holder != null) {
                    runCatching {
                        api.rowViewOf(holder)?.let { view ->
                            ExpressiveRowView.applyTo(view, placements.placementOf(row))
                            RowAccessoryView.applyTo(view, section.accessoryOf(row))
                        }
                    }.onFailure { error -> context.logger.warn("Unable to draw the settings row", error) }
                }
            }
        }
    }

    private companion object {
        const val SETTINGS_FRAGMENT =
            "com.android.launcher3.settings.SettingsActivity\$LauncherSettingsFragment"

        /** `onCreatePreferences(Bundle savedInstanceState, String rootKey)`. */
        const val ROOT_KEY_ARGUMENT = 1

        /** `onBindViewHolder(PreferenceViewHolder holder)`. */
        const val HOLDER_ARGUMENT = 0
    }
}

/**
 * The section itself: one row per page, each opening its own page.
 *
 * What the pages hold is [SettingsPages]; this only builds the page the launcher
 * asks for, and tells the bind which accessory each row ends with.
 */
private class SettingsSection(
    private val api: PreferenceApi,
    private val resources: Resources,
    private val settings: SettingsStore,
    private val environment: SettingsEnvironment,
) {

    private val accessories = RowAccessories()

    fun appendTo(screen: Any) {
        val context = api.contextOf(screen)
        val section = api.createCategory(context, resources.getString(R.string.app_name))

        // Added first: children are only attached to the hierarchy through a
        // parent that already has one.
        api.add(screen, section)

        val scope = scope(context)
        for (page in SettingsPages.topLevel) PageLinkRow(page).addTo(section, scope)
    }

    /**
     * Opens one of this module's pages, named after itself.
     *
     * The launcher names an open page by setting the activity's title from the
     * screen it built. This page is built here rather than there, so the same
     * has to be said here: without it the page opens under the title of the
     * screen it was opened from.
     */
    fun showPage(fragment: Any, page: SettingsPage) {
        val title = resources.getString(page.titleRes)
        val context = api.showRootScreen(fragment, SettingsKeys.page(page), title) { root -> populate(root, page) }

        context.activityOrNull()?.title = title
    }

    fun accessoryOf(row: Any): RowAccessory? = accessories.of(api.keyOf(row))

    private fun populate(root: Any, page: SettingsPage) {
        val scope = scope(api.contextOf(root))

        for (group in page.groups) {
            val target = group.titleRes?.let { title ->
                api.createCategory(scope.context, resources.getString(title)).also { api.add(root, it) }
            } ?: root
            group.rows.forEach { it.addTo(target, scope) }
        }
    }

    private fun scope(context: Context) = RowScope(api, resources, settings, environment, context, accessories)
}
