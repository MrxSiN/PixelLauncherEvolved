package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.res.Resources
import android.os.Bundle
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogPage
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
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

        val resources = moduleResources(context)
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

        val section = SettingsSection(api, resources, context.settings) {
            context.logger.info("Restart requested from Home settings; ending the launcher process")
            Process.killProcess(Process.myPid())
        }

        context.xposed.hook(createPreferences).intercept { chain ->
            val rootKey = chain.args.getOrNull(ROOT_KEY_ARGUMENT) as? String
            val page = CatalogPage.entries.firstOrNull { PAGE_KEY_PREFIX + it.key == rootKey }

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
     * This module's own resources, read from inside the launcher.
     *
     * Titles and summaries are described once, in this module's `strings.xml`,
     * and the rows are drawn in another app's process.
     * `getResourcesForApplication` opens them from the module's own APK, so the
     * two cannot drift and no text has to be duplicated as a constant.
     */
    private fun moduleResources(context: FeatureContext): Resources? = runCatching {
        context.appContext.packageManager.getResourcesForApplication(
            context.xposed.moduleApplicationInfo,
        )
    }.getOrNull()

    private companion object {
        const val SETTINGS_FRAGMENT =
            "com.android.launcher3.settings.SettingsActivity\$LauncherSettingsFragment"

        /** `onCreatePreferences(Bundle savedInstanceState, String rootKey)`. */
        const val ROOT_KEY_ARGUMENT = 1
        const val PAGE_KEY_PREFIX = "ple_page_"
    }
}

/**
 * The section itself: three focused pages, then the restart button.
 *
 * The tweaks come from [FeatureCatalog], so adding one to this module adds it
 * here too, and nothing in this class knows what any of them do.
 *
 * Keys are prefixed because they share a namespace with the launcher's own
 * preferences, which the launcher looks rows up by.
 */
private class SettingsSection(
    private val api: PreferenceApi,
    private val resources: Resources,
    private val settings: SettingsStore,
    private val onRestart: () -> Unit,
) {

    fun appendTo(screen: Any) {
        val context = api.contextOf(screen)
        val section = api.createCategory(context, resources.getString(R.string.app_name))

        // Added first: children are only attached to the hierarchy through a
        // parent that already has one.
        api.add(screen, section)

        for (page in CatalogPage.entries) {
            val pageScreen = api.createScreen(
                context = context,
                key = PAGE_KEY_PREFIX + page.key,
                title = resources.getString(page.titleRes),
            )
            api.add(section, pageScreen)
            populate(pageScreen, page)
        }

        api.add(
            section,
            api.createAction(
                context = context,
                key = RESTART_KEY,
                title = resources.getString(R.string.action_restart_launcher),
                summary = resources.getString(R.string.action_restart_launcher_summary),
                onClick = onRestart,
            ),
        )
    }

    fun showPage(fragment: Any, page: CatalogPage) {
        api.showRootScreen(fragment) { populate(it, page) }
    }

    private fun populate(screen: Any, page: CatalogPage) {
        val context = api.contextOf(screen)
        // Rows stay addressable so the two layout choices can update each other
        // on screen the moment one of them is switched on.
        val switches = mutableMapOf<BoolSetting, Any>()

        for (entry in FeatureCatalog.entriesIn(page)) {
            val setting = entry.setting
            if (setting !is BoolSetting) continue

            val row = api.createSwitch(
                context = context,
                key = KEY_PREFIX + setting.key,
                title = resources.getString(entry.titleRes),
                summary = resources.getString(entry.summaryRes),
                checked = settings[setting],
                onChange = { value -> apply(setting, value, switches) },
            )

            switches[setting] = row
            api.add(screen, row)
        }
    }

    /**
     * Writes one setting, and settles the pair that cannot both be on.
     *
     * Tablet mode and tablet taskbar only are two answers to the same question,
     * so switching one on switches the other off, on screen as well as in the
     * store. Without the second half a person would be looking at two switches
     * that both read on while only one of them is.
     */
    private fun apply(setting: BoolSetting, value: Boolean, switches: Map<BoolSetting, Any>) {
        settings.put(setting, value)
        if (value) {
            val opposite = when (setting) {
                Settings.TABLET_MODE -> Settings.TASKBAR_ONLY
                Settings.TASKBAR_ONLY -> Settings.TABLET_MODE
                else -> null
            }

            if (opposite != null) {
                settings.put(opposite, false)
                switches[opposite]?.let { api.setChecked(it, false) }
            }
        }
    }

    private companion object {
        const val KEY_PREFIX = "ple_"
        const val PAGE_KEY_PREFIX = KEY_PREFIX + "page_"
        const val RESTART_KEY = KEY_PREFIX + "restart_launcher"
    }
}
