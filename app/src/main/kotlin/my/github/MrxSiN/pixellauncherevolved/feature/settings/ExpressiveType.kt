package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/**
 * The Material 3 type scale, set in the faces Android 17 settings uses.
 *
 * Google Sans for headlines and Google Sans Text for everything read at body
 * size. A device without them gets the default sans-serif, which is the same
 * scale in a plainer face rather than a broken screen.
 */
enum class ExpressiveType(private val family: String, private val sizeSp: Float) {
    HEADLINE_SMALL("google-sans", 24f),
    TITLE_LARGE("google-sans", 22f),
    BODY_LARGE("google-sans-text", 16f),
    TITLE_MEDIUM("google-sans-text", 16f),
    BODY_MEDIUM("google-sans-text", 14f),
    LABEL_LARGE("google-sans-text-medium", 14f),
    LABEL_MEDIUM("google-sans-text-medium", 12f);

    fun applyTo(view: TextView, role: ExpressiveRole) {
        view.typeface = Typeface.create(family, Typeface.NORMAL)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        view.setTextColor(role.of(view.context))
    }
}
