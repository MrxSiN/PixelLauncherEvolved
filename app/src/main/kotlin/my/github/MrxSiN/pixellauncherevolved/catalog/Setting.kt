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

/**
 * A number within a range.
 *
 * The range belongs to the setting rather than to the row that shows it, so a
 * value read back is inside it whoever wrote it.
 */
data class IntSetting(
    override val key: String,
    override val default: Int,
    val range: IntRange,
) : Setting<Int>
