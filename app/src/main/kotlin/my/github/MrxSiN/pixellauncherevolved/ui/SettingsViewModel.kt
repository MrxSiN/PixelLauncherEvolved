package my.github.MrxSiN.pixellauncherevolved.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.Setting
import my.github.MrxSiN.pixellauncherevolved.settings.ModuleConnection
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore
import my.github.MrxSiN.pixellauncherevolved.settings.SharedPreferencesStore

/** Whether the framework is present and has accepted this module. */
sealed interface ModuleStatus {
    data object Checking : ModuleStatus
    data class Active(val frameworkName: String, val frameworkBuild: String) : ModuleStatus
    data object Inactive : ModuleStatus
}

data class SettingsUiState(
    val status: ModuleStatus = ModuleStatus.Checking,
    val values: Map<String, Any> = emptyMap(),
) {
    /** Settings can only be changed when there is a framework to store them in. */
    val editable: Boolean get() = status is ModuleStatus.Active
}

/**
 * Holds what the settings screen shows.
 *
 * The screen never sees the framework bridge: it reads values out of the state
 * and reports changes back here, so an inactive framework is one state value
 * rather than a special case spread over the UI.
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var store: SettingsStore? = null
    private var observation: AutoCloseable? = null

    init {
        viewModelScope.launch { connect() }
    }

    fun set(setting: BoolSetting, value: Boolean) {
        store?.put(setting, value)
        refreshValues()
    }

    fun set(setting: IntSetting, value: Int) {
        store?.put(setting, value)
        refreshValues()
    }

    override fun onCleared() {
        observation?.close()
        super.onCleared()
    }

    private suspend fun connect() {
        val service = withTimeoutOrNull(FRAMEWORK_GRACE_MILLIS) {
            ModuleConnection.service.filterNotNull().first()
        }

        if (service == null) {
            _uiState.value = SettingsUiState(status = ModuleStatus.Inactive)
            return
        }

        val opened = runCatching {
            SharedPreferencesStore(service.getRemotePreferences(FeatureCatalog.SETTINGS_GROUP))
        }.getOrNull()

        if (opened == null) {
            _uiState.value = SettingsUiState(status = ModuleStatus.Inactive)
            return
        }

        store = opened
        observation = opened.observe(::refreshValues)

        _uiState.value = SettingsUiState(
            status = ModuleStatus.Active(
                service.frameworkName,
                service.frameworkVersionCode.toString(),
            ),
            values = readValues(opened),
        )
    }

    private fun refreshValues() {
        val current = store ?: return
        _uiState.value = _uiState.value.copy(values = readValues(current))
    }

    private fun readValues(store: SettingsStore): Map<String, Any> =
        FeatureCatalog.entries.associate { entry -> entry.setting.key to store.read(entry.setting) }

    private fun SettingsStore.read(setting: Setting<*>): Any = when (setting) {
        is BoolSetting -> get(setting)
        is IntSetting -> get(setting)
    }

    private companion object {
        /** A framework that has not answered by now is not installed. */
        const val FRAMEWORK_GRACE_MILLIS = 1_500L
    }
}
