package my.github.MrxSiN.pixellauncherevolved.recovery

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Process
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
 */
class RecoveryActivity : Activity() {

    /** In the global mount namespace: the launcher's data is hidden from this app's own. */
    private val shell: CommandRunner = RootShell(globalNamespace = true)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var buttons: List<Button>

    /** The launcher's copies, for this Android user only. */
    private val folder = "/data/user/${Process.myUid() / PER_USER_RANGE}/$LAUNCHER/no_backup/ple_layout"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.recovery_title)

        val pad = dp(24)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            filterTouchesWhenObscured = true
        }
        column.addView(text(getString(R.string.recovery_intro)))
        status = text(getString(R.string.recovery_checking)).also(column::addView)
        buttons = listOf(
            button(R.string.recovery_restore_before) { confirm(SLOT_BEFORE, R.string.recovery_confirm_before) },
            button(R.string.recovery_restore_latest) { confirm(SLOT_LATEST, R.string.recovery_confirm_latest) },
        ).onEach(column::addView)
        column.addView(text(getString(R.string.recovery_limits)))
        setContentView(ScrollView(this).apply {
            addView(column)
            clipToPadding = false
            // Drawn edge to edge (target SDK 35+): keep the text clear of the title and system bars.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })

        refresh()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    /** Reads which copies exist, off the main thread: root can take a while to answer. */
    private fun refresh() {
        buttons.forEach { it.isEnabled = false }
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
            status.setText(R.string.recovery_no_root)
            return
        }
        val copies = parse(result.text)
        status.text = listOf(SLOT_BEFORE to R.string.recovery_copy_before, SLOT_LATEST to R.string.recovery_copy_latest)
            .joinToString("\n\n") { (slot, title) ->
                val copy = copies[slot]
                getString(title) + "\n" + if (copy == null) {
                    getString(R.string.recovery_copy_none)
                } else {
                    getString(R.string.recovery_copy_detail, copy.manifest.items, DateFormat.getDateFormat(this).format(Date(copy.seconds * 1000)) + " " + DateFormat.getTimeFormat(this).format(Date(copy.seconds * 1000)))
                }
            }
        buttons[0].isEnabled = SLOT_BEFORE in copies
        buttons[1].isEnabled = SLOT_LATEST in copies
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
        buttons.forEach { it.isEnabled = false }
        status.setText(R.string.recovery_restoring)
        worker.execute {
            val result = shell.run(
                "D=$folder; [ -f \$D/$slot.db ] && [ -f \$D/$slot.properties ] || exit 3; " +
                    "echo $slot > \$D/restore && chown \$(stat -c %u:%g \$D) \$D/restore && chmod 600 \$D/restore && " +
                    "restorecon \$D/restore && am force-stop --user ${Process.myUid() / PER_USER_RANGE} $LAUNCHER",
            )
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (result is RootResult.Output) {
                    status.setText(R.string.recovery_done)
                } else {
                    status.setText(R.string.recovery_failed)
                    refresh()
                }
            }
        }
    }

    private fun text(value: String) = TextView(this).apply {
        text = value
        setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
        setPadding(0, 0, 0, dp(16))
    }

    private fun button(title: Int, onClick: () -> Unit) = Button(this).apply {
        setText(title)
        isEnabled = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    internal class Copy(val seconds: Long, val manifest: Manifest)

    internal companion object {
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val SLOT_BEFORE = "before"
        const val SLOT_LATEST = "latest"
        const val MARK = "@@"

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
