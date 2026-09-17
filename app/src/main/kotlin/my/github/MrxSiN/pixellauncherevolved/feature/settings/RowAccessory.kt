package my.github.MrxSiN.pixellauncherevolved.feature.settings

import java.util.concurrent.ConcurrentHashMap

/**
 * What a settings row shows at its end, in place of a switch.
 *
 * A switch is the launcher's own widget and draws itself. Everything else a
 * row of this module ends with is drawn here: a radio button says the row is
 * one answer of several, and a status says what was found.
 *
 * A row that opens another screen ends with nothing. Android 17 QPR1 Settings
 * draws no chevron on those; the title and summary are the whole row.
 */
internal sealed interface RowAccessory {

    data class Radio(val checked: Boolean) : RowAccessory

    data class Status(val text: CharSequence, val tone: Tone = Tone.NEUTRAL) : RowAccessory

    enum class Tone { NEUTRAL, POSITIVE, WARNING, ERROR }
}

/**
 * The accessory each of this module's rows asks for, by row key.
 *
 * A row is bound long after it is built, and bound again whenever it is told
 * to redraw, so it is asked for an accessory at bind time rather than handed
 * one when made. A radio button asked this way reads the choice as it stands
 * now, not as it stood when the page opened.
 */
internal class RowAccessories {

    private val providers = ConcurrentHashMap<String, () -> RowAccessory>()

    fun register(key: String, provider: () -> RowAccessory) {
        providers[key] = provider
    }

    fun of(key: String?): RowAccessory? = key?.let(providers::get)?.invoke()
}
