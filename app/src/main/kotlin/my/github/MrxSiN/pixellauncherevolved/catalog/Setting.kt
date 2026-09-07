package my.github.MrxSiN.pixellauncherevolved.catalog

/**
 * One stored preference.
 *
 * A setting is the only thing the hook side and the settings section share: a
 * feature reads it, the section writes it, and neither knows about the other.
 */
sealed interface Setting<T : Any> {
    val key: String
    val default: T
}

data class BoolSetting(
    override val key: String,
    override val default: Boolean,
) : Setting<Boolean>
