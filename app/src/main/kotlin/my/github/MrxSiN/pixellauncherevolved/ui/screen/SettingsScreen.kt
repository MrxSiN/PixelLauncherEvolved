package my.github.MrxSiN.pixellauncherevolved.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogCategory
import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogEntry
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.ui.SettingsUiState
import my.github.MrxSiN.pixellauncherevolved.ui.SettingsViewModel
import my.github.MrxSiN.pixellauncherevolved.ui.component.ModuleStatusCard
import my.github.MrxSiN.pixellauncherevolved.ui.component.SwitchRow

/**
 * The whole settings surface.
 *
 * Everything the module offers fits on one scrolling page grouped by category,
 * which keeps a tweak one gesture away instead of behind a navigation step.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.app_tagline)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModuleStatusCard(
                    status = uiState.status,
                    onRestartLauncher = viewModel::requestLauncherRestart,
                )
            }

            for (category in CatalogCategory.entries) {
                val entries = FeatureCatalog.entriesIn(category)
                if (entries.isEmpty()) continue

                item(key = "header-${category.name}") { CategoryHeader(category) }
                item(key = "group-${category.name}") { CategoryCard(entries, uiState, viewModel) }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: CatalogCategory) {
    Column(
        modifier = Modifier.padding(start = 12.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(category.titleRes),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(category.summaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryCard(
    entries: List<CatalogEntry>,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            for (entry in entries) {
                SettingEntry(entry, uiState, viewModel)
            }
        }
    }
}

@Composable
private fun SettingEntry(
    entry: CatalogEntry,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    when (val setting = entry.setting) {
        is BoolSetting -> SwitchRow(
            title = stringResource(entry.titleRes),
            summary = stringResource(entry.summaryRes),
            checked = uiState.values[setting.key] as? Boolean ?: setting.default,
            enabled = uiState.editable,
            onCheckedChange = { viewModel.set(setting, it) },
        )

        else -> Unit
    }
}
