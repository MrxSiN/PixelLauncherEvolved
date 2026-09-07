package my.github.MrxSiN.pixellauncherevolved.ui.screen

import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.ui.HomeSettings
import my.github.MrxSiN.pixellauncherevolved.ui.ModuleStatus
import my.github.MrxSiN.pixellauncherevolved.ui.SettingsViewModel
import my.github.MrxSiN.pixellauncherevolved.ui.component.ModuleStatusCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsUnavailable = stringResource(R.string.settings_unavailable)

    fun report(done: Boolean, message: String) {
        if (!done) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    SettingsContent(
        status = status,
        onOpenHomeSettings = { report(HomeSettings.open(context), settingsUnavailable) },
    )
}

/** Stateless presentation; the framework lifecycle stays in the view model. */
@Composable
private fun SettingsContent(
    status: ModuleStatus,
    onOpenHomeSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.app_tagline)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 380.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection) + 20.dp,
                end = insets.calculateEndPadding(layoutDirection) + 20.dp,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                ModuleStatusCard(status)
            }
            item(key = "where", span = { GridItemSpan(maxLineSpan) }) {
                WhereTheSettingsAre(onOpenHomeSettings)
            }
        }
    }
}

/**
 * Where the tweaks went.
 *
 * They are part of the launcher's own Home settings now, so this app's job is
 * to say so once and offer the shortest way there.
 */
@Composable
private fun WhereTheSettingsAre(onOpenHomeSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.settings_heading),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            Text(
                stringResource(R.string.settings_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onOpenHomeSettings) {
            Text(stringResource(R.string.action_open_home_settings))
        }
    }
}
