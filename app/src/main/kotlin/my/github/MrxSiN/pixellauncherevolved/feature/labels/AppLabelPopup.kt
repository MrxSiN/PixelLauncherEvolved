package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.view.View

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabel
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Rename, in an app icon's long-press menu, right after Pause app.
 *
 * ```
 * PopupControllerForAppIcon.show(View)
 *   PopupContainerWithArrow.showComposePopup(int deepShortcutCount, List<PopupItem> systemShortcuts)
 * PopupItem(int iconResId, StringContainer label, Function0 action, PopupCategory category)
 * StringContainer.getText(Composer): String          an interface; the launcher ships one resource-backed kind
 * ```
 *
 * The menu is the launcher's own Compose menu, so the row is one more item in
 * the list it is given, built the way the launcher builds its own: its icon and
 * its text ("Edit Name", the launcher's own folder-name hint, translated with
 * the rest) are the launcher's resources, and its category is Pause app's. The
 * menu draws only resource-backed text, so the row cannot carry this module's
 * own string. The same menu opens on the Home screen, the dock,
 * folders and the app drawer, so the row is there in all of them; it is offered
 * for apps only, not for shortcuts, widgets or folders.
 *
 * Installed whatever is stored, because a first label can be given from here.
 * It runs only when a menu opens.
 */
internal object AppLabelPopup {

    fun install(context: FeatureContext) {
        runCatching { hook(context) }
            .onFailure { context.logger.warn("App labels: no Rename in the long-press menu on this launcher", it) }
    }

    private fun hook(context: FeatureContext) {
        val container = requireNotNull(context.findClass("com.android.launcher3.popup.PopupContainerWithArrow"))
        val base = requireNotNull(context.findClass("com.android.launcher3.popup.PopupContainer"))
        val itemInfo = requireNotNull(context.findClass(ITEM_INFO))
        val popupItem = requireNotNull(context.findClass("com.android.launcher3.popup.ui.PopupItem"))
        val resourceString = requireNotNull(context.findClass("com.android.launcher3.popup.ui.StringContainer\$ResourceString"))

        val show = Reflect.declaredMethod(container, "showComposePopup", Int::class.javaPrimitiveType!!, List::class.java)
        val ownItem = Reflect.declaredField(base, "itemInfo")
        val title = Reflect.declaredField(itemInfo, "title")
        val itemType = Reflect.declaredField(itemInfo, "itemType")
        // Named by position, not by class: the label, action and category types
        // are the launcher's own (one of them a shrunk Kotlin function type).
        @Suppress("UNCHECKED_CAST")
        val newItem = popupItem.declaredConstructors.single {
            it.parameterTypes.size == 4 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } as Constructor<Any>
        val actionType = newItem.parameterTypes[2]
        val labelOf = Reflect.declaredField(popupItem, "label")
        val categoryOf = Reflect.declaredField(popupItem, "category")
        val resId = Reflect.declaredField(resourceString, "resId")

        val launcher = context.appContext
        val pause = launcher.resources.getIdentifier("pause_app_label", "string", launcher.packageName)
        // The menu draws only the launcher's own strings. Its folder name field's
        // hint, "Edit Name", is the same action and is translated with the rest.
        val editName = launcher.resources.getIdentifier("folder_hint_text", "string", launcher.packageName)
        val newText = resourceString.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
        val icon = launcher.resources.getIdentifier("material_ic_edit_black_24dp", "drawable", launcher.packageName)
        val strings = requireNotNull(context.moduleResources)
        require(icon != 0 && editName != 0) { "the launcher has no edit icon or name" }

        val apps = AppIdentity(context, itemInfo)
        val store = AppLabelStore(LauncherSettings.preferences(launcher))

        context.xposed.hook(show).intercept { chain ->
            val items = chain.getArg(1) as? List<*>
            val popup = chain.thisObject as View
            val item = ownItem.get(popup)
            if (items.isNullOrEmpty() || item == null || itemType.getInt(item) != ITEM_TYPE_APPLICATION) {
                return@intercept chain.proceed()
            }
            val app = apps.targetOf(item) ?: return@intercept chain.proceed()

            val after = items.indexOfFirst { resourceString.isInstance(labelOf.get(it)) && resId.getInt(labelOf.get(it)) == pause }
            val rename = newItem.newInstance(
                icon,
                newText.newInstance(editName),
                Proxy.newProxyInstance(actionType.classLoader, arrayOf(actionType), action {
                    close(popup)
                    val own = (title.get(item) as CharSequence?)?.toString().orEmpty()
                    val current = store.labels().firstOrNull {
                        it.packageName == app.packageName && it.className == app.className && it.userId == app.userId
                    } ?: app
                    AppLabelEditor.show(popup.context, strings, store, context.logger, own, current)
                }),
                categoryOf.get(items[if (after >= 0) after else items.lastIndex]),
            )
            val withRename = ArrayList<Any?>(items.size + 1).apply {
                addAll(items)
                add(if (after >= 0) after + 1 else size, rename)
            }
            chain.proceed(arrayOf(chain.getArg(0), withRename))
        }
        context.logger.info("App labels: Rename is in the long-press menu")
    }

    /** A no-argument function that runs [run] and returns nothing. */
    private fun action(run: () -> Unit) = InvocationHandler { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "ple_rename"
            else -> {
                run()
                null
            }
        }
    }

    private fun close(popup: View) {
        Reflect.method(popup.javaClass, "close", Boolean::class.javaPrimitiveType!!)?.invoke(popup, true)
    }

    private const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
    private const val ITEM_TYPE_APPLICATION = 0
}

/** An empty entry for [item]'s app, or null for anything that is not one. */
internal fun AppIdentity.targetOf(item: Any): AppLabel? {
    val (component, userId) = componentAndUser(item) ?: return null
    return AppLabel(component.packageName, component.className, userId, name = null)
}
