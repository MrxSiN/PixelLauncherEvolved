package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogPage
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.focus.LauncherPagePreviewSource
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HideAppsSelection
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.SharedPreferencesWebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppDialog
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchApps
import my.github.MrxSiN.pixellauncherevolved.focus.ProviderFocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.SharedPreferencesFocusStore
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
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

        val section = SettingsSection(
            api = api,
            resources = resources,
            settings = context.settings,
            modulePackage = context.xposed.moduleApplicationInfo.packageName,
            logger = context.logger,
        ) {
            context.logger.info("Restart requested from Home settings; ending the launcher process")
            Process.killProcess(Process.myPid())
        }

        installExpressiveSlider(context, api)
        installExpressiveRows(context, api)

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
                if (row != null && api.keyOf(row)?.startsWith(KEY_PREFIX) == true) {
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
    private fun installExpressiveRows(context: FeatureContext, api: PreferenceApi) {
        val bind = api.rowBind
        if (!api.hasRowPlacement || bind == null) {
            context.logger.warn("Home settings does not expose its rows; they keep the launcher's own look")
            return
        }

        val placements = ExpressiveRowPlacements(api, PAGE_KEY_PREFIX)

        context.xposed.hook(bind).intercept { chain ->
            chain.proceed().also {
                val row = chain.thisObject
                val holder = chain.args.getOrNull(HOLDER_ARGUMENT)
                if (row != null && holder != null) {
                    runCatching {
                        api.rowViewOf(holder)?.let { view ->
                            ExpressiveRowView.applyTo(view, placements.placementOf(row))
                        }
                    }.onFailure { error -> context.logger.warn("Unable to draw the settings row", error) }
                }
            }
        }
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

        /** `onBindViewHolder(PreferenceViewHolder holder)`. */
        const val HOLDER_ARGUMENT = 0
        const val KEY_PREFIX = "ple_"
        const val PAGE_KEY_PREFIX = KEY_PREFIX + "page_"
    }
}

/**
 * The section itself: four focused pages, then the restart button.
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
    /** This module's own package, which its providers are addressed by. */
    private val modulePackage: String,
    private val logger: Logger,
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
                summary = resources.getString(page.summaryRes),
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

    /**
     * Opens one of this module's pages, named after itself.
     *
     * The launcher names an open page by setting the activity's title from the
     * screen it built. This page is built here rather than there, so the same
     * has to be said here: without it the page opens under the title of the
     * screen it was opened from.
     */
    fun showPage(fragment: Any, page: CatalogPage) {
        val title = resources.getString(page.titleRes)
        val context = api.showRootScreen(fragment, PAGE_KEY_PREFIX + page.key, title) { populate(it, page) }

        context.activityOrNull()?.title = title
    }

    private fun populate(screen: Any, page: CatalogPage) {
        val context = api.contextOf(screen)
        // Rows stay addressable so the two layout choices can update each other
        // on screen the moment one of them is switched on.
        val switches = mutableMapOf<BoolSetting, Any>()
        // A row that belongs to one switch, greyed out while that switch is off.
        val companions = mutableMapOf<BoolSetting, Any>()

        for (entry in FeatureCatalog.entriesIn(page)) {
            val setting = entry.setting
            if (setting !is BoolSetting) continue

            val row = api.createSwitch(
                context = context,
                key = KEY_PREFIX + setting.key,
                title = resources.getString(entry.titleRes),
                summary = resources.getString(entry.summaryRes),
                checked = settings[setting],
                onChange = { value -> apply(setting, value, switches, companions) },
            )

            switches[setting] = row
            api.add(screen, row)

            companionOf(setting, context)?.let { companion ->
                companions[setting] = companion
                api.add(screen, companion)
            }
        }

        when (page) {
            CatalogPage.HOME_SCREEN -> addFocusPages(screen, context)
            CatalogPage.APP_DRAWER -> {
                addHiddenApps(screen, context)
                addWebSearchApp(screen, context)
            }
            else -> Unit
        }
    }

    /**
     * The row that belongs directly under one switch, or none.
     *
     * A companion is not a tweak of its own — it says how the switch above it
     * behaves — so it is not in the catalogue, and it is greyed out rather than
     * hidden while that switch is off, to say what it would change.
     */
    private fun companionOf(setting: BoolSetting, context: Context): Any? = when (setting) {
        Settings.HOME_BLUR_WALLPAPER -> blurStrength(context)
        else -> null
    }

    /**
     * How strong the home screen blur is, from weakest to the launcher's own
     * deepest. The middle is what the tweak did before it could be changed.
     */
    private fun blurStrength(context: Context): Any? {
        if (!api.hasSlider) {
            logger.warn("Home settings has no slider row in this launcher; blur strength is not offered")
            return null
        }

        val strength = Settings.HOME_BLUR_STRENGTH
        lateinit var row: Any

        row = api.createSlider(
            context = context,
            key = KEY_PREFIX + strength.key,
            title = resources.getString(R.string.feature_home_blur_strength_title),
            summary = blurStrengthSummary(settings[strength]),
            value = settings[strength],
            isEnabled = settings[Settings.HOME_BLUR_WALLPAPER],
            onChange = { value ->
                settings.put(strength, value)
                api.setSummary(row, blurStrengthSummary(value))
            },
        )

        return row
    }

    private fun blurStrengthSummary(strength: Int): String =
        resources.getString(R.string.feature_home_blur_strength_summary, strength)

    /**
     * The row that says which pages each Mode shows.
     *
     * Not a switch, so it is not in the catalog: the catalog is one key per
     * on/off setting, and this is a set of pages per Mode. It sits under the
     * switch that turns the whole thing on, and does nothing while that is off.
     */
    private fun addFocusPages(screen: Any, context: Context) {
        if (!settings[Settings.FOCUS_HOME_SCREENS]) return

        val store = SharedPreferencesFocusStore(LauncherSettings.preferences(context))
        val source = ProviderFocusSource(context.contentResolver, modulePackage, logger)

        api.add(
            screen,
            api.createAction(
                context = context,
                key = KEY_PREFIX + "focus_pages",
                title = resources.getString(R.string.feature_focus_pages_title),
                summary = resources.getString(R.string.feature_focus_pages_summary),
                onClick = {
                    FocusPagesDialog.show(
                        context,
                        resources,
                        store,
                        source,
                        LauncherPagePreviewSource(),
                    )
                },
            ),
        )
    }

    /**
     * The row that says which apps the drawer leaves out.
     *
     * Not a switch, so it is not in the catalogue: the catalogue is one key per
     * on/off setting, and this is a set of apps out of everything installed.
     */
    private fun addHiddenApps(screen: Any, context: Context) {
        val store = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(context))

        api.add(
            screen,
            api.createAction(
                context = context,
                key = KEY_PREFIX + "hidden_apps",
                title = resources.getString(R.string.feature_hidden_apps_title),
                summary = hiddenAppsSummary(store),
                onClick = { chooseHiddenApps(context, store) },
            ),
        )
    }

    /**
     * Hands the question to the app drawer.
     *
     * The apps are picked where they are drawn, so settings steps out of the
     * way: it starts what the launcher will finish, goes home, and closes
     * itself rather than waiting behind the drawer with a count that is about
     * to be wrong.
     */
    private fun chooseHiddenApps(context: Context, store: HiddenAppsStore) {
        HideAppsSelection.begin(store.hidden())

        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        context.activityOrNull()?.finish()
    }

    /** The activity behind a themed preference context. */
    private fun Context.activityOrNull(): Activity? {
        var current: Context? = this
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun hiddenAppsSummary(store: HiddenAppsStore): String {
        val count = store.hidden().size
        if (count == 0) return resources.getString(R.string.feature_hidden_apps_summary_none)

        return resources.getQuantityString(R.plurals.feature_hidden_apps_summary, count, count)
    }

    /**
     * The row that says which app opens a tapped Web Search result.
     *
     * Not a switch, so it is not in the catalogue: the catalogue is one key per
     * on/off setting, and this is one app out of what the device offers. It is
     * left out entirely while Web Search is hidden, because there is then no
     * result to open.
     */
    private fun addWebSearchApp(screen: Any, context: Context) {
        if (settings[Settings.APP_DRAWER_SEARCH_HIDE_WEB]) return

        val store = SharedPreferencesWebSearchAppStore(LauncherSettings.preferences(context))
        val apps = WebSearchApps(context.packageManager)
        lateinit var row: Any

        row = api.createAction(
            context = context,
            key = KEY_PREFIX + "web_search_app",
            title = resources.getString(R.string.feature_app_drawer_search_web_app_title),
            summary = webSearchAppSummary(store, apps),
            onClick = {
                WebSearchAppDialog.show(context, resources, store, apps) {
                    api.setSummary(row, webSearchAppSummary(store, apps))
                }
            },
        )

        api.add(screen, row)
    }

    /**
     * An app that is gone reads as the launcher's own answer, which is what the
     * tap then does.
     */
    private fun webSearchAppSummary(store: WebSearchAppStore, apps: WebSearchApps): String {
        val label = store.chosen()?.let(apps::labelOf)
            ?: return resources.getString(R.string.feature_app_drawer_search_web_app_default)

        return resources.getString(R.string.feature_app_drawer_search_web_app_chosen, label)
    }

    /**
     * Writes one setting, greys its companion row with it, and settles the
     * choices that cannot all be on.
     *
     * The layout modes are several answers to the same question, so switching
     * one on switches the others off, on screen as well as in the store.
     * Without the second half a person would be looking at switches that all
     * read on while only one of them is. Which modes those are belongs to
     * [FeatureCatalog], so a mode added there needs no rule here.
     */
    private fun apply(
        setting: BoolSetting,
        value: Boolean,
        switches: Map<BoolSetting, Any>,
        companions: Map<BoolSetting, Any>,
    ) {
        settings.put(setting, value)
        companions[setting]?.let { api.setEnabled(it, value) }

        if (!value) return

        for (excluded in FeatureCatalog.layoutModesExcludedBy(setting)) {
            settings.put(excluded, false)
            switches[excluded]?.let { api.setChecked(it, false) }
        }
    }

    private companion object {
        const val KEY_PREFIX = "ple_"
        const val PAGE_KEY_PREFIX = KEY_PREFIX + "page_"
        const val RESTART_KEY = KEY_PREFIX + "restart_launcher"
    }
}
