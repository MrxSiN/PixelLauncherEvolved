package my.github.MrxSiN.pixellauncherevolved.diagnostics

/**
 * One member of the launcher a tweak cannot work without.
 *
 * Kept free of Android and of any launcher class, so the list can be read and
 * checked on a desk as well as inside the launcher.
 */
data class LauncherContract(
    val signature: Signature,
    val features: Set<CompatibilityFeature>,
    /**
     * What the module falls back to when [signature] is missing, in the order
     * it tries them. A contract met by one of these still works, on an older or
     * newer launcher than the one the module prefers.
     */
    val alternatives: List<Signature> = emptyList(),
    /**
     * False for a member the tweak reports and skips when it is missing. Its
     * absence is shown, but does not make the tweak incompatible.
     */
    val isRequired: Boolean = true,
)

/** A class, or a member of one, named as the launcher's dex names it. */
data class Signature(
    /** The binary class name, with `$` before a nested class. */
    val owner: String,
    val member: Member? = null,
) {

    /** `TaskView.onLayout(boolean, int, int, int, int)`, the way a person reads it. */
    fun describe(): String {
        val type = owner.substringAfterLast('.')
        return member?.let { "$type.${it.describe()}" } ?: type
    }
}

sealed interface Member {

    val name: String

    fun describe(): String

    /**
     * A method, matched by name alone or by its parameters too.
     *
     * Null parameters match any overload. The module finds some methods by
     * shape rather than by signature, because the shrinker rewrites their
     * parameter types between releases; those are checked the same way here.
     */
    data class Method(override val name: String, val parameters: List<String>? = null) : Member {
        override fun describe(): String =
            parameters?.joinToString(prefix = "$name(", postfix = ")") { it.substringAfterLast('.') } ?: name
    }

    data class Field(override val name: String) : Member {
        override fun describe(): String = name
    }
}
