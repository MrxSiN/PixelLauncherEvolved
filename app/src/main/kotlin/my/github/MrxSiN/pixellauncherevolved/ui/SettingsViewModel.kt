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

import my.github.MrxSiN.pixellauncherevolved.settings.ModuleConnection

/** Whether the framework is present and has accepted this module. */
sealed interface ModuleStatus {
    data object Checking : ModuleStatus
    data class Active(val frameworkName: String, val frameworkBuild: String) : ModuleStatus
    data object Inactive : ModuleStatus
}

/**
 * Holds what this app has left to say.
 *
 * The tweaks themselves live in the launcher's own Home settings, where they
 * are stored beside the launcher that reads them. What is left here is the one
 * question this app can answer and Home settings cannot: whether a framework
 * picked the module up at all.
 */
class SettingsViewModel : ViewModel() {

    private val _status = MutableStateFlow<ModuleStatus>(ModuleStatus.Checking)
    val status: StateFlow<ModuleStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect() {
        val service = withTimeoutOrNull(FRAMEWORK_GRACE_MILLIS) {
            ModuleConnection.service.filterNotNull().first()
        }

        _status.value = if (service == null) {
            ModuleStatus.Inactive
        } else {
            ModuleStatus.Active(service.frameworkName, service.frameworkVersionCode.toString())
        }
    }

    private companion object {
        /** A framework that has not answered by now is not installed. */
        const val FRAMEWORK_GRACE_MILLIS = 1_500L
    }
}
