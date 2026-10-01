package my.github.MrxSiN.pixellauncherevolved.picker

import android.content.Context
import android.content.res.Configuration

import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Wallpaper & style's own colours for the views this module shows in it.
 *
 * The picker keeps its views in the colours of the theme being previewed and
 * changes them as that theme changes, through `ColorUpdateBinder` and the
 * fragment's `colorUpdateViewModel`. Views painted here follow the same flows,
 * so they change colour with the picker's own. Where those cannot be reached
 * the system's dynamic palette stands in, as Material 3 would colour them.
 *
 * One instance serves one fragment of the picker.
 */
internal class PickerColors(
    private val context: Context,
    classLoader: ClassLoader,
    /** The picker's `ColorUpdateViewModel`, or null to use the system palette. */
    private val viewModel: Any?,
    /** The lifecycle the colours are followed for. */
    private val lifecycleOwner: Any,
    private val logger: Logger,
) {

    /** For one of the picker's fragments, which holds both. */
    constructor(context: Context, classLoader: ClassLoader, fragment: Any, logger: Logger) : this(
        context,
        classLoader,
        kotlin.runCatching { field(fragment, "colorUpdateViewModel") }.getOrNull(),
        kotlin.runCatching { fragment.javaClass.getMethod("getViewLifecycleOwner").invoke(fragment) }.getOrNull() ?: fragment,
        logger,
    )

    private val binder: Method? = runCatching {
        Class.forName(COLOR_BINDER, false, classLoader).declaredMethods.first { it.name == "bind" && it.parameterCount == 4 }
    }.getOrNull()

    private class Tone(var color: Int? = null) {
        val painters: MutableMap<Any, (Int) -> Unit> = Collections.synchronizedMap(WeakHashMap())
    }

    private val tones = HashMap<String, Tone>()

    /** Calls [apply] with the theme colour [name] now and as it changes, while [owner] lives. */
    fun paint(owner: Any, name: String, apply: (Int) -> Unit) {
        val tone = tones.getOrPut(name) { Tone().also { follow(name, it) } }
        tone.painters[owner] = apply
        (tone.color ?: fallback(name))?.let(apply)
    }

    /** The colour [name] as it stands now. */
    fun now(name: String): Int = tones[name]?.color ?: fallback(name) ?: 0

    private fun follow(name: String, tone: Tone) {
        val bind = binder ?: return
        val model = viewModel ?: return
        runCatching {
            val flow = field(model, name)
            val set = function(bind.parameterTypes[0]) { args ->
                val color = args[0] as Int
                tone.color = color
                synchronized(tone.painters) { tone.painters.values.toList() }.forEach { it(color) }
            }
            bind.invoke(null, set, flow, animate(bind.parameterTypes[2]), lifecycleOwner)
        }.onFailure { logger.warn("Picker: the colour $name cannot be followed; using the system palette", it) }
    }

    /** Material 3's roles from the system's dynamic palette, for a picker whose own cannot be reached. */
    private fun fallback(name: String): Int? {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val id = when (name) {
            "colorPrimary" -> if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
            "colorOnPrimary" -> if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_0
            "colorPrimaryContainer" -> if (dark) android.R.color.system_accent1_700 else android.R.color.system_accent1_100
            "colorOnPrimaryContainer" -> if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_900
            "colorOnSurface" -> if (dark) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900
            "colorOnSurfaceVariant" -> if (dark) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700
            "colorOutline" -> if (dark) android.R.color.system_neutral2_400 else android.R.color.system_neutral2_500
            "colorSurfaceContainer" -> if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
            "colorSurfaceContainerHigh" -> if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_100
            "colorSurfaceContainerHighest" -> if (dark) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_200
            "colorSurfaceBright" -> if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_10
            else -> return null
        }
        return context.getColor(id)
    }

    /** A Kotlin function of [type] that runs [body]. */
    private fun function(type: Class<*>, body: (Array<Any?>) -> Unit): Any =
        pickerProxy(type) { name, args -> if (name == "invoke") body(args); null }

    /** A Kotlin `() -> Boolean` that asks the binder to animate. */
    private fun animate(type: Class<*>): Any = pickerProxy(type) { name, _ -> if (name == "invoke") true else null }

    private companion object {
        fun field(owner: Any, name: String): Any =
            generateSequence(owner.javaClass as Class<*>?) { it.superclass }
                .firstNotNullOfOrNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
                ?.apply { isAccessible = true }
                ?.get(owner)
                ?: throw NoSuchFieldException(name)

        const val COLOR_BINDER = "com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder"
    }
}
