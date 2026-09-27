package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.app.search.SearchTarget

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Reads an `android.app.search.SearchTarget` without naming its class.
 *
 * The type is a system API rather than a public one, so the public SDK leaves
 * it out. The module compiles against a stub of the three getters (`:stubs`,
 * never packaged) and calls the device's own class directly, which skips a
 * reflective call per getter per result. The getters are still looked up once
 * by reflection, so a device without them leaves the reader unbuilt rather than
 * half built: [invoke] answers null and the feature declines to install.
 */
class SearchTargets private constructor(private val target: Class<*>) {

    /** Whether [type] is the search target class itself. */
    fun isTarget(type: Class<*>): Boolean = target == type

    /** Null for anything that is not a search target, or that will not answer. */
    fun read(value: Any?): SearchResult? {
        if (!target.isInstance(value)) return null

        val searchTarget = value as SearchTarget
        return runCatching {
            SearchResult(
                resultType = searchTarget.resultType,
                layoutType = searchTarget.layoutType ?: "",
                packageName = searchTarget.packageName ?: "",
            )
        }.getOrNull()
    }

    companion object {

        const val SEARCH_TARGET = "android.app.search.SearchTarget"

        operator fun invoke(context: FeatureContext): SearchTargets? {
            val target = context.findClass(SEARCH_TARGET) ?: return null

            Reflect.method(target, "getResultType") ?: return null
            Reflect.method(target, "getLayoutType") ?: return null
            Reflect.method(target, "getPackageName") ?: return null

            return SearchTargets(target)
        }
    }
}
