package my.github.MrxSiN.pixellauncherevolved.diagnostics

import android.content.Context

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * The contracts checked against the launcher running in this process.
 *
 * A launcher's classes do not change while it runs, so one analysis stands for
 * the life of the process: the registry reads it to leave out a tweak that
 * cannot work, and settings reads the same one to say why.
 */
class LauncherAnalysis(
    val launcherVersion: String,
    val report: ContractReport,
) {

    val text: String get() = report.format(launcherVersion)

    fun isAvailable(feature: CompatibilityFeature): Boolean = report.isCompatible(feature)

    companion object {

        fun run(context: Context, classLoader: ClassLoader, logger: Logger): LauncherAnalysis {
            val analyzer = ContractAnalyzer { name ->
                runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            }
            return LauncherAnalysis(launcherVersion(context, logger), analyzer.analyze(LauncherContracts.all))
        }

        /** The launcher's `versionCode`, which is what changes with each monthly update. */
        fun launcherVersion(context: Context, logger: Logger): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        }.onFailure { logger.warn("The launcher's version could not be read", it) }.getOrDefault("?")
    }
}
