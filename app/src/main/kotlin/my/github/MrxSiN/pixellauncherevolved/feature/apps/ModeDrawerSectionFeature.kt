package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.widget.TextView

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Shows the apps the winning Mode picked under a header with its name, at the
 * top of All apps (ROADMAP I17).
 *
 * The section is put in front of the launcher's own A–Z list while it is being
 * built (`addAppsWithSections` for the list's own apps), before the launcher
 * works out rows and diffs the list, so scrolling, rows, the fast scroller and
 * animations are the launcher's own. Every app also stays in its A–Z place, so
 * nothing is only reachable from the section. The apps come from the list the
 * launcher already filtered, so hidden apps, the profile tabs and Private space
 * stay as they are.
 *
 * The header is the launcher's own one-line text row (`VIEW_TYPE_EMPTY_SEARCH`)
 * with no app in it, which the launcher creates and gives a whole row, and
 * binds to nothing; only its text is set here. A new view type is not possible:
 * R8 removed the holder class's own constructor in launcher 907.
 */
class ModeDrawerSectionFeature : LauncherFeature {

    override val id: String = "focus_drawer_section"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val list = requireNotNull(context.findClass(APPS_LIST))
        val adapter = requireNotNull(context.findClass(ADAPTER))
        val itemClass = requireNotNull(context.findClass(ADAPTER_ITEM))
        val holderClass = requireNotNull(context.findClass(VIEW_HOLDER))
        val itemInfo = requireNotNull(context.findClass(ITEM_INFO))

        val addApps = Reflect.declaredMethod(list, "addAppsWithSections", Int::class.javaPrimitiveType!!, List::class.java)
        val apps = Reflect.declaredField(list, "mApps")
        val items = Reflect.declaredField(list, "mAdapterItems")
        val newItem: Constructor<*> = itemClass.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
        val itemApp: Field = Reflect.declaredField(itemClass, "itemInfo")
        val itemType: Field = Reflect.declaredField(itemClass, "viewType")
        val packageOf: Method = requireNotNull(Reflect.method(itemInfo, "getTargetPackage"))
        val adapterApps = Reflect.declaredField(adapter, "mApps")
        // RecyclerView's own holder field; looked up once, read on every bind.
        val itemView: Field = requireNotNull(Reflect.field(holderClass, "itemView"))
        val bind = requireNotNull(Reflect.declared(adapter, "onBindViewHolder") { it.parameterTypes.size == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType })

        context.xposed.hook(addApps).intercept { chain ->
            val section = ModeOverrides.drawerApps
            val title = ModeOverrides.drawerTitle
            // While apps are being ticked every app has to be in its own place, once.
            if (section.isEmpty() || title == null || HideAppsSelection.isSelecting || chain.getArg(1) !== apps.get(chain.thisObject)) {
                return@intercept chain.proceed()
            }
            val added = runCatching {
                @Suppress("UNCHECKED_CAST")
                val target = items.get(chain.thisObject) as ArrayList<Any>
                val picked = (chain.getArg(1) as List<*>).filter { app -> (app?.let { Invoke.noArgs(packageOf, it) } as? String) in section }
                if (picked.isEmpty()) return@runCatching 0
                target += newItem.newInstance(HEADER_TYPE)
                for (app in picked) target += newItem.newInstance(ICON_TYPE).also { itemApp.set(it, app) }
                // A whole-row divider ends the section, so A–Z starts on a row of its own.
                target += newItem.newInstance(DIVIDER_TYPE)
                picked.size + 2
            }.onFailure { context.logger.warn("The Mode's apps could not be put at the top of the drawer", it) }.getOrDefault(0)
            if (added == 0) chain.proceed() else chain.proceed(arrayOf((chain.getArg(0) as Int) + added, chain.getArg(1)))
        }

        context.xposed.hook(bind).intercept { chain ->
            val result = chain.proceed()
            // Ours: the launcher's text row with no app in it; the launcher's own always has one.
            val view = chain.getArg(0)?.let(itemView::get) as? TextView ?: return@intercept result
            val item = runCatching { (items.get(adapterApps.get(chain.thisObject)) as List<*>)[chain.getArg(1) as Int] }.getOrNull()
                ?: return@intercept result
            if (itemType.getInt(item) == HEADER_TYPE && itemApp.get(item) == null) {
                view.text = ModeOverrides.drawerTitle.orEmpty()
                view.isAccessibilityHeading = true
            }
            result
        }

        context.logger.info("App drawer: a Mode's own apps can head the list")
    }

    private companion object {
        const val APPS_LIST = "com.android.launcher3.allapps.AlphabeticalAppsList"
        const val ADAPTER = "com.android.launcher3.allapps.BaseAllAppsAdapter"
        const val ADAPTER_ITEM = "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
        const val VIEW_HOLDER = "com.android.launcher3.allapps.BaseAllAppsAdapter\$ViewHolder"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"

        /** `BaseAllAppsAdapter.VIEW_TYPE_ICON`. */
        const val ICON_TYPE = 2

        /** `VIEW_TYPE_ALL_APPS_DIVIDER`: the launcher's own divider row (`private_space_divider`). */
        const val DIVIDER_TYPE = 8

        /** `BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH`: a one-line text row. */
        const val HEADER_TYPE = 4
    }
}
