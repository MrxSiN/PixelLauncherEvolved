package my.github.MrxSiN.pixellauncherevolved.update

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

import java.net.HttpURLConnection
import java.net.URL

import my.github.MrxSiN.pixellauncherevolved.focus.FocusProvider

/**
 * Asks GitHub for the newest release, for the launcher's update check.
 *
 * Pixel Launcher has no Internet permission, so this module's app makes the request: one bodiless
 * `HEAD` to [UpdateWire.LATEST_URL] with redirects off, answering the `Location` it is sent to.
 * Only the launcher is answered. Runs on the Binder thread the launcher's call arrives on, which the
 * launcher makes off its main thread; it never retries.
 */
class UpdateProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (callingPackage != FocusProvider.LAUNCHER_PACKAGE || method != UpdateWire.METHOD_LATEST) return null
        return runCatching { UpdateWire.found(latest()) }.getOrElse { UpdateWire.failed(it.toString()) }
    }

    private fun latest(): String {
        val connection = URL(UpdateWire.LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // A generic name: nothing about the phone or its owner goes with the request.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            return connection.getHeaderField("Location") ?: error("no redirect (HTTP $code)")
        } finally {
            connection.disconnect()
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val USER_AGENT = "PixelLauncherEvolved-update-check"
    }
}
