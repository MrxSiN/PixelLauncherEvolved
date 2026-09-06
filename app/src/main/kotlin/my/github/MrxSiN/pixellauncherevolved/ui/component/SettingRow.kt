package my.github.MrxSiN.pixellauncherevolved.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** One on/off tweak, as a full-width row that toggles anywhere along its length. */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA

    ListItem(
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLargeEmphasized)
        },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = LocalContentColor.current.copy(alpha = contentAlpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
        ),
    )
}

private const val DISABLED_ALPHA = 0.38f
