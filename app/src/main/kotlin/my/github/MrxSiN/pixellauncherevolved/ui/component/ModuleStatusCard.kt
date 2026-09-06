package my.github.MrxSiN.pixellauncherevolved.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.ui.ModuleStatus

/**
 * Says whether the module is actually doing anything, and offers the one action
 * that is not a tweak.
 *
 * Nothing on this screen has any effect while the framework is absent, so the
 * answer leads the page rather than hiding in an about box. Tweaks apply to a
 * running launcher on their own; the restart button is there for the times a
 * hooked process needs a clean slate.
 */
@Composable
fun ModuleStatusCard(
    status: ModuleStatus,
    onRestartLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        is ModuleStatus.Active -> MaterialTheme.colorScheme.primaryContainer
        ModuleStatus.Inactive -> MaterialTheme.colorScheme.errorContainer
        ModuleStatus.Checking -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (status) {
        is ModuleStatus.Active -> MaterialTheme.colorScheme.onPrimaryContainer
        ModuleStatus.Inactive -> MaterialTheme.colorScheme.onErrorContainer
        ModuleStatus.Checking -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusGlyph(status)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(status.titleRes),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = status.detail(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (status is ModuleStatus.Active) {
                FilledTonalButton(onClick = onRestartLauncher) {
                    Text(stringResource(R.string.action_restart_launcher))
                }
            }
        }
    }
}

@Composable
private fun StatusGlyph(status: ModuleStatus) {
    val icon = status.iconRes
    if (icon == null) {
        LoadingIndicator(modifier = Modifier.size(32.dp))
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}

@get:DrawableRes
private val ModuleStatus.iconRes: Int?
    get() = when (this) {
        ModuleStatus.Checking -> null
        is ModuleStatus.Active -> R.drawable.ic_status_active
        ModuleStatus.Inactive -> R.drawable.ic_status_inactive
    }

@get:StringRes
private val ModuleStatus.titleRes: Int
    get() = when (this) {
        ModuleStatus.Checking -> R.string.status_checking_title
        is ModuleStatus.Active -> R.string.status_active_title
        ModuleStatus.Inactive -> R.string.status_inactive_title
    }

@Composable
private fun ModuleStatus.detail(): String = when (this) {
    ModuleStatus.Checking -> stringResource(R.string.status_checking_detail)
    is ModuleStatus.Active ->
        stringResource(R.string.status_active_detail, frameworkName, frameworkBuild)
    ModuleStatus.Inactive -> stringResource(R.string.status_inactive_detail)
}
