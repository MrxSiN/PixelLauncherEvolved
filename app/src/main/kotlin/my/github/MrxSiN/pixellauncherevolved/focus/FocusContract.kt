package my.github.MrxSiN.pixellauncherevolved.focus

import android.net.Uri

/**
 * The one thing this module's app and the launcher both have to agree on.
 *
 * Both sides are built from this file, so a column renamed here is renamed for
 * the reader and the writer at once.
 */
object FocusContract {

    /**
     * Matches `${applicationId}.focus` in the manifest, written out because the
     * build config class this module would read it from is not generated.
     * `check-project.sh` holds the two together.
     */
    const val AUTHORITY: String = "my.github.MrxSiN.pixellauncherevolved.focus"

    /** Every mode, one row each. */
    val MODES: Uri = Uri.parse("content://$AUTHORITY/modes")

    /** Access and modes from one root-backed snapshot. */
    val SNAPSHOT: Uri = Uri.parse("content://$AUTHORITY/snapshot")

    /** One row saying whether modes can be read at all. */
    val ACCESS: Uri = Uri.parse("content://$AUTHORITY/access")

    const val COLUMN_ID: String = "id"
    const val COLUMN_NAME: String = "name"
    const val COLUMN_ACTIVE: String = "active"
    const val COLUMN_GRANTED: String = "granted"
}
