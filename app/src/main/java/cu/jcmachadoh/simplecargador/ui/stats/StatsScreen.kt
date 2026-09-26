package cu.jcmachadoh.simplecargador.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.ui.components.BatteryRing
import cu.jcmachadoh.simplecargador.ui.components.LineChart
import cu.jcmachadoh.simplecargador.ui.components.ScatterChart
import cu.jcmachadoh.simplecargador.ui.components.SectionCard
import cu.jcmachadoh.simplecargador.ui.containerViewModel
import cu.jcmachadoh.simplecargador.ui.theme.LocalStateColors

/**
 * RF-07 / RF-08: estadísticas avanzadas (Premium).
 * Con PREMIUM_ENFORCED=false todo es visible; el gateo se activa al final (T5.1).
 */
object PremiumGate {
    const val ENFORCED = true
}

@Composable
fun StatsScreen(
    container: AppContainer,
    onGoToPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPremium by container.licenseRepository.isPremium.collectAsStateWithLifecycle()
    val viewModel = containerViewModel(container, ::StatsViewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (PremiumGate.ENFORCED && !isPremium) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.stats_premium_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onGoToPayment) { Text(stringResource(R.string.stats_unlock_premium)) }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Héroe: salud estimada (RF-08) ──────────────────────────────
        val health = state.health
        SectionCard(
            title = stringResource(R.string.stats_health_title),
            helpText = stringResource(R.string.stats_health_help)
        ) {
            if (health == null) {
                Text(
                    stringResource(R.string.stats_health_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                )
            } else {
                val healthPercent = health.healthPercent.coerceIn(0f, 100f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BatteryRing(
                        levelPercent = healthPercent.toInt(),
                        isCharging = false,
                        ringSize = 148.dp,
                        strokeWidth = 12.dp
                    ) {
                        Text(
                            "${"%.0f".format(healthPercent)}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            R.string.stats_capacity_formatted,
                            "%.0f".format(health.estimatedCapacityMah),
                            health.nominalCapacityMah.toString()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = when {
                            health.weekTrendPercentPoints > 0.5f -> stringResource(R.string.stats_trend_improving)
                            health.weekTrendPercentPoints < -0.5f -> stringResource(R.string.stats_trend_worsening)
                            else -> stringResource(R.string.stats_trend_stable)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (health.weekTrendPercentPoints < -0.5f)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ── Línea: nivel final vs tiempo (RF-07) ───────────────────────
        SectionCard(
            title = stringResource(R.string.stats_level_time_title),
            helpText = stringResource(R.string.stats_level_time_help)
        ) {
            if (state.levelTimeSeries.size < 2) {
                HintText(stringResource(R.string.stats_level_time_min))
            } else {
                LineChart(
                    points = state.levelTimeSeries,
                    lineColor = LocalStateColors.current.charging,
                    dotColor = LocalStateColors.current.charging,
                    fillUnderLine = true
                )
            }
        }

        // ── Dispersión: duración vs ganancia (RF-07) ───────────────────
        SectionCard(
            title = stringResource(R.string.stats_duration_gain_title),
            helpText = stringResource(R.string.stats_duration_gain_help)
        ) {
            if (state.durationGainPoints.isEmpty()) {
                HintText(stringResource(R.string.stats_duration_gain_min))
            } else {
                ScatterChart(
                    points = state.durationGainPoints,
                    dotColor = LocalStateColors.current.mid
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
    )
}
