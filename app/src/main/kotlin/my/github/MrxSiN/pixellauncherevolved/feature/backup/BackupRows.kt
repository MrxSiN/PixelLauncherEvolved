package my.github.MrxSiN.pixellauncherevolved.feature.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.SharedPreferencesWebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.RowScope
import my.github.MrxSiN.pixellauncherevolved.feature.settings.SettingsKeys
import my.github.MrxSiN.pixellauncherevolved.feature.settings.SettingsRow
import my.github.MrxSiN.pixellauncherevolved.feature.settings.activityOrNull
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Export settings, Import settings and Reset all tweaks.
 *
 * A backup is a JSON file the person picks a place for, through the system file
 * picker, so it can go to Drive or to another phone. Importing and resetting
 * both replace every tweak at once, so both ask first, and both end by offering
 * the restart that a layout mode needs before it applies.
 */
internal object BackupRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val activity = scope.context.activityOrNull()
        val tweaks = TweakStores(
            settings = scope.settings,
            hiddenApps = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(scope.context)),
            webSearchApp = SharedPreferencesWebSearchAppStore(LauncherSettings.preferences(scope.context)),
        )

        scope.api.add(
            group,
            scope.link(SettingsKeys.row("backup_export"), R.string.backup_export_title, scope.string(R.string.backup_export_summary)) {
                activity?.let { export(it, scope, tweaks) }
            },
        )
        scope.api.add(
            group,
            scope.link(SettingsKeys.row("backup_import"), R.string.backup_import_title, scope.string(R.string.backup_import_summary)) {
                activity?.let { import(it, scope, tweaks) }
            },
        )
        scope.api.add(
            group,
            scope.link(SettingsKeys.row("backup_reset"), R.string.backup_reset_title, scope.string(R.string.backup_reset_summary)) {
                activity?.let { reset(it, scope, tweaks) }
            },
        )
    }

    private fun export(activity: Activity, scope: RowScope, tweaks: TweakStores) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(MIME_JSON)
            .putExtra(Intent.EXTRA_TITLE, FILE_NAME)

        DocumentRequests.launch(activity, intent) { uri ->
            uri ?: return@launch
            val written = runCatching {
                val text = SettingsBackupFormat.write(tweaks.snapshot())
                requireNotNull(activity.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
            }.onFailure { scope.environment.logger.warn("Settings could not be exported", it) }.isSuccess

            tell(scope, activity, if (written) R.string.backup_exported else R.string.backup_export_failed)
        }
    }

    private fun import(activity: Activity, scope: RowScope, tweaks: TweakStores) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_JSON, "text/plain", "application/octet-stream"))

        DocumentRequests.launch(activity, intent) { uri ->
            uri ?: return@launch
            val snapshot = try {
                SettingsBackupFormat.read(readText(activity, uri))
            } catch (error: BackupException) {
                val message = when (error.reason) {
                    BackupException.Reason.NOT_A_BACKUP -> R.string.backup_import_not_a_backup
                    BackupException.Reason.NEWER_VERSION -> R.string.backup_import_newer
                }
                tell(scope, activity, message)
                return@launch
            } catch (error: Exception) {
                scope.environment.logger.warn("Settings could not be read", error)
                tell(scope, activity, R.string.backup_import_not_a_backup)
                return@launch
            }

            confirm(scope, activity, R.string.backup_import_confirm_title, R.string.backup_import_confirm_message, R.string.backup_import_confirm) {
                tweaks.restore(snapshot)
                offerRestart(scope, activity, R.string.backup_imported)
            }
        }
    }

    private fun reset(activity: Activity, scope: RowScope, tweaks: TweakStores) {
        confirm(scope, activity, R.string.backup_reset_confirm_title, R.string.backup_reset_confirm_message, R.string.backup_reset_confirm) {
            tweaks.reset()
            offerRestart(scope, activity, R.string.backup_reset_done)
        }
    }

    private fun readText(activity: Activity, uri: Uri): String =
        requireNotNull(activity.contentResolver.openInputStream(uri)).use { stream ->
            val bytes = stream.readNBytes(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            String(bytes)
        }

    private fun confirm(scope: RowScope, activity: Activity, title: Int, message: Int, action: Int, onConfirm: () -> Unit) {
        ExpressiveDialog(activity)
            .title(scope.string(title))
            .message(scope.string(message))
            .dismiss(activity.getString(android.R.string.cancel))
            .confirm(scope.string(action), onConfirm)
            .show()
    }

    private fun tell(scope: RowScope, activity: Activity, message: Int) {
        ExpressiveDialog(activity)
            .message(scope.string(message))
            .dismiss(activity.getString(android.R.string.ok))
            .show()
    }

    /**
     * Says the tweaks changed, and offers the restart a layout mode needs.
     *
     * Later redraws the settings screen instead, so its switches show what was
     * just written rather than what was there before.
     */
    private fun offerRestart(scope: RowScope, activity: Activity, message: Int) {
        ExpressiveDialog(activity)
            .message(scope.string(message))
            .dismiss(scope.string(R.string.backup_restart_later)) { activity.recreate() }
            .confirm(scope.string(R.string.action_restart_launcher_confirm)) { scope.environment.onRestart() }
            .show()
    }

    private const val MIME_JSON = "application/json"
    private const val FILE_NAME = "pixel-launcher-evolved-settings.json"

    /** A settings file is a few hundred bytes; anything this large is not one. */
    private const val MAX_BYTES = 64 * 1024
}
