package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle

/** One launchable app, as the per-app editor and the pack preview list it. */
data class LaunchableApp(
    val packageName: String,
    val className: String,
    val label: String,
    val user: UserHandle,
    val userId: Int,
    val info: LauncherActivityInfo,
)

/**
 * The launchable apps of every profile this launcher can see.
 *
 * Asked for through `LauncherApps`, which is the same list the app drawer is
 * built from, so the editor offers exactly what the drawer shows. It is a Binder
 * call per profile and is only ever made from a worker, never while anything is
 * being drawn.
 *
 * A locked private profile answers with nothing rather than throwing, which is
 * the behaviour wanted: its apps are not listed while it is locked, and no
 * attempt is made to read them.
 */
object LaunchableApps {

    fun of(context: Context): List<LaunchableApp> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val profiles = runCatching { launcherApps.profiles }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: listOf(Process.myUserHandle())

        val apps = ArrayList<LaunchableApp>()
        for (user in profiles) {
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrNull() ?: continue
            for (activity in activities) {
                apps += LaunchableApp(
                    packageName = activity.componentName.packageName,
                    className = activity.componentName.className,
                    label = activity.label?.toString().orEmpty(),
                    user = user,
                    // The same arithmetic the icon hook uses, so an override
                    // stored from here is found from there.
                    userId = activity.applicationInfo.uid / PER_USER_RANGE,
                    info = activity,
                )
            }
        }
        return apps.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }, { it.userId }))
    }

    /** How the platform packs a profile into an application's uid. */
    private const val PER_USER_RANGE = 100000
}
