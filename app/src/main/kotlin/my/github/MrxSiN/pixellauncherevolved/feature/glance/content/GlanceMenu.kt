package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.content.Context
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Proxy

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.settings.GlanceScreen
import my.github.MrxSiN.pixellauncherevolved.feature.settings.GlanceUi
import my.github.MrxSiN.pixellauncherevolved.feature.settings.PageHost
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Adds "At a Glance settings" to the menu a long press on At a Glance opens, which opens
 * [GlanceScreen] over the home screen.
 *
 * ```
 * com.android.launcher3.popup.PopupContainer$Companion
 *   showForMenuItems(ActivityContext, View, List<PopupData>, Rect)
 * com.android.launcher3.popup.PopupData(int id, int icon, StringContainer label, PopupCategory,
 *   EventEnum, String type, <action>)            // action: a Kotlin Function3, obfuscated
 * ```
 * Only a menu opened inside the launcher's At a Glance container (on the card shown) is changed. The new item copies the
 * category, event and type of the menu's first item; its action type is read off `PopupData`'s
 * `popupAction` field, and its icon is the launcher's own palette icon, looked up by name. The
 * action is called as `invoke(activityContext, itemInfo, view)` and its answer is not used.
 */
internal object GlanceMenu {

    private const val CONTAINER = "com.google.android.apps.nexuslauncher.qsb.SmartspaceViewContainer"
    private const val COMPANION = "com.android.launcher3.popup.PopupContainer\$Companion"
    private const val SHOW = "showForMenuItems"
    private const val DATA = "com.android.launcher3.popup.PopupData"
    private const val FIXED = "com.android.launcher3.popup.ui.StringContainer\$FixedString"
    private const val ICON = "ic_palette"

    fun install(context: FeatureContext) {
        val container = requireNotNull(context.findClass(CONTAINER)) { CONTAINER }
        val companion = requireNotNull(context.findClass(COMPANION)) { COMPANION }
        val show = requireNotNull(Reflect.declared(companion, SHOW) { m -> m.parameterTypes.size == 4 && m.parameterTypes[2] == List::class.java }) { SHOW }
        val resources = requireNotNull(context.moduleResources) { "module resources" }
        val id = View.generateViewId()
        // What builds the item is looked up the first time an At a Glance menu opens, off launcher start.
        val parts by lazy { Parts.of(context) }

        context.xposed.hook(show).intercept { chain ->
            val view = chain.args[1] as? View
            val items = chain.args[2] as? List<*>
            if (view == null || items.isNullOrEmpty() || !inside(view, container)) return@intercept chain.proceed()
            val added = runCatching {
                val (action, build, label, fields) = parts
                val template = requireNotNull(items[0])
                val icon = view.resources.getIdentifier(ICON, "drawable", view.context.packageName).takeIf { it != 0 } ?: fields.icon.getInt(template)
                val open = Proxy.newProxyInstance(action.classLoader, arrayOf(action)) { proxy, method, args ->
                    when (method.name) {
                        "invoke" -> {
                            val activity = args?.getOrNull(0) as? Context ?: view.context
                            runCatching {
                                PageHost.open(activity, resources.getString(R.string.settings_page_glance)) { screen ->
                                    GlanceScreen(GlanceUi(screen, resources, context.xposed.moduleApplicationInfo.packageName, context.logger), context.settings).show()
                                }
                            }
                                .onFailure { context.logger.warn("At a Glance settings could not be opened", it) }
                            null
                        }
                        "equals" -> proxy === args?.getOrNull(0)
                        "hashCode" -> System.identityHashCode(proxy)
                        else -> "GlanceMenu"
                    }
                }
                val item = build.newInstance(
                    id, icon, label.newInstance(resources.getString(R.string.glance_menu_settings)),
                    fields.category.get(template), fields.event.get(template), fields.type.get(template), open,
                )
                ArrayList<Any?>(items.size + 1).apply { addAll(items); add(item) }
            }.onFailure { context.logger.warn("At a Glance settings could not be added to the menu", it) }.getOrNull()
                ?: return@intercept chain.proceed()
            chain.proceed(chain.args.toTypedArray().also { it[2] = added })
        }
    }

    /** Whether [view] is the At a Glance container or inside it: the menu is anchored on the page shown. */
    private fun inside(view: View, container: Class<*>): Boolean {
        var at: Any? = view
        while (at is View) {
            if (container.isInstance(at)) return true
            at = at.parent
        }
        return false
    }

    private class Fields(val icon: Field, val category: Field, val event: Field, val type: Field) {
        init {
            for (field in arrayOf(icon, category, event, type)) field.isAccessible = true
        }
    }

    /** `PopupData`'s action type, its full constructor, `FixedString`'s constructor and the fields copied. */
    private data class Parts(val action: Class<*>, val build: java.lang.reflect.Constructor<*>, val label: java.lang.reflect.Constructor<*>, val fields: Fields) {
        companion object {
            fun of(context: FeatureContext): Parts {
                val data = requireNotNull(context.findClass(DATA)) { DATA }
                val fixed = requireNotNull(context.findClass(FIXED)) { FIXED }
                val action = requireNotNull(Reflect.field(data, "popupAction")).type
                val build = requireNotNull(
                    data.declaredConstructors.firstOrNull { c ->
                        val p = c.parameterTypes
                        p.size == 7 && p[0] == Int::class.javaPrimitiveType && p[1] == Int::class.javaPrimitiveType &&
                            p[2].isAssignableFrom(fixed) && p[5] == String::class.java && p[6] == action
                    },
                ) { "PopupData(int, int, StringContainer, PopupCategory, EventEnum, String, action)" }.apply { isAccessible = true }
                val label = fixed.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
                val fields = Fields(
                    requireNotNull(Reflect.field(data, "iconResId")),
                    requireNotNull(Reflect.field(data, "category")),
                    requireNotNull(Reflect.field(data, "eventId")),
                    requireNotNull(Reflect.field(data, "type")),
                )
                return Parts(action, build, label, fields)
            }
        }
    }
}
