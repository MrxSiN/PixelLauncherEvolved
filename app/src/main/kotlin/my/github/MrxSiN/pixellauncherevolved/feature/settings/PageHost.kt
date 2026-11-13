package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle

import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opens one of this module's full-screen pages as a real Pixel Launcher Settings screen, the way
 * Settings opens a sub-page (Home screen, then Dock): a new `SettingsActivity` with its own title
 * bar, so opening it, closing it and the back swipe are the launcher's own, frame for frame.
 *
 * ```
 * new Intent(context, SettingsActivity.class)
 *   .putExtra(":settings:fragment_args",
 *       { "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT": <root key> })
 * ```
 * The root key is one of this module's own; [LauncherSettingsFeature] answers it with an empty
 * screen and hands the activity to [begin], which runs the page's builder with the new screen's
 * context. The builder ends in [ExpressivePage.show], which, inside such a screen, fills it
 * ([isHosting]) instead of opening a window of its own. Its dialogs then belong to the screen on top.
 */
internal object PageHost {

    const val KEY_PREFIX = SettingsKeys.PREFIX + "host_"

    private const val SETTINGS_ACTIVITY = "com.android.launcher3.settings.SettingsActivity"
    private const val FRAGMENT_ARGS = ":settings:fragment_args"
    private const val PREFERENCE_ROOT = "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"

    private class Pending(val title: CharSequence, val build: (Context) -> Unit)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val next = AtomicInteger()

    /** Screens opened for a page and not filled yet. Main thread. */
    private val hosting = WeakHashMap<Activity, Boolean>()

    /** Opens a Settings screen titled [title] and runs [build] in it, with its context. */
    fun open(from: Context, title: CharSequence, build: (Context) -> Unit) {
        val key = KEY_PREFIX + next.incrementAndGet()
        pending[key] = Pending(title, build)
        val intent = Intent().setClassName(from.packageName, SETTINGS_ACTIVITY)
            .putExtra(FRAGMENT_ARGS, Bundle().apply { putString(PREFERENCE_ROOT, key) })
        if (from.activityOrNull() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        from.startActivity(intent)
    }

    /** The title the page opened under [key] asked for, so the screen is named before it is filled. */
    fun titleOf(key: String): CharSequence = pending[key]?.title ?: ""

    /**
     * Called for a root key of this host's, with the screen's context. Runs the page's builder once
     * the screen is laid out. A screen whose builder is gone (the launcher was restarted under it)
     * closes itself rather than stand empty.
     */
    fun begin(context: Context, key: String) {
        val activity = context.activityOrNull() ?: return
        val page = pending.remove(key)
        if (page == null) {
            activity.finish()
            return
        }
        hosting[activity] = true
        activity.window.decorView.post { page.build(context) }
    }

    /** The Settings screen [context] belongs to while it waits for its page; null otherwise. */
    fun isHosting(context: Context): Activity? = context.activityOrNull()?.takeIf { hosting[it] == true }

    /** The screen has its page now; a later page opened from it opens a screen of its own. */
    fun filled(activity: Activity) {
        hosting.remove(activity)
    }

    /** Runs [action] once when [activity] is finishing, which is when a page saves, as Settings saves on leaving. */
    fun onLeave(activity: Activity, action: () -> Unit) {
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var done = false

            private fun leave(seen: Activity, finishing: Boolean) {
                if (done || seen !== activity || !finishing) return
                done = true
                activity.application.unregisterActivityLifecycleCallbacks(this)
                action()
            }

            override fun onActivityPaused(seen: Activity) = leave(seen, seen.isFinishing)
            override fun onActivityDestroyed(seen: Activity) = leave(seen, true)
            override fun onActivityCreated(seen: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(seen: Activity) = Unit
            override fun onActivityResumed(seen: Activity) = Unit
            override fun onActivityStopped(seen: Activity) = Unit
            override fun onActivitySaveInstanceState(seen: Activity, state: Bundle) = Unit
        })
    }
}
