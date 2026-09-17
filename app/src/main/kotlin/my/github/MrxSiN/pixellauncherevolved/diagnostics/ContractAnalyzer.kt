package my.github.MrxSiN.pixellauncherevolved.diagnostics

/**
 * Checks each [LauncherContract] against the launcher that is actually running.
 *
 * Every lookup walks the class and its superclasses, declared members included,
 * because that is how the tweaks themselves look members up: a member the
 * launcher declares privately on a base class is one a tweak can reach.
 *
 * @param load the launcher's class by binary name, or null when it has none.
 */
class ContractAnalyzer(private val load: (String) -> Class<*>?) {

    fun analyze(contracts: List<LauncherContract>): ContractReport =
        ContractReport(contracts.map(::check))

    private fun check(contract: LauncherContract): ContractResult {
        if (find(contract.signature)) return ContractResult(contract, ContractOutcome.RESOLVED)

        contract.alternatives.firstOrNull(::find)?.let { alternative ->
            return ContractResult(contract, ContractOutcome.FALLBACK, alternative)
        }

        val outcome = if (load(contract.signature.owner) == null) {
            ContractOutcome.MISSING_CLASS
        } else {
            ContractOutcome.MISSING_MEMBER
        }
        return ContractResult(contract, outcome)
    }

    private fun find(signature: Signature): Boolean {
        val type = load(signature.owner) ?: return false
        val member = signature.member ?: return true

        return runCatching { hierarchy(type).any { declares(it, member) } }.getOrDefault(false)
    }

    private fun declares(type: Class<*>, member: Member): Boolean = when (member) {
        is Member.Field -> type.declaredFields.any { it.name == member.name }
        is Member.Method -> type.declaredMethods.any { method ->
            method.name == member.name &&
                (member.parameters == null || method.parameterTypes.map(Class<*>::getName) == member.parameters)
        }
    }

    private fun hierarchy(type: Class<*>): Sequence<Class<*>> = generateSequence(type) { it.superclass }
}

enum class ContractOutcome { RESOLVED, FALLBACK, MISSING_MEMBER, MISSING_CLASS }

data class ContractResult(
    val contract: LauncherContract,
    val outcome: ContractOutcome,
    /** The alternative that met the contract, for [ContractOutcome.FALLBACK]. */
    val alternative: Signature? = null,
) {
    val isMet: Boolean get() = outcome == ContractOutcome.RESOLVED || outcome == ContractOutcome.FALLBACK
}

/** What the analyzer found, and what that means for each tweak. */
class ContractReport(val results: List<ContractResult>) {

    val total: Int get() = results.size
    val resolved: Int get() = results.count(ContractResult::isMet)
    val fallbacks: Int get() = results.count { it.outcome == ContractOutcome.FALLBACK }
    val failed: Int get() = total - resolved

    /** Incompatible when any member the tweak requires is missing. */
    fun isCompatible(feature: CompatibilityFeature): Boolean =
        results.none { feature in it.contract.features && it.contract.isRequired && !it.isMet }

    /** The report as text, for a log or a bug report. */
    fun format(launcherVersion: String): String = buildString {
        appendLine("Pixel Launcher versionCode: $launcherVersion")
        appendLine("Resolved $resolved / $total, fallback signatures $fallbacks, failed $failed")
        appendLine()
        appendLine("Feature compatibility")
        for (feature in CompatibilityFeature.entries) {
            appendLine("${if (isCompatible(feature)) PASS else FAIL} ${feature.name}")
        }
        appendLine()
        appendLine("Contracts")
        for (result in results) {
            appendLine("${result.mark} ${result.contract.signature.describe()}")
            when (result.outcome) {
                ContractOutcome.RESOLVED -> Unit
                ContractOutcome.FALLBACK -> {
                    appendLine("  Missing")
                    appendLine("  Alternative found:")
                    appendLine("  ${result.alternative?.describe()}")
                }
                ContractOutcome.MISSING_MEMBER -> appendLine("  Missing${if (result.contract.isRequired) "" else " (optional)"}")
                ContractOutcome.MISSING_CLASS -> appendLine("  Class missing${if (result.contract.isRequired) "" else " (optional)"}")
            }
        }
    }.trimEnd()

    private val ContractResult.mark: String
        get() = when (outcome) {
            ContractOutcome.RESOLVED -> PASS
            ContractOutcome.FALLBACK -> WARN
            else -> FAIL
        }

    private companion object {
        const val PASS = "✓"
        const val WARN = "⚠"
        const val FAIL = "✗"
    }
}
