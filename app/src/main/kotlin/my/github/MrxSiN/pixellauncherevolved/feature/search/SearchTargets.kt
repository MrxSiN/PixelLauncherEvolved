package my.github.MrxSiN.pixellauncherevolved.feature.search

import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Reads an `android.app.search.SearchTarget` without naming its class.
 *
 * The type is a system API rather than a public one, so this module cannot
 * compile against it even though the launcher receives instances of it. The
 * three getters this needs are read by reflection once, and everything above
 * this class works in plain values.
 *
 * A failed lookup leaves the reader unbuilt rather than half built: [invoke]
 * answers null and the feature declines to install.
 */
class SearchTargets private constructor(
    private val target: Class<*>,
    private val resultTypeOf: Method,
    private val layoutTypeOf: Method,
    private val packageNameOf: Method,
) {

    /** Whether [type] is the search target class itself. */
    fun isTarget(type: Class<*>): Boolean = target == type

    /** Null for anything that is not a search target, or that will not answer. */
    fun read(value: Any?): SearchResult? {
        if (!target.isInstance(value)) return null

        return runCatching {
            SearchResult(
                resultType = resultTypeOf.invoke(value) as Int,
                layoutType = layoutTypeOf.invoke(value) as? String ?: "",
                packageName = packageNameOf.invoke(value) as? String ?: "",
            )
        }.getOrNull()
    }

    companion object {

        const val SEARCH_TARGET = "android.app.search.SearchTarget"

        operator fun invoke(context: FeatureContext): SearchTargets? {
            val target = context.findClass(SEARCH_TARGET) ?: return null

            return SearchTargets(
                target = target,
                resultTypeOf = Reflect.method(target, "getResultType") ?: return null,
                layoutTypeOf = Reflect.method(target, "getLayoutType") ?: return null,
                packageNameOf = Reflect.method(target, "getPackageName") ?: return null,
            )
        }
    }
}
