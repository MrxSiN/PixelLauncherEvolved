package my.github.MrxSiN.pixellauncherevolved.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Says whether the module is actually doing anything.
 *
 * Nothing this module offers has any effect while the framework is absent, and
 * that answer is the one thing the launcher's own settings screen cannot give,
 * so it leads this page rather than hiding in an about box.
 */
@Composable
fun ModuleStatusCard(
    status: ModuleStatus,
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(status)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(status.titleRes),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = status.detail(),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
