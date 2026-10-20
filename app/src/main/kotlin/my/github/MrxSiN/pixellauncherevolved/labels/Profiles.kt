package my.github.MrxSiN.pixellauncherevolved.labels

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * A profile by what it is rather than by its number.
 *
 * A user id belongs to one device: a work profile is 10 on one phone and 11 on
 * the next, and a removed profile's number can come back as a different
 * profile. So a label leaving the device names the kind of profile it was for,
 * and is placed by kind where it lands.
 */
enum class ProfileKind(val portable: String) {
    PERSONAL("personal"),
    WORK("work"),
    PRIVATE("private"),
    CLONE("clone");

    companion object {
        fun of(portable: String?): ProfileKind? = entries.firstOrNull { it.portable == portable }
    }
}

/** The profiles this launcher shows, by id and by kind. Binder calls: never on a frame path. */
class Profiles(context: Context) {

    private val users = context.getSystemService(UserManager::class.java)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val me = Process.myUserHandle()

    /** Every profile id of this user, paused or locked ones included. */
    fun ids(): Set<Int> = runCatching { users?.userProfiles }.getOrNull().orEmpty().mapTo(HashSet()) { it.hashCode() }

    fun kindOf(userId: Int): ProfileKind? {
        val user = handleOf(userId)
        if (user == me) return ProfileKind.PERSONAL
        val type = runCatching { launcherApps?.getLauncherUserInfo(user)?.userType }.getOrNull() ?: return null
        return when (type) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> ProfileKind.WORK
            UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
            UserManager.USER_TYPE_PROFILE_CLONE -> ProfileKind.CLONE
            else -> null
        }
    }

    /** This device's profile of [kind], or null when it has none. */
    fun idOf(kind: ProfileKind): Int? {
        if (kind == ProfileKind.PERSONAL) return me.hashCode()
        return ids().firstOrNull { it != me.hashCode() && kindOf(it) == kind }
    }

    companion object {
        /** How the platform packs a profile into an application's uid. */
        private const val PER_USER_RANGE = 100000

        fun handleOf(userId: Int): UserHandle = UserHandle.getUserHandleForUid(userId * PER_USER_RANGE)
    }
}
