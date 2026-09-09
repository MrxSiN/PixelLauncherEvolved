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
    private val slider = load(classLoader, "androidx.preference.SeekBarPreference")
    private val twoState = load(classLoader, "androidx.preference.TwoStatePreference")
    private val fragment = load(classLoader, "androidx.preference.PreferenceFragmentCompat")

    private val preferenceConstructor = preference?.let(::styledConstructor)
    private val categoryConstructor = category?.let(::styledConstructor)
    private val screenConstructor = screen?.let(::styledConstructor)
    private val switchConstructor = switch?.let(::styledConstructor)
    private val sliderConstructor = slider?.let(::styledConstructor)
    private val setSliderValue = slider?.let(::valueSetter)

    /**
     * `onBindViewHolder`, and the `SeekBar` a bind puts in the row.
     *
     * The override keeps its name where every other member of the class lost
     * one, because it overrides a method of `Preference`, which is not renamed.
     * The view behind it is found by its type: there is one field of it.
     */
    val sliderBind: java.lang.reflect.Method? =
        slider?.declaredMethods?.singleOrNull { it.name == "onBindViewHolder" }
    private val sliderViewField = slider?.declaredFields?.singleOrNull {
        it.type == android.widget.SeekBar::class.java
    }?.apply { isAccessible = true }

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
    private val enabledField = preference?.let { Reflect.field(it, "mEnabled") }
    private val iconSpaceField = preference?.let { Reflect.field(it, "mIconSpaceReserved") }
    private val keyField = preference?.let { Reflect.field(it, "mKey") }
    private val notifyChanged = preference?.let { Reflect.method(it, "notifyChanged") }
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

    /**
     * Whether this build can draw a slider row.
     *
     * Separate from [isUsable] because a launcher that has shrunk this one
     * class away should lose one row rather than the whole section.
     */
    val hasSlider: Boolean =
        sliderConstructor != null && setSliderValue != null &&
            enabledField != null && notifyChanged != null

    /** The bar a bound slider row draws, for a row that wants restyling. */
    fun sliderViewOf(row: Any): android.widget.SeekBar? =
        sliderViewField?.get(row) as? android.widget.SeekBar

    /** A row's key, which is what says whether it is one of this module's. */
    fun keyOf(row: Any): String? = keyField?.get(row) as? String

    /** The themed context a preference was built with, which new rows must share. */
    fun contextOf(preference: Any): Context = contextField!!.get(preference) as Context

    fun createCategory(context: Context, title: CharSequence): Any =
        categoryConstructor!!.newInstance(context, null).also { setTitle!!.invoke(it, title) }

    /** A row that opens a nested page within the launcher's settings activity. */
    fun createScreen(
        context: Context,
        key: String,
        title: CharSequence,
        summary: CharSequence,
    ): Any = screenConstructor!!.newInstance(context, null).also { row ->
        setKey!!.invoke(row, key)
        setTitle!!.invoke(row, title)
        setSummary!!.invoke(row, summary)
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

    /**
     * A row carrying a slider from 0 to 100.
     *
     * Those bounds are not set here: they are what
     * `SeekBarPreference(Context, null)` builds itself, reading `min` with a
     * default of 0 and `android:max` with a default of 100 from an attribute
     * set that is not there. The setters for them did not survive this
     * launcher's shrinker — every member of this one class is renamed — so a
     * caller's range has to be the same 0..100.
     *
     * The value is set through the only method the class declares that takes a
     * number and a flag and returns nothing, which is recognised by that shape
     * rather than by its rewritten name.
     */
    fun createSlider(
        context: Context,
        key: String,
        title: CharSequence,
        summary: CharSequence,
        value: Int,
        isEnabled: Boolean,
        onChange: (Int) -> Unit,
    ): Any = sliderConstructor!!.newInstance(context, null).also { row ->
        setKey!!.invoke(row, key)
        setTitle!!.invoke(row, title)
        setSummary!!.invoke(row, summary)
        persistentField!!.setBoolean(row, false)
        enabledField!!.setBoolean(row, isEnabled)
        // The theme's slider style reserves room for an icon, which no row in
        // this section has. Left reserved, the slider alone sits 56dp right of
        // every switch above and below it.
        iconSpaceField?.setBoolean(row, false)
        setSliderValue!!.invoke(row, value, false)
        changeListenerField!!.set(
            row,
            listener(changeListenerField.type) { args ->
                args.filterIsInstance<Int>().firstOrNull()?.let(onChange)
            },
        )
    }

    /**
     * Greys a row out, for one that only means something while another is on.
     *
     * `setEnabled` did not survive the shrinker, so the field behind it is
     * written and the row is asked to draw itself again.
     */
    fun setEnabled(row: Any, isEnabled: Boolean) {
        if (enabledField == null || notifyChanged == null) return
        if (enabledField.getBoolean(row) == isEnabled) return

        enabledField.setBoolean(row, isEnabled)
        notifyChanged.invoke(row)
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

    /** Rewrites a row's line, for one that reports a choice made in a dialog. */
    fun setSummary(row: Any, summary: CharSequence) {
        setSummary!!.invoke(row, summary)
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
         * `setValueInternal(int, boolean)`, found by shape.
         *
         * It is the only method the class declares that takes a number and a
         * flag and returns nothing. Its name is written by the launcher's
         * shrinker, and so is the name of every field it touches, so nothing
         * here can be asked for by name.
         */
        fun valueSetter(type: Class<*>) = type.declaredMethods.singleOrNull {
            it.returnType == Void.TYPE &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.apply { isAccessible = true }

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
