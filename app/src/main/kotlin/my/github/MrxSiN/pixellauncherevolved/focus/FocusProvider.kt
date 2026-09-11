package my.github.MrxSiN.pixellauncherevolved.focus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Reports the device's Modes to the launcher.
 *
 * The launcher cannot read Modes itself: that needs Do Not Disturb access,
 * which it does not hold and which cannot be given to it without changing what
 * a Google app is allowed to do. This module's app can hold it, granted once by
 * the person, so it does the reading and answers here.
 *
 * Exported, because the launcher is a different app and package visibility
 * gives it no other way in. Every call is checked against the one caller that
 * has any business asking, so being exported does not mean being open: which
 * Modes someone has, and which are on right now, says a good deal about them.
 */
class FocusProvider : ContentProvider() {

    private val modes = ZenModes()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (callingPackage != LAUNCHER_PACKAGE) return null
        val zen = modes

        return when (uri.lastPathSegment) {
            SNAPSHOT -> snapshotCursor(zen.snapshot())

            MODES -> MatrixCursor(
                arrayOf(
                    FocusContract.COLUMN_ID,
                    FocusContract.COLUMN_NAME,
                    FocusContract.COLUMN_ACTIVE,
                ),
            ).apply {
                for (mode in zen.modes()) {
                    addRow(arrayOf<Any>(mode.id, mode.name, if (mode.isActive) 1 else 0))
                }
            }

            ACCESS -> MatrixCursor(arrayOf(FocusContract.COLUMN_GRANTED)).apply {
                addRow(arrayOf<Any>(if (zen.isGranted()) 1 else 0))
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null

    // Read-only: the launcher asks what the modes are and never sets one.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun snapshotCursor(snapshot: FocusSnapshot): MatrixCursor = MatrixCursor(
        arrayOf(
            FocusContract.COLUMN_ID,
            FocusContract.COLUMN_NAME,
            FocusContract.COLUMN_ACTIVE,
            FocusContract.COLUMN_GRANTED,
        ),
    ).apply {
        if (snapshot.modes.isEmpty()) {
            addRow(arrayOf(null, null, 0, if (snapshot.isReadable) 1 else 0))
        } else {
            for (mode in snapshot.modes) {
                addRow(
                    arrayOf<Any>(
                        mode.id,
                        mode.name,
                        if (mode.isActive) 1 else 0,
                        if (snapshot.isReadable) 1 else 0,
                    ),
                )
            }
        }
    }

    private companion object {
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

        // The paths the reader asks by, so the two cannot drift apart.
        const val SNAPSHOT = FocusContract.SNAPSHOT_PATH
        const val MODES = FocusContract.MODES_PATH
        const val ACCESS = FocusContract.ACCESS_PATH
    }
}
