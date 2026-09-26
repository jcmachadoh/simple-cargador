package cu.jcmachadoh.simplecargador.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.domain.model.BatteryHealth
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.domain.usecase.CalculateHealthUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val sessions: List<ChargingSession> = emptyList(),
    val health: BatteryHealth? = null,
    val isLoading: Boolean = true
) {
    /** Serie nivel final vs tiempo (normalizado 0..1). */
    val levelTimeSeries: List<Pair<Float, Float>>
        get() = normalize(sessions.filter { it.endLevelPercent != null }.map {
            it.startTimeMillis to it.endLevelPercent!!.toFloat()
        })

    /** Dispersión duración (min) vs % ganado. */
    val durationGainPoints: List<Pair<Float, Float>>
        get() = sessions.filter { it.durationSeconds != null && it.gainedPercent != null }
            .map { (it.durationSeconds!! / 60f) to it.gainedPercent!!.toFloat() }

    private fun normalize(list: List<Pair<Long, Float>>): List<Pair<Float, Float>> {
        if (list.size < 2) return emptyList()
        val minX = list.minOf { it.first }.toFloat()
        val maxX = list.maxOf { it.first }.toFloat()
        val span = (maxX - minX).coerceAtLeast(1f)
        return list.map { ((it.first - minX) / span) to it.second }
    }
}

class StatsViewModel(container: AppContainer) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = container.sessionRepository.observeAll()
        .map { all ->
            StatsUiState(
                sessions = all,
                health = CalculateHealthUseCase.fromSessions(
                    sessions = all,
                    nominalCapacityMah = container.batteryReader.nominalCapacityMah(),
                    nowMillis = System.currentTimeMillis(),
                    fullChargeCapacityMah = container.batteryReader.fullChargeCapacityMah()
                ),
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
