package cu.jcmachadoh.simplecargador.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.utils.extensions.formatFullDate
import cu.jcmachadoh.simplecargador.utils.extensions.formatShortDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<ChargingSession> = emptyList(),
    val isLoading: Boolean = true,
    val exportMessage: String? = null
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = container.sessionRepository.observeAll()
        .map { HistoryUiState(sessions = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** RF-10: exporta a CSV y JSON y comparte por cualquier medio. */
    fun exportAndShare(onFiles: (java.io.File, java.io.File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = container.sessionRepository.getAllOnce()
            val dir = container.cacheDir
            val (csv, json) =
                cu.jcmachadoh.simplecargador.domain.usecase.ExportHistoryUseCase.writeFiles(
                    sessions, dir, System.currentTimeMillis()
                )
            onFiles(csv, json)
        }
    }
}

/** Fila lista para renderizar en la pantalla. */
data class SessionRow(
    val id: Long,
    val title: String,
    val detail: String,
    val gained: Int?,
    val endLevel: Int?,
    val inProgress: Boolean
)

fun ChargingSession.toRow(inProgressLabel: String): SessionRow {
    val durationText = if (inProgress) inProgressLabel else (durationSeconds?.formatShortDuration() ?: "")
    return SessionRow(
        id = id,
        title = startTimeMillis.formatFullDate(),
        detail = "$startLevelPercent% → ${endLevelPercent ?: "?"}% · $durationText",
        gained = gainedPercent,
        endLevel = endLevelPercent,
        inProgress = inProgress
    )
}
