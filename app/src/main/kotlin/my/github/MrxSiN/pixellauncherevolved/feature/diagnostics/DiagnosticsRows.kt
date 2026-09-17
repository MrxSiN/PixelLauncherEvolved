package my.github.MrxSiN.pixellauncherevolved.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.diagnostics.ContractOutcome
import my.github.MrxSiN.pixellauncherevolved.diagnostics.ContractReport
import my.github.MrxSiN.pixellauncherevolved.diagnostics.ContractResult
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.settings.RowAccessory
import my.github.MrxSiN.pixellauncherevolved.feature.settings.RowAccessory.Tone
import my.github.MrxSiN.pixellauncherevolved.feature.settings.RowScope
import my.github.MrxSiN.pixellauncherevolved.feature.settings.SettingsKeys
import my.github.MrxSiN.pixellauncherevolved.feature.settings.SettingsRow
import my.github.MrxSiN.pixellauncherevolved.focus.ProviderFocusSource

/**
 * The Compatibility & diagnostics page: what is running, and what of the
 * launcher each tweak found.
 *
 * Everything here is read when the page opens, from the launcher that is
 * running, so after a monthly update it says what that update changed rather
 * than what the module was written against.
 */
internal object DiagnosticsRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val analysis = scope.environment.analysis()
        val section = Section(group, scope)

        section.heading(R.string.diagnostics_group_module)
        section.value("module", R.string.diagnostics_module, scope.string(R.string.diagnostics_active), Tone.POSITIVE)
        section.value("module_version", R.string.diagnostics_version, moduleVersion(scope))

        section.heading(R.string.diagnostics_group_system)
        section.value("android", R.string.diagnostics_android, androidVersion())
        section.value("build", R.string.diagnostics_build, Build.ID)
        section.value("launcher", R.string.diagnostics_launcher, analysis.launcherVersion)
        section.value("xposed", R.string.diagnostics_xposed_api, scope.environment.xposedApiVersion.toString())
        section.value(
            "framework",
            R.string.diagnostics_framework,
            "${scope.environment.frameworkName} ${scope.environment.frameworkVersion}",
        )
        root(section, scope)

        section.heading(R.string.diagnostics_group_features)
        for (feature in CompatibilityFeature.entries) {
            val available = analysis.isAvailable(feature)
            section.value(
                name = "feature_" + feature.name.lowercase(),
                title = feature.titleRes,
                value = scope.string(if (available) R.string.diagnostics_compatible else R.string.diagnostics_incompatible),
                tone = if (available) Tone.POSITIVE else Tone.ERROR,
                summary = scope.string(
                    if (available) R.string.compatibility_supported else R.string.compatibility_unavailable,
                    analysis.launcherVersion,
                ),
            )
        }

        hooks(section, scope, analysis.report)
        contracts(section, scope, analysis.report)

        section.heading(R.string.diagnostics_group_report)
        scope.api.add(
            section.target,
            scope.link(SettingsKeys.row("diagnostics_copy"), R.string.diagnostics_copy_report, scope.string(R.string.diagnostics_copy_report_summary)) {
                copy(scope.context, analysis.text)
            },
        )
    }

    private fun hooks(section: Section, scope: RowScope, report: ContractReport) {
        section.heading(R.string.diagnostics_group_hooks)
        section.value(
            "hooks_resolved",
            R.string.diagnostics_hooks_resolved,
            "${report.resolved} / ${report.total}",
            if (report.failed == 0) Tone.POSITIVE else Tone.WARNING,
        )
        section.value(
            "hooks_fallback",
            R.string.diagnostics_hooks_fallback,
            report.fallbacks.toString(),
            if (report.fallbacks == 0) Tone.NEUTRAL else Tone.WARNING,
        )
        section.value(
            "hooks_failed",
            R.string.diagnostics_hooks_failed,
            report.failed.toString(),
            if (report.failed == 0) Tone.NEUTRAL else Tone.ERROR,
        )
    }

    /** One row per contract, the ones that need attention first. */
    private fun contracts(section: Section, scope: RowScope, report: ContractReport) {
        section.heading(R.string.diagnostics_group_contracts)
        report.results
            .sortedBy { it.outcome == ContractOutcome.RESOLVED }
            .forEachIndexed { index, result ->
                val key = SettingsKeys.row("contract_$index")
                scope.api.add(
                    section.target,
                    scope.api.createAction(scope.context, key, result.contract.signature.describe(), explain(scope, result)) {},
                )
                scope.accessory(key) { mark(result) }
            }
    }

    private fun explain(scope: RowScope, result: ContractResult): String = when (result.outcome) {
        ContractOutcome.RESOLVED -> ""
        ContractOutcome.FALLBACK -> scope.string(R.string.diagnostics_contract_fallback, result.alternative?.describe().orEmpty())
        ContractOutcome.MISSING_MEMBER -> scope.string(
            if (result.contract.isRequired) R.string.diagnostics_contract_missing else R.string.diagnostics_contract_missing_optional,
        )
        ContractOutcome.MISSING_CLASS -> scope.string(R.string.diagnostics_contract_class_missing)
    }

    private fun mark(result: ContractResult): RowAccessory = when {
        result.outcome == ContractOutcome.RESOLVED -> RowAccessory.Status("✓", Tone.POSITIVE)
        result.outcome == ContractOutcome.FALLBACK -> RowAccessory.Status("⚠", Tone.WARNING)
        result.contract.isRequired -> RowAccessory.Status("✗", Tone.ERROR)
        else -> RowAccessory.Status("✗", Tone.WARNING)
    }

    /**
     * Whether this module's app holds root, which Focus home screens and Double
     * tap to sleep need.
     *
     * Asked off the main thread: the answer comes from this module's own app
     * through a root shell, which can take half a second.
     */
    private fun root(section: Section, scope: RowScope) {
        // Written and read on the main thread only.
        var granted: Boolean? = null
        val key = SettingsKeys.row("diagnostics_root")
        val row = scope.api.createAction(scope.context, key, scope.string(R.string.diagnostics_root), "") {}
        scope.accessory(key) {
            when (granted) {
                null -> RowAccessory.Status(scope.string(R.string.diagnostics_checking))
                true -> RowAccessory.Status(scope.string(R.string.diagnostics_granted), Tone.POSITIVE)
                false -> RowAccessory.Status(scope.string(R.string.diagnostics_not_granted), Tone.ERROR)
            }
        }
        scope.api.add(section.target, row)

        val source = ProviderFocusSource(scope.context.contentResolver, scope.environment.modulePackage, scope.environment.logger)
        val main = Handler(Looper.getMainLooper())
        Thread({
            val answer = source.isReadable()
            main.post {
                granted = answer
                scope.api.refresh(row)
            }
        }, "PixelLauncherEvolved-root").start()
    }

    private fun moduleVersion(scope: RowScope): String = runCatching {
        scope.context.packageManager.getPackageInfo(scope.environment.modulePackage, 0).versionName
    }.getOrNull() ?: "?"

    private fun androidVersion(): String {
        val minor = Build.VERSION.SDK_INT_FULL % SDK_FULL_BASE
        return "${Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY} (SDK ${Build.VERSION.SDK_INT}.$minor)"
    }

    private fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Pixel Launcher Evolved compatibility report", text))
    }

    /** Rows go under the heading last added, or onto the page before the first. */
    private class Section(private val page: Any, private val scope: RowScope) {

        var target: Any = page
            private set

        fun heading(@StringRes title: Int) {
            target = scope.api.createCategory(scope.context, scope.string(title)).also { scope.api.add(page, it) }
        }

        fun value(name: String, @StringRes title: Int, value: String, tone: Tone = Tone.NEUTRAL, summary: String = "") {
            val key = SettingsKeys.row("diagnostics_$name")
            scope.api.add(target, scope.api.createAction(scope.context, key, scope.string(title), summary) {})
            scope.accessory(key) { RowAccessory.Status(value, tone) }
        }
    }

    /** `Build.VERSION.SDK_INT_FULL` is the major version times this, plus the minor. */
    private const val SDK_FULL_BASE = 100_000
}
