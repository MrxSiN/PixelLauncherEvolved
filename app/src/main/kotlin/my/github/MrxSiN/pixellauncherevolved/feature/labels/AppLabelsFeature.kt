package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.content.ComponentName
import android.content.Intent
import android.os.LocaleList
import android.os.SystemClock
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconReloader
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabels
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Per-app labels: a name of a person's choosing, and the label left off an icon
 * on the Home screen or in the app drawer.
 *
 * ```
 * BubbleTextView.applyIconAndLabel(ItemInfoWithIcon)        every app icon bind
 *   BubbleTextView.applyLabel(title, contentDescription, inactiveArchive, disabled)
 * AppInfoComparator.compare(AppInfo, AppInfo)                 app drawer order
 * AppsListData.<init>(AppInfo[], int)                         app drawer sections
 * SearchSession.query(Query, Executor, Consumer)              app search, see AppLabelSearch
 * ```
 *
 * Nothing of the launcher's is rewritten. The name goes to the one call that
 * puts an app's label on its icon, for that bind only, so the app's own label
 * stays in the launcher's model, its database and its icon cache, and every
 * other reader — launching, uninstalling, predictions, the database — sees the
 * app as it is. Only app entries are touched; a deep shortcut keeps the label
 * its app gave it.
 *
 * A hidden label is an empty one. The icon keeps the place the label's line
 * gives it, as a dock icon does, and the cell does not shrink: the launcher
 * sizes cells for the whole grid, never per app.
 *
 * Nothing is hooked until the first label is chosen. With none chosen this
 * feature costs the launcher nothing; after that each hook reads one volatile
 * reference and returns when there is nothing to do.
 */
class AppLabelsFeature : LauncherFeature {

    override val compatibility = CompatibilityFeature.APP_LABELS

    override val id: String = "home_app_labels"

    /** Installed always; the hooks themselves wait for a first label. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        AppLabelPopup.install(context)
        val store = AppLabelStore(LauncherSettings.preferences(context.appContext))
        val labels = store.publish()
        if (labels.isEmpty && AppLabels.hiddenSurfaces() == 0) {
            AppLabelStore.onFirstLabels = { hookOnce(context) }
            context.logger.info("App labels: none chosen; nothing hooked")
        } else {
            hookOnce(context)
        }

        // A Mode with its own label switches coming on or going off binds the icons
        // again through the same reload the everywhere switches use.
        val reloader = IconReloader(context.appContext, context.logger)
        ModeOverrides.onLabelsChanged = {
            if (AppLabels.hiddenSurfaces() != 0) hookOnce(context)
            if (hooked) reloader.reload("Mode labels", SystemClock.elapsedRealtime(), null)
        }
    }

    @Volatile
    private var hooked = false

    @Synchronized
    private fun hookOnce(context: FeatureContext) {
        if (hooked) return
        hooked = true
        AppLabelStore.onFirstLabels = null
        runCatching { hookLabels(context) }
            .onFailure { context.logger.warn("App labels: icons keep their own labels on this launcher", it) }
        runCatching { hookOrder(context) }
            .onFailure { context.logger.warn("App labels: the app drawer keeps its own order on this launcher", it) }
        AppLabelSearch.install(context)
    }

    private fun hookLabels(context: FeatureContext) {
        val icon = requireNotNull(context.findClass(ICON_VIEW))
        val itemInfo = requireNotNull(context.findClass(ITEM_INFO))
        val withIcon = requireNotNull(context.findClass(ITEM_INFO_WITH_ICON))
        val applyIconAndLabel = Reflect.declaredMethod(icon, "applyIconAndLabel", withIcon)
        val applyLabel = Reflect.declaredMethod(
            icon,
            "applyLabel",
            CharSequence::class.java,
            CharSequence::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
        )
        val display = Reflect.declaredField(icon, "mDisplay")
        val apps = AppIdentity(context, itemInfo)
        val describe = Descriptions(context)
        val binding = ThreadLocal.withInitial { Binding() }

        // Says which entry the label about to be applied belongs to. The bind is
        // synchronous and per thread (the taskbar binds on its own), so the
        // answer is handed to applyLabel through the thread rather than the view.
        context.xposed.hook(applyIconAndLabel).intercept { chain ->
            val labels = AppLabels.current()
            if (labels.isEmpty) {
                // Only a surface-wide hide applies; applyLabel finds this view and no entry.
                if (AppLabels.hiddenSurfaces() == 0) return@intercept chain.proceed()
                val slot = binding.get()!!
                slot.at = -1
                slot.view = chain.thisObject
                return@intercept try {
                    chain.proceed()
                } finally {
                    slot.view = null
                }
            }

            val slot = binding.get()!!
            slot.at = apps.find(labels, chain.getArg(0))
            if (slot.at < 0) return@intercept chain.proceed()
            slot.labels = labels
            slot.view = chain.thisObject
            try {
                chain.proceed()
            } finally {
                slot.at = -1
                slot.view = null
            }
        }

        context.xposed.hook(applyLabel).intercept { chain ->
            val slot = binding.get()!!
            val at = slot.at
            if (at < 0 || slot.view !== chain.thisObject) {
                // No entry of its own: only a label hidden on this whole surface. The
                // description argument is passed on untouched, so TalkBack keeps the name.
                val everywhere = AppLabels.hiddenSurfaces()
                if (everywhere == 0 || chain.getArg(TITLE) == null ||
                    everywhere and wholeSurfaceOf(display.getInt(chain.thisObject)) == 0
                ) {
                    return@intercept chain.proceed()
                }
                return@intercept chain.proceed(arrayOf("", chain.getArg(DESCRIPTION), chain.getArg(INACTIVE_ARCHIVE), chain.getArg(DISABLED)))
            }
            slot.at = -1

            val labels = slot.labels
            val title = chain.getArg(TITLE) as CharSequence?
            val description = chain.getArg(DESCRIPTION) as CharSequence?
            val surface = surfaceOf(display.getInt(chain.thisObject))
            val name = labels.name(at)?.takeIf { surface == AppLabels.HIDE_HOME || !AppLabels.homeOnly() }
            val hidden = labels.flags(at) and surface != 0 ||
                AppLabels.hiddenSurfaces() and wholeSurfaceOf(display.getInt(chain.thisObject)) != 0

            val shown: CharSequence? = when {
                title == null -> null
                hidden -> ""
                else -> name ?: title
            }
            val said = if (name == null || title == null || title.contentEquals(name)) {
                description
            } else {
                describe.of(labels, at, name, description ?: title)
            }
            if (shown === title && said === description) return@intercept chain.proceed()
            val result = chain.proceed(arrayOf(shown, said, chain.getArg(INACTIVE_ARCHIVE), chain.getArg(DISABLED)))
            // applyLabel words the description only for a disabled app; otherwise
            // applyIconAndLabel set the app's own just before, and it is replaced here.
            if (said !== description && chain.getArg(DISABLED) != true) (chain.thisObject as View).contentDescription = said
            result
        }

        // The notification dot words the description again after every bind and
        // every dot change, from the app's own description or label ("YouTube,
        // 1 notification"). Where it names the app, the chosen description goes in
        // its place, keeping the launcher's own wording around it.
        val applyDotState = Reflect.declaredMethod(icon, "applyDotState", itemInfo, Boolean::class.javaPrimitiveType!!)
        val ownDescription = Reflect.declaredField(itemInfo, "contentDescription")
        val ownTitle = Reflect.declaredField(itemInfo, "title")
        context.xposed.hook(applyDotState).intercept { chain ->
            val result = chain.proceed()
            val labels = AppLabels.current()
            if (!labels.hasNames()) return@intercept result

            val item = chain.getArg(0)
            val at = apps.find(labels, item)
            val view = chain.thisObject as View
            val onHome = surfaceOf(display.getInt(view)) == AppLabels.HIDE_HOME
            val name = if (at < 0 || !(onHome || !AppLabels.homeOnly())) null else labels.name(at)
            val current = view.contentDescription
            if (name == null || current == null) return@intercept result

            val own = ownDescription.get(item) as CharSequence?
            val title = ownTitle.get(item) as CharSequence?
            val said = describe.of(labels, at, name, own ?: title ?: return@intercept result)
            if (current === said) return@intercept result
            val text = current.toString()
            val replaced = when {
                text.contains(said) -> null
                own != null && text.contains(own) -> text.replace(own.toString(), said.toString())
                title != null && text.contains(title) -> text.replace(title.toString(), said.toString())
                else -> null
            }
            if (replaced != null) view.contentDescription = if (replaced == said.toString()) said else replaced
            result
        }

        context.logger.info("App labels: icons wear their chosen labels")
    }

    /**
     * The app drawer reads a renamed app under its new name: sorted by it, and
     * filed under its letter in the fast scroller.
     *
     * The order is the launcher's own comparison with the shown name put in
     * for the app's label; two apps that tie on it fall back to the launcher's
     * whole comparison, so the order stays total. The letter is the launcher's
     * own index, asked of the shown name, written to the drawer's copy of the
     * app as the launcher writes it, and recomputed by the launcher whenever it
     * rebuilds that copy.
     */
    private fun hookOrder(context: FeatureContext) {
        val itemInfo = requireNotNull(context.findClass(ITEM_INFO))
        val appInfo = requireNotNull(context.findClass(APP_INFO))
        val comparator = requireNotNull(context.findClass(APP_INFO_COMPARATOR))
        val labelComparator = requireNotNull(context.findClass(LABEL_COMPARATOR))
        val listData = requireNotNull(context.findClass(APPS_LIST_DATA))
        val indexClass = requireNotNull(context.findClass(ALPHABETIC_INDEX))

        val compare = Reflect.declaredMethod(comparator, "compare", appInfo, appInfo)
        val ownComparator = Reflect.declaredField(comparator, "mLabelComparator")
        val compareLabels = Reflect.declaredMethod(labelComparator, "compare", String::class.java, String::class.java)
        val title = Reflect.declaredField(itemInfo, "title")
        val appTitle = Reflect.field(itemInfo, "appTitle")
        val sectionName = Reflect.declaredField(appInfo, "sectionName")
        val newIndex = indexClass.getDeclaredConstructor(LocaleList::class.java).apply { isAccessible = true }
        val sectionOf = Reflect.declaredMethod(indexClass, "computeSectionName", CharSequence::class.java)
        val apps = AppIdentity(context, itemInfo)

        context.xposed.hook(compare).intercept { chain ->
            val labels = AppLabels.current()
            if (!labels.hasNames() || AppLabels.homeOnly()) return@intercept chain.proceed()

            val a = chain.getArg(0)
            val b = chain.getArg(1)
            val nameA = apps.nameOf(labels, a)
            val nameB = apps.nameOf(labels, b)
            if (nameA == null && nameB == null) return@intercept chain.proceed()

            val order = compareLabels.invoke(
                ownComparator.get(chain.thisObject),
                nameA ?: ownLabel(a, title, appTitle),
                nameB ?: ownLabel(b, title, appTitle),
            ) as Int
            if (order != 0) order else chain.proceed()
        }

        var index: Any? = null
        var indexLocales: LocaleList? = null
        context.xposed.hook(listData.getDeclaredConstructor(java.lang.reflect.Array.newInstance(appInfo, 0).javaClass, Int::class.javaPrimitiveType!!))
            .intercept { chain ->
                val labels = AppLabels.current()
                val list = chain.getArg(0) as? Array<*>
                if (labels.hasNames() && !AppLabels.homeOnly() && list != null) {
                    try {
                        synchronized(this) {
                            val locales = context.appContext.resources.configuration.locales
                            if (index == null || locales != indexLocales) {
                                index = newIndex.newInstance(locales)
                                indexLocales = locales
                            }
                            for (app in list) {
                                val name = apps.nameOf(labels, app) ?: continue
                                sectionName.set(app, sectionOf.invoke(index, name))
                            }
                        }
                    } catch (error: Throwable) {
                        context.logger.warn("App labels: the app drawer's letters stay the launcher's own", error)
                    }
                }
                chain.proceed()
            }

        context.logger.info("App labels: the app drawer is ordered by the shown names")
    }

    /** What the launcher's own comparison reads for an app, as it reads it. */
    private fun ownLabel(app: Any?, title: Field, appTitle: Field?): String {
        val archived = appTitle?.get(app) as CharSequence?
        if (!archived.isNullOrEmpty()) return archived.toString()
        return (title.get(app) as CharSequence?)?.toString() ?: ""
    }

    /** One thread's bind in progress. */
    private class Binding {
        @JvmField var at = -1
        @JvmField var labels: AppLabels = AppLabels.NONE
        @JvmField var view: Any? = null
    }

    private companion object {
        const val ICON_VIEW = "com.android.launcher3.BubbleTextView"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val ITEM_INFO_WITH_ICON = "com.android.launcher3.model.data.ItemInfoWithIcon"
        const val APP_INFO = "com.android.launcher3.model.data.AppInfo"
        const val APP_INFO_COMPARATOR = "com.android.launcher3.allapps.AppInfoComparator"
        const val LABEL_COMPARATOR = "com.android.launcher3.util.LabelComparator"
        const val APPS_LIST_DATA = "com.android.launcher3.model.data.AppsListData"
        const val ALPHABETIC_INDEX = "com.android.launcher3.compat.AlphabeticIndexCompat"

        /** `applyLabel(CharSequence title, CharSequence contentDescription, boolean inactiveArchive, boolean disabled)`. */
        const val TITLE = 0
        const val DESCRIPTION = 1
        const val INACTIVE_ARCHIVE = 2
        const val DISABLED = 3

        /** `BubbleTextView.mDisplay`, from the launcher's `iconDisplay` attribute. */
        const val DISPLAY_WORKSPACE = 0
        const val DISPLAY_ALL_APPS = 1
        const val DISPLAY_FOLDER = 2
        const val DISPLAY_PREDICTION_ROW = 8

        /** Which surface-wide hide ([AppLabels.hiddenSurfaces]) applies to an icon drawn as [display]. */
        fun wholeSurfaceOf(display: Int): Int = when (display) {
            DISPLAY_WORKSPACE -> AppLabels.HIDE_HOME
            DISPLAY_FOLDER -> AppLabels.HIDE_FOLDER
            DISPLAY_ALL_APPS, DISPLAY_PREDICTION_ROW -> AppLabels.HIDE_DRAWER
            else -> 0
        }

        /** Which of an entry's hide flags applies to an icon drawn as [display]; search, taskbar and the rest have none. */
        fun surfaceOf(display: Int): Int = when (display) {
            DISPLAY_WORKSPACE, DISPLAY_FOLDER -> AppLabels.HIDE_HOME
            DISPLAY_ALL_APPS, DISPLAY_PREDICTION_ROW -> AppLabels.HIDE_DRAWER
            else -> 0
        }
    }
}

/**
 * Which app an item is, read off the launcher's item without allocating.
 *
 * Only app entries answer. `ItemInfo.getTargetComponent()` builds an `Optional`
 * and a lambda per call, so the same answer is read from the fields it reads.
 */
internal class AppIdentity(context: FeatureContext, itemInfo: Class<*>) {

    private val itemType: Field = Reflect.declaredField(itemInfo, "itemType")
    private val user: Field = Reflect.declaredField(itemInfo, "user")
    private val ownComponent: Field? = Reflect.field(itemInfo, "mComponentName")
    private val intentOf: Method = Reflect.declaredMethod(itemInfo, "getIntent")
    private val appInfo: Class<*>? = context.findClass("com.android.launcher3.model.data.AppInfo")
    private val appComponent: Field? = appInfo?.let { Reflect.field(it, "componentName") }

    /** The entry for [item], or -1 for one with none or for anything that is not an app. */
    fun find(labels: AppLabels, item: Any?): Int {
        if (item == null) return -1
        return try {
            if (itemType.getInt(item) != ITEM_TYPE_APPLICATION) return -1
            val component = componentOf(item) ?: return -1
            // UserHandle.hashCode() is its user id; getIdentifier() is hidden API.
            val userId = (user.get(item) ?: return -1).hashCode()
            labels.find(component.packageName, component.className, userId)
        } catch (_: ReflectiveOperationException) {
            -1
        }
    }

    fun nameOf(labels: AppLabels, item: Any?): String? {
        val at = find(labels, item)
        return if (at < 0) null else labels.name(at)
    }

    /** [item]'s component and user id, or null for anything that is not an app. Allocates; not for binds. */
    fun componentAndUser(item: Any): Pair<ComponentName, Int>? = try {
        val component = componentOf(item)
        val owner = user.get(item)
        if (component == null || owner == null) null else component to owner.hashCode()
    } catch (_: ReflectiveOperationException) {
        null
    }

    private fun componentOf(item: Any): ComponentName? {
        if (appInfo != null && appComponent != null && appInfo.isInstance(item)) {
            return appComponent.get(item) as ComponentName?
        }
        return (Invoke.noArgs(intentOf, item) as Intent?)?.component ?: ownComponent?.get(item) as ComponentName?
    }

    private companion object {
        /** `LauncherSettings.Favorites.ITEM_TYPE_APPLICATION`. */
        const val ITEM_TYPE_APPLICATION = 0
    }
}

/**
 * What a screen reader says for a renamed app: the chosen name, then the app it
 * is, in the launcher's own words for that app — "Video, YouTube", or "Video,
 * Work YouTube" for a work app — so the name a person chose leads and the app's
 * real identity is never lost.
 *
 * Built once per entry and per launcher description, then handed back as the
 * same string on every bind after.
 */
private class Descriptions(context: FeatureContext) {

    private val resources = context.moduleResources
    private var labels: AppLabels = AppLabels.NONE
    private var described = arrayOfNulls<CharSequence>(0)
    private var said = arrayOfNulls<String>(0)

    @Synchronized
    fun of(labels: AppLabels, at: Int, name: String, own: CharSequence): CharSequence {
        if (labels !== this.labels) {
            this.labels = labels
            described = arrayOfNulls(labels.size())
            said = arrayOfNulls(labels.size())
        }
        val last = said[at]
        if (last != null && described[at] === own) return last

        val text = resources?.getString(R.string.app_labels_content_description, name, own) ?: name
        described[at] = own
        said[at] = text
        return text
    }
}

