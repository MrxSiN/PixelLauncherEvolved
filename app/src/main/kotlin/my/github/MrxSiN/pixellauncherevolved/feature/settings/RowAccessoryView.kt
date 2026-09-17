package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * Puts a [RowAccessory] into a bound row's widget frame, or takes one out.
 *
 * `android:id/widget_frame` is where a preference row keeps its trailing
 * widget. A plain row, which is what every row carrying an accessory is, leaves
 * it empty and hidden. The list hands one view on from row to row, so a view
 * given an accessory has to be handed back empty when a row without one reuses
 * it, and a view that already carries a widget of the launcher's own is never
 * touched.
 */
internal object RowAccessoryView {

    fun applyTo(row: View, accessory: RowAccessory?) {
        val frame = row.findViewById<View>(android.R.id.widget_frame) as? ViewGroup ?: return
        val slot = frame.getTag(R.id.ple_row_accessory) as? Slot

        if (accessory == null) {
            slot?.let { clear(frame, it) }
            return
        }
        if (slot == null && frame.childCount > 0) return

        val view = slot?.view?.takeIf { accessory.isDrawnBy(it) } ?: create(frame.context, accessory).also { view ->
            slot?.let { frame.removeView(it.view) }
            frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            frame.setTag(R.id.ple_row_accessory, Slot(view, slot?.visibility ?: frame.visibility))
        }
        frame.visibility = View.VISIBLE
        bind(view, accessory)
    }

    private fun clear(frame: ViewGroup, slot: Slot) {
        frame.removeView(slot.view)
        frame.visibility = slot.visibility
        frame.setTag(R.id.ple_row_accessory, null)
    }

    private fun RowAccessory.isDrawnBy(view: View): Boolean = when (this) {
        is RowAccessory.Radio -> view is ExpressiveRadio
        is RowAccessory.Status -> view is TextView
    }

    private fun create(context: Context, accessory: RowAccessory): View = when (accessory) {
        is RowAccessory.Radio -> ExpressiveRadio(context)
        is RowAccessory.Status -> TextView(context).apply {
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
            maxLines = 1
        }
    }

    private fun bind(view: View, accessory: RowAccessory) {
        when (accessory) {
            is RowAccessory.Radio -> (view as ExpressiveRadio).let { radio ->
                // Animated only when the choice moves under a row already on
                // screen; a row scrolled into view arrives already settled.
                radio.setChecked(accessory.checked, animate = radio.isAttachedToWindow && radio.checked != accessory.checked)
            }
            is RowAccessory.Status -> (view as TextView).apply {
                text = accessory.text
                setTextColor(accessory.tone.role.of(context))
            }
        }
    }

    private val RowAccessory.Tone.role: ExpressiveRole
        get() = when (this) {
            RowAccessory.Tone.NEUTRAL -> ExpressiveRole.ON_SURFACE_VARIANT
            RowAccessory.Tone.POSITIVE -> ExpressiveRole.PRIMARY
            RowAccessory.Tone.WARNING -> ExpressiveRole.TERTIARY
            RowAccessory.Tone.ERROR -> ExpressiveRole.ERROR
        }

    /** An empty frame's own visibility, kept to give the frame back as it was. */
    private class Slot(val view: View, val visibility: Int)
}
