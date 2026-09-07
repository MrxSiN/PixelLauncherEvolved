package my.github.MrxSiN.pixellauncherevolved.lock

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/**
 * Turns the screen off, for the launcher and for nobody else.
 *
 * A provider is used rather than a broadcast because [Binder.getCallingUid]
 * names the caller, so the one thing this app can be asked to do cannot be
 * asked by any other app on the device.
 *
 * It stores nothing and answers no queries. The only entry point is [call].
 */
class ScreenLockProvider : ContentProvider() {

    private val screenLocker: ScreenLocker = RootScreenLocker()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (method != ScreenLock.LOCK || !isLauncher(context, Binder.getCallingUid())) return null

        return Bundle().apply { putBoolean(ScreenLock.LOCKED, screenLocker.lock()) }
    }

    private fun isLauncher(context: Context, uid: Int): Boolean =
        context.packageManager.getPackagesForUid(uid)?.contains(LAUNCHER_PACKAGE) == true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
    }
}
