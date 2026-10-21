package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import kotlin.math.roundToInt

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
 * The header is a view type of this module's own, created and bound here; the
 * launcher's span lookup gives an unknown type a whole row.
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
        val newHolder: Constructor<*> = holderClass.declaredConstructors.first { it.parameterTypes.contentEquals(arrayOf(View::class.java)) }
            .apply { isAccessible = true }
        val packageOf: Method = requireNotNull(Reflect.method(itemInfo, "getTargetPackage"))
        val adapterApps = Reflect.declaredField(adapter, "mApps")
        // RecyclerView's own holder field; looked up once, read on every bind.
        val itemView: Field = requireNotNull(Reflect.field(holderClass, "itemView"))
        val create = requireNotNull(Reflect.declared(adapter, "onCreateViewHolder") { it.parameterTypes.size == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType })
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
                picked.size + 1
            }.onFailure { context.logger.warn("The Mode's apps could not be put at the top of the drawer", it) }.getOrDefault(0)
            if (added == 0) chain.proceed() else chain.proceed(arrayOf((chain.getArg(0) as Int) + added, chain.getArg(1)))
        }

        context.xposed.hook(create).intercept { chain ->
            if (chain.getArg(1) != HEADER_TYPE) return@intercept chain.proceed()
            newHolder.newInstance(header(chain.getArg(0) as ViewGroup))
        }

        context.xposed.hook(bind).intercept { chain ->
            val view = chain.getArg(0)?.let(itemView::get)
            if (view !is TextView || view.tag !== HEADER_TAG) return@intercept chain.proceed()
            val position = chain.getArg(1) as Int
            val type = runCatching {
                val listed = items.get(adapterApps.get(chain.thisObject)) as List<*>
                itemType.getInt(listed[position])
            }.getOrDefault(-1)
            if (type != HEADER_TYPE) return@intercept chain.proceed()
            view.text = ModeOverrides.drawerTitle.orEmpty()
            null
        }

        context.logger.info("App drawer: a Mode's own apps can head the list")
    }

    /** A section heading in the drawer's own secondary text colour, inset like its icons. */
    private fun header(parent: ViewGroup): TextView = TextView(parent.context).apply {
        tag = HEADER_TAG
        val density = resources.displayMetrics.density
        val inset = (24 * density).roundToInt()
        setPadding(inset, (16 * density).roundToInt(), inset, (8 * density).roundToInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val color = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.textColorSecondary, color, true)) {
            setTextColor(context.getColorStateList(color.resourceId.takeIf { it != 0 } ?: return@apply))
        }
        isAccessibilityHeading = true
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private companion object {
        const val APPS_LIST = "com.android.launcher3.allapps.AlphabeticalAppsList"
        const val ADAPTER = "com.android.launcher3.allapps.BaseAllAppsAdapter"
        const val ADAPTER_ITEM = "com.android.launcher3.allapps.BaseAllAppsAdapter\$AdapterItem"
        const val VIEW_HOLDER = "com.android.launcher3.allapps.BaseAllAppsAdapter\$ViewHolder"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"

        /** `BaseAllAppsAdapter.VIEW_TYPE_ICON`. */
        const val ICON_TYPE = 2

        /** This module's own view type: far above the launcher's own (up to 512) and its search types. */
        const val HEADER_TYPE = 1 shl 29

        val HEADER_TAG = Any()
    }
}
