package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Calendar

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Widget stacks: several home screen widgets in one place, swiped through.
 *
 * ```
 * LauncherAppWidgetHostView.onInterceptTouchEvent / onTouchEvent       the swipe ([StackSwipe])
 * CellLayout.addViewToCellLayout(View, int, CellLayoutLayoutParams, boolean)   a stack's widget lands on a page
 * PopupContainer.showComposePopup(int, List<PopupItem>)                a widget's long-press menu
 * StringContainer$ResourceString.getText(Composer)                     the menu draws only launcher strings
 * LauncherAppWidgetHostView.onInitializeAccessibilityNodeInfo; View.performAccessibilityAction
 * Launcher.onResume                                                    home is back: Smart rotate, Remember
 * Launcher.completeAddAppWidget(int, ItemInfo, AppWidgetHostView, ...) a widget from the picker is on a page
 * ItemDeleteHandler.completeDelete(ItemInfo, String)                   a delete is final
 * ResizeManager.beginResizeSession / runAtomicResizeSession / finish   resizing a stack
 * ```
 *
 * Where stacks live and why no widget can be lost is [StackGraph]'s to say;
 * the pages they keep their waiting widgets on are hidden by the Modes pages
 * bind filter, which [WidgetStacks.onBind] feeds. Each hook group past the
 * swipe installs on its own: one this launcher lacks takes only its own part
 * away.
 *
 * Live: switching it off binds the home screen again with every page showing,
 * so each widget waiting in a stack appears on a page of its own; switching it
 * back on puts the stacks back as they were. With no stack the touch hooks
 * return after one array length check.
 */
class WidgetStacksFeature : ToggleFeature(Settings.WIDGET_STACKS), SharedPreferences.OnSharedPreferenceChangeListener {

    override val compatibility = CompatibilityFeature.WIDGET_STACKS

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var indicators: StackIndicators? = null

    /** The launcher on show, held weakly, for settings' Restore standalone widgets. */
    @Volatile
    private var current: WeakReference<Any>? = null

    override fun install(context: FeatureContext) {
        val prefs = LauncherSettings.preferences(context.appContext)
        val host = StackHost.resolve(context.classLoader)
        val strings = requireNotNull(context.moduleResources) { "module resources are unreachable" }
        val indicators = StackIndicators().also { indicators = it }
        val swipe = StackSwipe(host, context.logger, indicators)
        val actions = StackActions(host, swipe, context.logger, context.classLoader)
        val dialogs = StackDialogs(host, swipe, actions, strings, context.logger)

        WidgetStacks.enabled = { context.settings[toggle] }
        readChoices(context)
        WidgetStacks.onBound = swipe::forgetViews
        WidgetStacks.preferences = prefs
        WidgetStacks.describe = { label, at, of -> strings.getString(R.string.widget_stacks_a11y, label, at, of) }
        WidgetStacks.restoreAll = {
            val launcher = current?.get()
            if (launcher == null) null else onMain { actions.restoreAll(launcher) }
        }

        val hostView = requireNotNull(context.findClass(HOST_VIEW))
        context.xposed.hook(Reflect.declaredMethod(hostView, "onInterceptTouchEvent", MotionEvent::class.java)).intercept { chain ->
            if (WidgetStacks.anchors.isEmpty()) return@intercept chain.proceed()
            if (swipe.intercept(chain.thisObject as View, chain.getArg(0) as MotionEvent)) true else chain.proceed()
        }
        context.xposed.hook(Reflect.declaredMethod(hostView, "onTouchEvent", MotionEvent::class.java)).intercept { chain ->
            if (WidgetStacks.anchors.isEmpty()) return@intercept chain.proceed()
            if (swipe.touch(chain.thisObject as View, chain.getArg(0) as MotionEvent)) true else chain.proceed()
        }

        // Each time a stack's widget on show lands on a page: its dots, and its neighbours parked.
        val cellLayout = requireNotNull(context.findClass(CELL_LAYOUT))
        val addToPage = requireNotNull(Reflect.declared(cellLayout, "addViewToCellLayout") { it.parameterTypes.size == 4 })
        context.xposed.hook(addToPage).intercept { chain ->
            val result = chain.proceed()
            if (WidgetStacks.anchors.isNotEmpty()) {
                val child = chain.getArg(0) as? View
                if (child != null && isAnchor(host, child)) main.post { swipe.onAnchorAdded(child) }
            }
            result
        }

        installMenu(context, host, dialogs, strings)
        isolated(context, "dropping a widget on another") { StackDrop.install(context, host, actions) }
        isolated(context, "TalkBack actions") { installAccessibility(context, host, hostView, swipe, dialogs, strings) }
        isolated(context, "Smart rotate and Remember last widget") { installHome(context, host, swipe, actions) }
        isolated(context, "adding from the widget picker") { installPicker(context, actions) }
        isolated(context, "keeping a stack when its widget is removed") { installDelete(context, actions) }
        isolated(context, "resizing a stack") { installResize(context, host, swipe, actions) }
        prefs.registerOnSharedPreferenceChangeListener(this)
        context.logger.info("Widget stacks ready")
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        when (key) {
            toggle.key -> {
                WidgetStacks.on = preferences.getBoolean(key, toggle.default)
                main.post { ModeOverrides.rebindWorkspace?.invoke() }
            }
            Settings.WIDGET_STACKS_SIDEWAYS.key, Settings.WIDGET_STACKS_REMEMBER.key,
            Settings.WIDGET_STACKS_SMART_ROTATE.key, Settings.WIDGET_STACKS_INDICATOR.key -> {
                WidgetStacks.sideways = preferences.getBoolean(Settings.WIDGET_STACKS_SIDEWAYS.key, Settings.WIDGET_STACKS_SIDEWAYS.default)
                WidgetStacks.remember = preferences.getBoolean(Settings.WIDGET_STACKS_REMEMBER.key, Settings.WIDGET_STACKS_REMEMBER.default)
                WidgetStacks.smartRotate =
                    preferences.getBoolean(Settings.WIDGET_STACKS_SMART_ROTATE.key, Settings.WIDGET_STACKS_SMART_ROTATE.default)
                WidgetStacks.indicatorMode = preferences.getInt(Settings.WIDGET_STACKS_INDICATOR.key, Settings.WIDGET_STACKS_INDICATOR.default)
                main.post { indicators?.restAll() }
            }
        }
    }

    private fun readChoices(context: FeatureContext) {
        WidgetStacks.on = context.settings[toggle]
        WidgetStacks.sideways = context.settings[Settings.WIDGET_STACKS_SIDEWAYS]
        WidgetStacks.remember = context.settings[Settings.WIDGET_STACKS_REMEMBER]
        WidgetStacks.smartRotate = context.settings[Settings.WIDGET_STACKS_SMART_ROTATE]
        WidgetStacks.indicatorMode = context.settings[Settings.WIDGET_STACKS_INDICATOR]
    }

    /**
     * Create stack, Add widget and Edit stack in a home screen widget's long-press menu.
     *
     * Built as App labels builds Rename ([my.github.MrxSiN.pixellauncherevolved.feature.labels.AppLabelPopup]):
     * more `PopupItem`s in the list the menu is handed, with the launcher's own
     * icons, and text answered by `getText` for these labels only. The launcher's
     * own Remove stays, and removes the widget on show; the next one takes its place.
     */
    private fun installMenu(context: FeatureContext, host: StackHost, dialogs: StackDialogs, strings: Resources) {
        val container = requireNotNull(context.findClass("com.android.launcher3.popup.PopupContainer"))
        val popupItem = requireNotNull(context.findClass("com.android.launcher3.popup.ui.PopupItem"))
        val resourceString = requireNotNull(context.findClass("com.android.launcher3.popup.ui.StringContainer\$ResourceString"))
        val composer = requireNotNull(context.findClass("androidx.compose.runtime.Composer"))

        val show = Reflect.declaredMethod(container, "showComposePopup", Int::class.javaPrimitiveType!!, List::class.java)
        val ownItem = Reflect.declaredField(container, "itemInfo")
        @Suppress("UNCHECKED_CAST")
        val newItem = popupItem.declaredConstructors.single {
            it.parameterTypes.size == 4 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } as Constructor<Any>
        val actionType = newItem.parameterTypes[2]
        val categoryOf = Reflect.declaredField(popupItem, "category")
        // A widget with nothing to set up hands the menu no rows to borrow a category from.
        val fixed = requireNotNull(context.findClass("com.android.launcher3.popup.PopupCategory"))
            .getField("SYSTEM_SHORTCUT_FIXED").get(null)
        val originalView = Reflect.declaredField(container, "originalView")

        val launcher = context.appContext
        fun id(name: String, type: String) = launcher.resources.getIdentifier(name, type, launcher.packageName)
        val addIcon = id("widgets_24px", "drawable")
        val editIcon = id("material_ic_edit_black_24dp", "drawable")
        // Any launcher string will do: getText answers for these labels by identity.
        val anyText = id("gadget_setup_text", "string")
        require(addIcon != 0 && editIcon != 0 && anyText != 0) { "the launcher has no widget or edit icon" }
        val newText = resourceString.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
        val createLabel = newText.newInstance(anyText)
        val addLabel = newText.newInstance(anyText)
        val editLabel = newText.newInstance(anyText)
        val createText = strings.getString(R.string.widget_stacks_create)
        val addText = strings.getString(R.string.widget_stacks_add_widget)
        val editText = strings.getString(R.string.widget_stacks_edit)

        context.xposed.hook(Reflect.declaredMethod(resourceString, "getText", composer)).intercept { chain ->
            when {
                chain.thisObject === createLabel -> createText
                chain.thisObject === addLabel -> addText
                chain.thisObject === editLabel -> editText
                else -> chain.proceed()
            }
        }

        context.xposed.hook(show).intercept { chain ->
            val items = chain.getArg(1) as? List<*>
            val popup = chain.thisObject as View
            val item = ownItem.get(popup)
            if (!WidgetStacks.on || items == null || !host.isWidget(item)) return@intercept chain.proceed()
            val widget = originalView.get(popup) as? View ?: return@intercept chain.proceed()
            val category = items.lastOrNull()?.let(categoryOf::get) ?: fixed
            fun row(icon: Int, label: Any, run: () -> Unit) = newItem.newInstance(
                icon, label,
                Proxy.newProxyInstance(actionType.classLoader, arrayOf(actionType), action {
                    close(popup)
                    runCatching(run).onFailure { context.logger.warn("Widget stacks: the stack could not be opened", it) }
                }),
                category,
            )
            val rows = ArrayList<Any?>(items.size + 2).apply { addAll(items) }
            if (WidgetStacks.isAnchor(host.id.getInt(item))) {
                rows += row(addIcon, addLabel) { dialogs.addTo(widget) }
                rows += row(editIcon, editLabel) { dialogs.edit(widget) }
            } else {
                rows += row(addIcon, createLabel) { dialogs.addTo(widget) }
            }
            chain.proceed(arrayOf(chain.getArg(0), rows))
        }
    }

    /**
     * Next widget, Previous widget and Edit stack as TalkBack actions on a stack, and
     * "Widget stack. Calendar. 2 of 4." as its state, so no swipe is ever needed.
     */
    private fun installAccessibility(
        context: FeatureContext,
        host: StackHost,
        hostView: Class<*>,
        swipe: StackSwipe,
        dialogs: StackDialogs,
        strings: Resources,
    ) {
        // Immutable, so made once rather than at every node TalkBack asks for.
        val next = AccessibilityNodeInfo.AccessibilityAction(ACTION_NEXT, strings.getString(R.string.widget_stacks_next))
        val previous = AccessibilityNodeInfo.AccessibilityAction(ACTION_PREVIOUS, strings.getString(R.string.widget_stacks_previous))
        val edit = AccessibilityNodeInfo.AccessibilityAction(ACTION_EDIT, strings.getString(R.string.widget_stacks_edit))
        context.xposed.hook(Reflect.declaredMethod(hostView, "onInitializeAccessibilityNodeInfo", AccessibilityNodeInfo::class.java))
            .intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                if (WidgetStacks.anchors.isNotEmpty() && isAnchor(host, view)) {
                    val node = chain.getArg(0) as AccessibilityNodeInfo
                    val stack = WidgetStacks.graph.stackOf(host.id.getInt(view.tag))
                    if (stack != null) {
                        node.stateDescription = WidgetStacks.describe(host.label(view) ?: "", stack.shown + 1, stack.members.size)
                        if (stack.shown + 1 < stack.members.size) node.addAction(next)
                        if (stack.shown > 0) node.addAction(previous)
                        node.addAction(edit)
                    }
                }
                result
            }
        context.xposed.hook(Reflect.declaredMethod(View::class.java, "performAccessibilityAction", Int::class.javaPrimitiveType!!, Bundle::class.java))
            .intercept { chain ->
                val action = chain.getArg(0) as Int
                val view = chain.thisObject as View
                if (action < ACTION_NEXT || action > ACTION_EDIT || !hostView.isInstance(view) || !isAnchor(host, view)) {
                    return@intercept chain.proceed()
                }
                when (action) {
                    ACTION_NEXT -> swipe.stepBy(view, 1, announce = true)
                    ACTION_PREVIOUS -> swipe.stepBy(view, -1, announce = true)
                    else -> {
                        dialogs.edit(view)
                        true
                    }
                }
            }
    }

    /**
     * Each time home comes back: Smart rotate brings forward the widget set for the
     * part of the day, unless the person chose one in the last [CHOSEN_GRACE_MS]; with
     * Remember last widget off, a stack goes back to its first widget. Nothing runs at
     * any other time, so a stack never changes while it is being looked at.
     */
    private fun installHome(context: FeatureContext, host: StackHost, swipe: StackSwipe, actions: StackActions) {
        val launcherClass = requireNotNull(context.findClass(LAUNCHER))
        context.hookAfter(launcherClass, "onResume") { launcher, _ ->
            if (launcher == null) return@hookAfter
            current = WeakReference(launcher)
            if (!WidgetStacks.on || WidgetStacks.anchors.isEmpty() || (WidgetStacks.remember && !WidgetStacks.smartRotate)) return@hookAfter
            main.post { runCatching { homeBack(host, swipe, actions, launcher) } }
        }
    }

    private fun homeBack(host: StackHost, swipe: StackSwipe, actions: StackActions, launcher: Any) {
        val slot = StackGraph.Slot.at(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        for (stack in WidgetStacks.graph.stacks) {
            val anchor = actions.viewOf(launcher, stack.shownId) ?: continue
            if (swipe.isBusy(anchor)) continue
            val since = WidgetStacks.sinceChosen(stack)
            var target = -1
            if (WidgetStacks.smartRotate && since > CHOSEN_GRACE_MS) target = stack.slots.indexOf(slot)
            if (target < 0 && !WidgetStacks.remember && since > RECENT_MS) target = 0
            if (target >= 0 && target != stack.shown) swipe.fadeTo(anchor, target)
        }
    }

    /** A widget picked in the launcher's picker for a stack joins it once the launcher has put it on a page. */
    private fun installPicker(context: FeatureContext, actions: StackActions) {
        val launcherClass = requireNotNull(context.findClass(LAUNCHER))
        val complete = requireNotNull(Reflect.declared(launcherClass, "completeAddAppWidget") { it.parameterTypes.size == 7 })
        context.xposed.hook(complete).intercept { chain ->
            val result = chain.proceed()
            val launcher = chain.thisObject
            val appWidgetId = chain.getArg(0) as Int
            main.post {
                runCatching { actions.onWidgetAdded(launcher, appWidgetId) }
                    .onFailure { context.logger.warn("Widget stacks: the picked widget could not join the stack", it) }
            }
            result
        }
    }

    /** When the launcher's own Remove has deleted a stack's widget on show, the next one takes its place. */
    private fun installDelete(context: FeatureContext, actions: StackActions) {
        val handler = requireNotNull(context.findClass("com.android.launcher3.dragndrop.ItemDeleteHandler"))
        val complete = requireNotNull(Reflect.declared(handler, "completeDelete") { it.parameterTypes.size == 2 })
        val activityContext: Field = Reflect.declaredField(handler, "activityContext")
        context.xposed.hook(complete).intercept { chain ->
            val result = chain.proceed()
            val item = chain.getArg(0)
            val launcher = activityContext.get(chain.thisObject)
            if (item != null && launcher != null && WidgetStacks.anchors.isNotEmpty()) {
                main.post {
                    runCatching { actions.onDeleted(launcher, item) }
                        .onFailure { context.logger.warn("Widget stacks: the next widget could not take the removed one's place", it) }
                }
            }
            result
        }
    }

    /**
     * Resizing a stack: the frame's limits are narrowed so every widget in the stack
     * can take the size, and when it closes the waiting widgets take it too, with
     * their apps told, so no member is left at another size.
     */
    private fun installResize(context: FeatureContext, host: StackHost, swipe: StackSwipe, actions: StackActions) {
        val manager = requireNotNull(context.findClass("com.android.launcher3.widget.resize.ResizeManager"))
        val constraints = requireNotNull(context.findClass("com.android.launcher3.widget.resize.ResizeManager\$ResizeConstraints"))
        val widgetView = Reflect.declaredField(manager, "widgetView")
        val limits = Reflect.declaredField(manager, "resizeConstraints")
        val minH = Reflect.declaredField(constraints, "minHSpan")
        val minV = Reflect.declaredField(constraints, "minVSpan")
        val minSpanX = Reflect.field(host.id.declaringClass, "minSpanX") ?: error("no minSpanX")
        val minSpanY = Reflect.field(host.id.declaringClass, "minSpanY") ?: error("no minSpanY")

        fun narrow(resize: Any) {
            val view = widgetView.get(resize) as? View ?: return
            if (!isAnchor(host, view)) return
            val launcher = host.launcher(view) ?: return
            val stack = WidgetStacks.graph.stackOf(host.id.getInt(view.tag)) ?: return
            val bounds = limits.get(resize) ?: return
            var h = minH.getInt(bounds)
            var v = minV.getInt(bounds)
            for (id in stack.members) {
                val item = host.modelItem(launcher, id) ?: continue
                h = maxOf(h, minSpanX.getInt(item))
                v = maxOf(v, minSpanY.getInt(item))
            }
            minH.setInt(bounds, h)
            minV.setInt(bounds, v)
        }
        for (name in arrayOf("beginResizeSession", "runAtomicResizeSession")) {
            val method = Reflect.declared(manager, name) { true } ?: continue
            context.xposed.hook(method).intercept { chain ->
                if (WidgetStacks.anchors.isNotEmpty()) runCatching { narrow(chain.thisObject) }
                chain.proceed()
            }
        }
        val finish = requireNotNull(Reflect.declared(manager, "finish") { it.parameterTypes.isEmpty() })
        context.xposed.hook(finish).intercept { chain ->
            val result = chain.proceed()
            val view = widgetView.get(chain.thisObject) as? View
            if (view != null && WidgetStacks.anchors.isNotEmpty() && isAnchor(host, view)) {
                main.post {
                    runCatching { resizeMembers(host, swipe, view) }
                        .onFailure { context.logger.warn("Widget stacks: the stack's other widgets could not take its new size", it) }
                }
            }
            result
        }
    }

    private fun resizeMembers(host: StackHost, swipe: StackSwipe, anchor: View) {
        val launcher = host.launcher(anchor) ?: return
        val stack = WidgetStacks.graph.stackOf(host.id.getInt(anchor.tag)) ?: return
        val width = host.spanX.getInt(anchor.tag)
        val height = host.spanY.getInt(anchor.tag)
        for (id in stack.members) {
            if (id == stack.shownId) continue
            val item = host.modelItem(launcher, id) ?: continue
            if (host.spanX.getInt(item) == width && host.spanY.getInt(item) == height) continue
            host.move(launcher, item, host.screenId.getInt(item), 0, 0, width, height)
            swipe.parked(id)?.let { host.resized(it, width, height) }
        }
        swipe.onAnchorAdded(anchor)
    }

    private fun isAnchor(host: StackHost, view: View): Boolean {
        val tag = view.tag ?: return false
        return host.id.declaringClass.isInstance(tag) && WidgetStacks.isAnchor(host.id.getInt(tag))
    }

    private inline fun isolated(context: FeatureContext, part: String, install: () -> Unit) {
        runCatching(install).onFailure { context.logger.warn("Widget stacks: $part is unavailable on this launcher", it) }
    }

    /** Runs [work] on the UI thread and waits for its answer, for settings, which run on it too. */
    private fun <T> onMain(work: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return work()
        val latch = java.util.concurrent.CountDownLatch(1)
        var answer: T? = null
        main.post {
            answer = work()
            latch.countDown()
        }
        latch.await(MAIN_WAIT_S, java.util.concurrent.TimeUnit.SECONDS)
        return answer
    }

    /** A no-argument function that runs [run] and returns nothing. */
    private fun action(run: () -> Unit) = InvocationHandler { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "ple_widget_stack"
            else -> {
                run()
                null
            }
        }
    }

    private fun close(popup: View) {
        Reflect.method(popup.javaClass, "close", Boolean::class.javaPrimitiveType!!)?.invoke(popup, true)
    }

    private companion object {
        const val HOST_VIEW = "com.android.launcher3.widget.LauncherAppWidgetHostView"
        const val CELL_LAYOUT = "com.android.launcher3.CellLayout"
        const val LAUNCHER = "com.android.launcher3.Launcher"

        // TalkBack action ids, clear of the framework's own and the launcher's.
        const val ACTION_NEXT = 0x7e570001
        const val ACTION_PREVIOUS = 0x7e570002
        const val ACTION_EDIT = 0x7e570003

        /** Smart rotate leaves a stack alone this long after the person chose its widget. */
        const val CHOSEN_GRACE_MS = 2 * 60 * 60 * 1000L

        /** A widget chosen this recently is not taken back to the first one. */
        const val RECENT_MS = 60 * 1000L
        const val MAIN_WAIT_S = 5L
    }
}
