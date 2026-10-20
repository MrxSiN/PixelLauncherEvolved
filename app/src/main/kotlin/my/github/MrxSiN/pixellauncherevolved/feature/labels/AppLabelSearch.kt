package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.app.search.Query
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.function.Consumer

import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabels
import my.github.MrxSiN.pixellauncherevolved.labels.Profiles

/**
 * Finds a renamed app by the name it was given.
 *
 * The app drawer's search is answered by the platform's search service, which
 * knows each app by its own label and has never heard of this one. So the
 * launcher's question is let through unchanged, and the answer is added to on
 * the way back: a renamed app whose new name matches what was typed is put at
 * the head of the app results, as the same kind of result the launcher builds
 * for an app itself. An app the service already found is not added twice, and
 * typing the app's own label still finds it, because that is the service's
 * answer and it is left alone.
 *
 * Matching is the launcher's own (`StringMatcherUtility`), word starts and
 * all, so a name is found the way a label is found.
 *
 * ```
 * android.app.search.SearchSession.query(Query, Executor, Consumer<List<SearchTarget>>)
 * new SearchTarget.Builder(RESULT_TYPE_APPLICATION, "icon", id)
 *     .setPackageName(package).setUserHandle(user).setExtras({"class": activity}).build()
 * ```
 *
 * Apps of a profile that is paused or locked (a work profile switched off, a
 * locked private space) are never added: their names must not surface while
 * the apps themselves are hidden.
 */
internal object AppLabelSearch {

    fun install(context: FeatureContext) {
        val parts = runCatching { Parts(context) }
            .onFailure { context.logger.warn("App labels: search finds apps by their own labels only", it) }
            .getOrNull() ?: return

        val session = requireNotNull(context.findClass(SEARCH_SESSION))
        val query = Reflect.declaredMethod(session, "query", Query::class.java, Executor::class.java, Consumer::class.java)
        val users = context.appContext.getSystemService(UserManager::class.java)
        val me = Process.myUserHandle()

        context.xposed.hook(query).intercept { chain ->
            val labels = AppLabels.current()
            val asked = chain.getArg(0) as? Query
            val answer = chain.getArg(ANSWER) as? Consumer<*>
            if (!labels.hasNames() || AppLabels.homeOnly() || asked == null || answer == null) return@intercept chain.proceed()

            val matches = runCatching { parts.matches(labels, asked.input, users, me) }
                .onFailure { context.logger.warn("App labels: unable to search the chosen names", it) }
                .getOrNull()
            if (matches == null || matches.isEmpty()) return@intercept chain.proceed()

            @Suppress("UNCHECKED_CAST")
            val theirs = answer as Consumer<List<Any?>>
            chain.proceed(
                arrayOf(
                    asked,
                    chain.getArg(EXECUTOR),
                    Consumer<List<Any?>> { results ->
                        val added = runCatching { parts.withMatches(results, labels, matches) }
                            .onFailure { context.logger.warn("App labels: renamed apps left out of these results", it) }
                            .getOrDefault(results)
                        theirs.accept(added)
                    },
                ),
            )
        }
        context.logger.info("App labels: search finds renamed apps by their new names")
    }

    private class Parts(context: FeatureContext) {
        private val target: Class<*> = requireNotNull(context.findClass(SEARCH_TARGET))
        private val builder: Constructor<*> = requireNotNull(context.findClass("$SEARCH_TARGET\$Builder"))
            .getConstructor(Int::class.javaPrimitiveType, String::class.java, String::class.java)
        private val setPackage: Method = builder.declaringClass.getMethod("setPackageName", String::class.java)
        private val setUser: Method = builder.declaringClass.getMethod("setUserHandle", UserHandle::class.java)
        private val setExtras: Method = builder.declaringClass.getMethod("setExtras", Bundle::class.java)
        private val build: Method = builder.declaringClass.getMethod("build")
        private val packageOf: Method = target.getMethod("getPackageName")
        private val userOf: Method = target.getMethod("getUserHandle")
        private val extrasOf: Method = target.getMethod("getExtras")
        private val matcherClass: Class<*> = requireNotNull(context.findClass("$MATCHER_UTILITY\$StringMatcher"))
        private val matcher: Any = matcherClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        private val matches: Method = Reflect.declaredMethod(
            requireNotNull(context.findClass(MATCHER_UTILITY)),
            "matches",
            String::class.java,
            String::class.java,
            matcherClass,
        )

        /** The entries whose name matches [input], or null for none. */
        fun matches(labels: AppLabels, input: String?, users: UserManager?, me: UserHandle): IntArray? {
            if (input.isNullOrBlank()) return null
            val typed = input.trim().lowercase()
            var found: IntArray? = null
            var count = 0
            for (at in 0 until labels.size()) {
                val name = labels.name(at) ?: continue
                if (matches.invoke(null, typed, name, matcher) != true) continue
                val user = Profiles.handleOf(labels.userId(at))
                if (user != me && users?.isQuietModeEnabled(user) != false) continue
                val into = found ?: IntArray(labels.size()).also { found = it }
                into[count++] = at
            }
            return found?.copyOf(count)
        }

        /** [results] with the matched apps the service did not return put first. */
        fun withMatches(results: List<Any?>?, labels: AppLabels, matched: IntArray): List<Any?> {
            val all = ArrayList<Any?>((results?.size ?: 0) + matched.size)
            for (at in matched) {
                if (results != null && results.any { isApp(it, labels, at) }) continue
                all += appTarget(labels, at)
            }
            if (results != null) all.addAll(results)
            return all
        }

        private fun isApp(result: Any?, labels: AppLabels, at: Int): Boolean {
            if (!target.isInstance(result)) return false
            if (Invoke.noArgs(packageOf, result) != labels.packageName(at)) return false
            if ((Invoke.noArgs(userOf, result) as UserHandle?)?.hashCode() != labels.userId(at)) return false
            val activity = (Invoke.noArgs(extrasOf, result) as Bundle?)?.getString(EXTRA_CLASS)
            return activity == null || activity == labels.className(at)
        }

        /** The result the launcher builds for an app itself, for its own fallback search. */
        private fun appTarget(labels: AppLabels, at: Int): Any {
            val packageName = labels.packageName(at)
            val className = labels.className(at)
            val user = Profiles.handleOf(labels.userId(at))
            val id = "APPLICATION:${labels.userId(at)}:$packageName:$className"
            val made = builder.newInstance(RESULT_TYPE_APPLICATION, LAYOUT_ICON, id)
            setPackage.invoke(made, packageName)
            setUser.invoke(made, user)
            setExtras.invoke(made, Bundle().apply { putString(EXTRA_CLASS, className) })
            return Invoke.noArgs(build, made)
        }
    }

    private const val SEARCH_SESSION = "android.app.search.SearchSession"
    private const val SEARCH_TARGET = "android.app.search.SearchTarget"
    private const val MATCHER_UTILITY = "com.android.launcher3.search.StringMatcherUtility"

    /** `query(Query, Executor, Consumer)`. */
    private const val EXECUTOR = 1
    private const val ANSWER = 2

    /** `SearchTarget.RESULT_TYPE_APPLICATION`, and the layout the launcher gives an app result. */
    private const val RESULT_TYPE_APPLICATION = 1
    private const val LAYOUT_ICON = "icon"
    private const val EXTRA_CLASS = "class"
}
