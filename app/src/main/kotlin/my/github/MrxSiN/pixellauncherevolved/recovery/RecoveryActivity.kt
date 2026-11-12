package my.github.MrxSiN.pixellauncherevolved.recovery

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Process
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import java.util.Date
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.focus.CommandRunner
import my.github.MrxSiN.pixellauncherevolved.focus.RootResult
import my.github.MrxSiN.pixellauncherevolved.focus.RootShell
import my.github.MrxSiN.pixellauncherevolved.layout.Manifest

/**
 * Puts the Home screen layout back without opening Pixel Launcher.
 *
 * Home settings live inside the launcher, so a launcher that crashes as it
 * starts takes them with it. This screen is in the module's own app, opened from
 * Android Settings (Apps, this app, its settings), from the module manager, or
 * with `adb shell am start -n my.github.MrxSiN.pixellauncherevolved/.recovery.RecoveryActivity`.
 *
 * It shows the copies the launcher kept of its layout
 * ([my.github.MrxSiN.pixellauncherevolved.layout.LayoutCheckpoints]) and puts
 * one back the way the launcher reads a request: a file naming it, which the
 * launcher acts on as it next starts, before it opens its layout. Root writes
 * that file as the launcher's own and stops the launcher, for this Android user
 * only. Nothing is done from the intent alone: every change is a tap here,
 * confirmed, on a screen that ignores taps made through another window.
 *
 * Drawn by [RecoveryPage] the way Settings draws a page.
 */
class RecoveryActivity : Activity() {

    /** In the global mount namespace: the launcher's data is hidden from this app's own. */
    private val shell: CommandRunner = RootShell(globalNamespace = true)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var rows: Map<String, RecoveryPage.Row>

    /** What the banner says now; a later read of the copies hides it only while it says "checking". */
    private var message = R.string.recovery_checking

    /** The launcher's copies, for this Android user only. */
    private val folder = "/data/user/${Process.myUid() / PER_USER_RANGE}/$LAUNCHER/no_backup/ple_layout"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.recovery_title)

        val page = RecoveryPage(this)
        page.header(getString(R.string.recovery_title))
        page.intro(getString(R.string.recovery_intro))
        status = page.banner(getString(R.string.recovery_checking))
        page.groupTitle(getString(R.string.recovery_backups))
        val restore = getString(R.string.recovery_restore)
        rows = SLOTS.associate { (slot, title, confirm) ->
            slot to page.row(R.drawable.ic_recovery_history, getString(title), restore) { confirm(slot, confirm) }
        }
        page.shapeGroup(rows.values.toList())
        page.footer(getString(R.string.recovery_limits))

        refresh()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    /** Reads which copies exist, off the main thread: root can take a while to answer. */
    private fun refresh() {
        rows.values.forEach { it.button.isEnabled = false }
        worker.execute {
            val result = shell.run(
                "for s in $SLOT_BEFORE $SLOT_LATEST; do echo \"$MARK\$s \$(stat -c %Y $folder/\$s.db 2>/dev/null)\"; " +
                    "cat $folder/\$s.properties 2>/dev/null; done; true",
            )
            runOnUiThread { if (!isFinishing) show(result) }
        }
    }

    private fun show(result: RootResult) {
        if (result !is RootResult.Output) {
            say(R.string.recovery_no_root)
            return
        }
        if (message == R.string.recovery_checking) status.visibility = View.GONE
        val copies = parse(result.text)
        for ((slot, row) in rows) {
            val copy = copies[slot]
            row.summary.text = if (copy == null) {
                getString(R.string.recovery_copy_none)
            } else {
                val saved = Date(copy.seconds * 1000)
                getString(
                    R.string.recovery_copy_detail,
                    copy.manifest.items,
                    DateFormat.getDateFormat(this).format(saved) + " " + DateFormat.getTimeFormat(this).format(saved),
                )
            }
            row.button.isEnabled = copy != null
        }
    }

    private fun confirm(slot: String, message: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.recovery_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.recovery_restore) { _, _ -> restore(slot) }
            .show()
    }

    /**
     * Files the request as the launcher's own (owner, mode and SELinux label), then
     * stops the launcher; Android starts it again and it puts the copy back first.
     */
    private fun restore(slot: String) {
        rows.values.forEach { it.button.isEnabled = false }
        say(R.string.recovery_restoring)
        worker.execute {
            val result = shell.run(
                "D=$folder; [ -f \$D/$slot.db ] && [ -f \$D/$slot.properties ] || exit 3; " +
                    "echo $slot > \$D/restore && chown \$(stat -c %u:%g \$D) \$D/restore && chmod 600 \$D/restore && " +
                    "restorecon \$D/restore && am force-stop --user ${Process.myUid() / PER_USER_RANGE} $LAUNCHER",
            )
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (result is RootResult.Output) {
                    say(R.string.recovery_done)
                } else {
                    refresh()
                    say(R.string.recovery_failed)
                }
            }
        }
    }

    /** Shows what the page is doing now in its banner. */
    private fun say(message: Int) {
        this.message = message
        status.setText(message)
        status.visibility = View.VISIBLE
    }

    internal class Copy(val seconds: Long, val manifest: Manifest)

    internal companion object {
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val SLOT_BEFORE = "before"
        const val SLOT_LATEST = "latest"
        const val MARK = "@@"

        /** Each copy's row: its slot, its title and what restoring it asks to confirm. */
        val SLOTS = listOf(
            Triple(SLOT_BEFORE, R.string.recovery_copy_before, R.string.recovery_confirm_before),
            Triple(SLOT_LATEST, R.string.recovery_copy_latest, R.string.recovery_confirm_latest),
        )

        /** `UserHandle.PER_USER_RANGE`: a uid divided by it is its user. */
        const val PER_USER_RANGE = 100_000

        /** Each copy the listing names, with when it was taken; a copy missing either half is left out. */
        fun parse(listing: String): Map<String, Copy> {
            val copies = HashMap<String, Copy>()
            for (block in listing.split(MARK).drop(1)) {
                val header = block.substringBefore('\n').trim().split(' ')
                val seconds = header.getOrNull(1)?.toLongOrNull() ?: continue
                val manifest = Manifest.decode(block.substringAfter('\n', "")) ?: continue
                copies[header[0]] = Copy(seconds, manifest)
            }
            return copies
        }
    }
}
