package cu.jcmachadoh.simplecargador.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.domain.model.BatterySnapshot
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.utils.extensions.startOfTodayMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val battery: BatterySnapshot? = null,
    val lastSessions: List<ChargingSession> = emptyList(),
    val todaySessions: List<ChargingSession> = emptyList(),
    val inProgress: ChargingSession? = null,
    val isLoading: Boolean = true
) {
    val todayTotalSeconds: Long
        get() {
            val completed = todaySessions.sumOf { it.durationSeconds ?: 0L }
            val live = inProgress?.let {
                ((System.currentTimeMillis() - it.startTimeMillis).coerceAtLeast(0)) / 1000
            } ?: 0L
            return completed + live
        }

    /** Ganado hoy = sesiones cerradas de hoy + ganancia en vivo de la sesión actual. */
    val todayGainedPercent: Int
        get() {
            val completed = todaySessions.sumOf { it.gainedPercent ?: 0 }
            val live = inProgress?.let { ip ->
                battery?.let { b -> (b.levelPercent - ip.startLevelPercent).coerceAtLeast(0) } ?: 0
            } ?: 0
            return completed + live
        }

    val isCharging: Boolean get() = battery?.isCharging == true
    val levelPercent: Int get() = battery?.levelPercent ?: 0
}

class HomeViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.sessionRepository
    private val reader = container.batteryReader

    val uiState: StateFlow<HomeUiState> =
        combine(
            reader.observeChanges(),
            repository.observeCompletedLast(10),
            repository.observeCompletedInRange(startOfTodayMillis(), Long.MAX_VALUE),
            repository.observeInProgress()
        ) { snap, last, todayCompleted, inProgress ->
            HomeUiState(
                battery = snap,
                lastSessions = last,
                todaySessions = todayCompleted,
                inProgress = inProgress,
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
