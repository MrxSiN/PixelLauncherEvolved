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
     * Appended to the module's own package name, as `${applicationId}.focus` in
     * the manifest is.
     *
     * The package itself is never written here. The reader runs inside the
     * launcher, where the module's package is something to be asked for rather
     * than assumed, and a copy of it kept in this file is a copy that survives
     * the package being renamed — which is exactly how the launcher came to be
     * asking an authority that no longer existed.
     */
    const val AUTHORITY_SUFFIX: String = ".focus"

    /** Every mode, one row each. */
    fun modes(modulePackage: String): Uri = uri(modulePackage, MODES_PATH)

    /** Access and modes from one root-backed snapshot. */
    fun snapshot(modulePackage: String): Uri = uri(modulePackage, SNAPSHOT_PATH)

    /** One row saying whether modes can be read at all. */
    fun access(modulePackage: String): Uri = uri(modulePackage, ACCESS_PATH)

    private fun uri(modulePackage: String, path: String): Uri =
        Uri.parse("content://$modulePackage$AUTHORITY_SUFFIX/$path")

    /** The paths above, which the provider answers by. */
    const val MODES_PATH: String = "modes"
    const val SNAPSHOT_PATH: String = "snapshot"
    const val ACCESS_PATH: String = "access"

    const val COLUMN_ID: String = "id"
    const val COLUMN_NAME: String = "name"
    const val COLUMN_ACTIVE: String = "active"
    const val COLUMN_GRANTED: String = "granted"
    const val COLUMN_ICON: String = "icon"
    const val COLUMN_ENABLED: String = "enabled"
}
