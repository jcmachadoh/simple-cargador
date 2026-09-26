package cu.jcmachadoh.simplecargador.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.jcmachadoh.simplecargador.data.update.AppUpdater
import cu.jcmachadoh.simplecargador.data.update.UpdateCheckResult
import cu.jcmachadoh.simplecargador.data.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estados visibles del botón "Buscar actualizaciones". */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object Downloading : UpdateUiState
    data object Installing : UpdateUiState
    data class Available(val update: UpdateInfo) : UpdateUiState
    data object InstallFailed : UpdateUiState
    data class Error(val message: String?) : UpdateUiState
}

/**
 * Controla la comprobación de versiones, la descarga y la instalación de la APK.
 * Se alimenta de [AppUpdater] (RNF-09: DI manual vía fábrica del contenedor).
 */
class UpdateViewModel(private val updater: AppUpdater) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate() {
        if (_state.value == UpdateUiState.Checking || _state.value == UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = updater.checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> _state.value = UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> _state.value = UpdateUiState.Available(result.update)
                is UpdateCheckResult.Error -> _state.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadAndInstall() {
        val current = _state.value as? UpdateUiState.Available ?: return
        _state.value = UpdateUiState.Downloading
        viewModelScope.launch {
            val ok = updater.downloadAndInstall(current.update)
            _state.value = if (ok) UpdateUiState.Installing else UpdateUiState.InstallFailed
        }
    }
}
