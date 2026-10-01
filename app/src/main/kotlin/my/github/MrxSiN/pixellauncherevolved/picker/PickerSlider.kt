package my.github.MrxSiN.pixellauncherevolved.picker

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.Xml
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.SeekBar

import org.xmlpull.v1.XmlPullParser

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * A stepped slider drawn as Wallpaper & style draws its own.
 *
 * ```
 * com.google.android.material.slider.Slider(Context, AttributeSet)
 * BaseSlider: float valueFrom, valueTo, stepSize; boolean dirtyConfig;
 *   List changeListeners (BaseOnChangeListener.onValueChange(BaseSlider, float, boolean)),
 *   List touchListeners (BaseOnSliderTouchListener.onStart/StopTrackingTouch(BaseSlider)),
 *   ColorStateList trackColorActive/Inactive, tickColorActive/Inactive;
 *   setThumbTintList(ColorStateList), setValuesInternal(ArrayList), getValues()
 * layout/floating_sheet_clock_style_content: the clock's size slider
 * ```
 *
 * The picker's own Material 3 slider is built from the attributes of the
 * clock's slider in the picker's own layout (its theme, track and thumb
 * height, no value label), so it matches whatever the picker ships, and is
 * coloured as the picker's `SliderColorBinder` colours that one. The picker's
 * build keeps the slider's fields but not its setters, so the range, value and
 * listeners are written to them. Where any of that is missing, a platform
 * [SeekBar] stands in.
 *
 * [moved] hears every step and whether a finger is on it; [released] hears a
 * pick once it is made: the finger lifted, or a step from a keyboard or
 * TalkBack.
 */
internal class PickerSlider private constructor(val view: View, private val setStep: (Int) -> Unit) {

    /** Puts the slider on [step] without telling anyone, as for a refused pick. */
    fun set(step: Int) = setStep(step)

    companion object {

        fun create(
            context: Context,
            classLoader: ClassLoader,
            colors: PickerColors,
            logger: Logger,
            range: IntRange,
            current: Int,
            moved: (step: Int, dragging: Boolean) -> Unit,
            released: (step: Int) -> Unit,
        ): PickerSlider {
            var tracking = false
            fun changed(step: Int, fromUser: Boolean) {
                moved(step, fromUser && tracking)
                if (fromUser && !tracking) released(step)
            }
            return runCatching { material(context, classLoader, colors, range, current.coerceIn(range), ::changed, { tracking = it }, released) }
                .onFailure { logger.warn("Picker: the picker's slider is unavailable; a platform one stands in", it) }
                .getOrNull()
                ?: platform(context, colors, range, current.coerceIn(range), ::changed, { tracking = it }, released)
        }

        private fun material(
            context: Context,
            classLoader: ClassLoader,
            colors: PickerColors,
            range: IntRange,
            current: Int,
            changed: (Int, Boolean) -> Unit,
            touching: (Boolean) -> Unit,
            released: (Int) -> Unit,
        ): PickerSlider {
            val sliderClass = Class.forName(SLIDER, false, classLoader)
            val base = Class.forName(BASE_SLIDER, false, classLoader)
            val layout = PickerPage.resource(context, "layout", ATTRIBUTES_LAYOUT)
            require(layout != 0) { "no $ATTRIBUTES_LAYOUT" }
            val parser = context.resources.getLayout(layout)
            val slider = try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == SLIDER) break
                }
                require(parser.eventType == XmlPullParser.START_TAG) { "no slider in $ATTRIBUTES_LAYOUT" }
                val attributes = Xml.asAttributeSet(parser)
                // android:theme is applied by the inflater, not by the view.
                val theme = attributes.getAttributeResourceValue(ANDROID, "theme", 0)
                val themed = if (theme != 0) ContextThemeWrapper(context, theme) else context
                sliderClass.getConstructor(Context::class.java, AttributeSet::class.java).newInstance(themed, attributes) as View
            } finally {
                parser.close()
            }
            slider.id = View.NO_ID

            fun field(name: String) = base.getDeclaredField(name).apply { isAccessible = true }
            field("valueFrom").setFloat(slider, range.first.toFloat())
            field("valueTo").setFloat(slider, range.last.toFloat())
            field("stepSize").setFloat(slider, 1f)
            field("dirtyConfig").setBoolean(slider, true)
            val setValues = base.getMethod("setValuesInternal", ArrayList::class.java)
            val getValues = base.getMethod("getValues")
            fun set(step: Int) {
                setValues.invoke(slider, arrayListOf(step.toFloat()))
            }
            set(current)

            val trackActive = field("trackColorActive")
            val trackInactive = field("trackColorInactive")
            val tickActive = field("tickColorActive")
            val tickInactive = field("tickColorInactive")
            val thumbTint = base.getMethod("setThumbTintList", ColorStateList::class.java)
            colors.paint(slider, "colorPrimary") { color ->
                val tint = ColorStateList.valueOf(color)
                trackActive.set(slider, tint)
                tickInactive.set(slider, tint)
                thumbTint.invoke(slider, tint)
                slider.refreshDrawableState()
                slider.invalidate()
            }
            colors.paint(slider, "colorSurfaceContainerHighest") { color ->
                val tint = ColorStateList.valueOf(color)
                trackInactive.set(slider, tint)
                tickActive.set(slider, tint)
                slider.refreshDrawableState()
                slider.invalidate()
            }

            fun step(): Int = ((getValues.invoke(slider) as List<*>).firstOrNull() as? Float)?.toInt() ?: current
            @Suppress("UNCHECKED_CAST")
            (field("changeListeners").get(slider) as MutableList<Any>) += listener(classLoader, BASE_CHANGE) { method, args ->
                if (method == "onValueChange") changed((args[1] as Float).toInt(), args[2] as Boolean)
            }
            @Suppress("UNCHECKED_CAST")
            (field("touchListeners").get(slider) as MutableList<Any>) += listener(classLoader, BASE_TOUCH) { method, _ ->
                when (method) {
                    "onStartTrackingTouch" -> touching(true)
                    "onStopTrackingTouch" -> {
                        touching(false)
                        released(step())
                    }
                }
            }
            return PickerSlider(slider, ::set)
        }

        /** One of the picker's slider listener interfaces, handing its calls to [body]. */
        private fun listener(classLoader: ClassLoader, name: String, body: (String, Array<Any?>) -> Unit): Any =
            pickerProxy(Class.forName(name, false, classLoader)) { method, args -> body(method, args); null }

        private fun platform(
            context: Context,
            colors: PickerColors,
            range: IntRange,
            current: Int,
            changed: (Int, Boolean) -> Unit,
            touching: (Boolean) -> Unit,
            released: (Int) -> Unit,
        ): PickerSlider {
            val bar = SeekBar(context, null, 0, android.R.style.Widget_Material_SeekBar_Discrete)
            bar.min = range.first
            bar.max = range.last
            bar.progress = current
            colors.paint(bar, "colorPrimary") { color ->
                bar.progressTintList = ColorStateList.valueOf(color)
                bar.thumbTintList = ColorStateList.valueOf(color)
            }
            colors.paint(bar, "colorSurfaceContainerHighest") { color ->
                bar.progressBackgroundTintList = ColorStateList.valueOf(color)
                bar.tickMarkTintList = ColorStateList.valueOf(color)
            }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = changed(progress, fromUser)

                override fun onStartTrackingTouch(seekBar: SeekBar) = touching(true)

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    touching(false)
                    released(seekBar.progress)
                }
            })
            return PickerSlider(bar) { bar.progress = it }
        }

        private const val ANDROID = "http://schemas.android.com/apk/res/android"
        private const val ATTRIBUTES_LAYOUT = "floating_sheet_clock_style_content"
        private const val SLIDER = "com.google.android.material.slider.Slider"
        private const val BASE_SLIDER = "com.google.android.material.slider.BaseSlider"
        private const val BASE_CHANGE = "com.google.android.material.slider.BaseOnChangeListener"
        private const val BASE_TOUCH = "com.google.android.material.slider.BaseOnSliderTouchListener"
    }
}
