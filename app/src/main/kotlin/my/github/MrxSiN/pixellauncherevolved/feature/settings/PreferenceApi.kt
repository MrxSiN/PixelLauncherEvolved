package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.util.AttributeSet

import java.lang.reflect.Proxy

import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * The launcher's copy of `androidx.preference`, reached by reflection.
 *
 * Home settings is an `androidx.preference` screen, and the only way to add a
 * row to it is to hand it instances of the launcher's own preference classes.
 * This module cannot link against them: its classes are loaded by a different
 * class loader, so a `PreferenceCategory` compiled here would be a different
 * type from the one the screen accepts.
 *
 * The launcher ships a shrunk copy, which is why nothing here calls a setter
 * without checking first. R8 dropped every method the launcher itself never
 * used — `setPersistent` and `setOnPreferenceChangeListener` are both
 * gone — and rewrote the listener interfaces down to the arguments that
 * were actually read. What survives is the fields behind them, so this writes
 * those instead, and it reads the listener types off those fields rather than
 * naming them, because their names are shrunk too.
 *
 * A failed lookup leaves this object unusable rather than half usable:
 * [isUsable] is the one question callers ask.
 */
class PreferenceApi(classLoader: ClassLoader) {

    private val preference = load(classLoader, "androidx.preference.Preference")
    private val group = load(classLoader, "androidx.preference.PreferenceGroup")
    private val category = load(classLoader, "androidx.preference.PreferenceCategory")
    private val screen = load(classLoader, "androidx.preference.PreferenceScreen")
    private val switch = load(classLoader, "androidx.preference.SwitchPreference")
    private val twoState = load(classLoader, "androidx.preference.TwoStatePreference")
    private val fragment = load(classLoader, "androidx.preference.PreferenceFragmentCompat")

    private val preferenceConstructor = preference?.let(::styledConstructor)
    private val categoryConstructor = category?.let(::styledConstructor)
    private val screenConstructor = screen?.let(::styledConstructor)
    private val switchConstructor = switch?.let(::styledConstructor)

    private val setTitle = preference?.let { Reflect.method(it, "setTitle", CharSequence::class.java) }
    private val setSummary = preference?.let { Reflect.method(it, "setSummary", CharSequence::class.java) }
    private val setKey = preference?.let { Reflect.method(it, "setKey", String::class.java) }
    private val addPreference = group?.let { owner ->
        preference?.let { Reflect.method(owner, "addPreference", it) }
    }
    private val setChecked = twoState?.let {
        Reflect.method(it, "setChecked", Boolean::class.javaPrimitiveType!!)
    }
    private val setPreferenceScreen = fragment?.let { owner ->
        screen?.let { Reflect.method(owner, "setPreferenceScreen", it) }
    }

    private val contextField = preference?.let { Reflect.field(it, "mContext") }
    private val persistentField = preference?.let { Reflect.field(it, "mPersistent") }
    private val changeListenerField = preference?.let { Reflect.field(it, "mOnChangeListener") }
    private val clickListenerField = preference?.let { Reflect.field(it, "mOnClickListener") }
    private val preferenceManagerField = fragment?.let { Reflect.field(it, "mPreferenceManager") }
    private val attachToHierarchy = preference?.let { owner ->
        preferenceManagerField?.let { Reflect.method(owner, "onAttachedToHierarchy", it.type) }
    }

    val isUsable: Boolean =
        preferenceConstructor != null && categoryConstructor != null && screenConstructor != null &&
            switchConstructor != null &&
            setTitle != null && setSummary != null && setKey != null &&
            addPreference != null && setChecked != null && setPreferenceScreen != null &&
            contextField != null && persistentField != null &&
            changeListenerField != null && clickListenerField != null &&
            preferenceManagerField != null && attachToHierarchy != null

    /** The themed context a preference was built with, which new rows must share. */
    fun contextOf(preference: Any): Context = contextField!!.get(preference) as Context

    fun createCategory(context: Context, title: CharSequence): Any =
        categoryConstructor!!.newInstance(context, null).also { setTitle!!.invoke(it, title) }

    /** A row that opens a nested page within the launcher's settings activity. */
    fun createScreen(context: Context, key: String, title: CharSequence): Any =
        screenConstructor!!.newInstance(context, null).also { row ->
            setKey!!.invoke(row, key)
            setTitle!!.invoke(row, title)
            persistentField!!.setBoolean(row, false)
        }

    /** Builds and then installs one of this module's own pages atomically. */
    fun showRootScreen(fragment: Any, populate: (Any) -> Unit) {
        val context = Reflect.method(fragment.javaClass, "requireContext")!!.invoke(fragment) as Context
        val root = screenConstructor!!.newInstance(context, null)
        attachToHierarchy!!.invoke(root, preferenceManagerField!!.get(fragment))
        populate(root)
        setPreferenceScreen!!.invoke(fragment, root)
    }

    /**
     * A row that reports its own value rather than storing one.
     *
     * Settings live in this module's own file, so every row here is built
     * non-persistent: left persistent, the switch would also write its value
     * into the launcher's preferences, where nothing reads it.
     */
    fun createSwitch(
        context: Context,
        key: String,
        title: CharSequence,
        summary: CharSequence,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): Any = switchConstructor!!.newInstance(context, null).also { row ->
        setKey!!.invoke(row, key)
        setTitle!!.invoke(row, title)
        setSummary!!.invoke(row, summary)
        persistentField!!.setBoolean(row, false)
        setChecked!!.invoke(row, checked)
        changeListenerField!!.set(
            row,
            listener(changeListenerField.type) { args ->
                onChange(args.filterIsInstance<Boolean>().firstOrNull() ?: !checked)
            },
        )
    }

    fun createAction(
        context: Context,
        key: String,
        title: CharSequence,
        summary: CharSequence,
        onClick: () -> Unit,
    ): Any = preferenceConstructor!!.newInstance(context, null).also { row ->
        setKey!!.invoke(row, key)
        setTitle!!.invoke(row, title)
        setSummary!!.invoke(row, summary)
        persistentField!!.setBoolean(row, false)
        clickListenerField!!.set(row, listener(clickListenerField.type) { onClick() })
    }

    /** Moves a switch that something else turned off. Its listener is not called. */
    fun setChecked(row: Any, checked: Boolean) {
        setChecked!!.invoke(row, checked)
    }

    fun add(group: Any, preference: Any) {
        addPreference!!.invoke(group, preference)
    }

    /**
     * A listener of whatever shape this build shrank the interface to.
     *
     * The interface carries one method and its name is not stable, so the call
     * is recognised by elimination rather than by name. A change listener that
     * answers false vetoes the change it was told about, so a boolean answer is
     * always true.
     */
    private fun listener(type: Class<*>, onCall: (List<Any?>) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            val arguments = args?.toList().orEmpty()
            when (method.name) {
                "equals" -> proxy === arguments.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PixelLauncherEvolved listener"
                else -> {
                    onCall(arguments)
                    if (method.returnType == Boolean::class.javaPrimitiveType) true else null
                }
            }
        }

    private companion object {

        fun load(classLoader: ClassLoader, name: String): Class<*>? =
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()

        /**
         * The two-argument constructor, which is what applies the theme.
         *
         * `Preference(Context)` delegates to it with a null attribute set, and
         * the shrunk copy no longer carries that delegate, so the null is
         * passed here instead. The style still comes from the theme, which is
         * what makes an added row look like the launcher's own.
         */
        fun styledConstructor(type: Class<*>) = runCatching {
            type.getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
                .apply { isAccessible = true }
        }.getOrNull()
    }
}
