package cu.jcmachadoh.simplecargador.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.ui.components.BatteryRing
import cu.jcmachadoh.simplecargador.ui.components.LastSessionsBarChart
import cu.jcmachadoh.simplecargador.ui.components.SectionCard
import cu.jcmachadoh.simplecargador.ui.components.StatTile
import cu.jcmachadoh.simplecargador.ui.components.StatusPill
import cu.jcmachadoh.simplecargador.ui.components.batteryLevelColor
import cu.jcmachadoh.simplecargador.ui.containerViewModel
import cu.jcmachadoh.simplecargador.ui.theme.LocalStateColors
import cu.jcmachadoh.simplecargador.utils.extensions.formatShortDuration
import cu.jcmachadoh.simplecargador.utils.extensions.signedPercent

@Composable
fun HomeScreen(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(container, ::HomeViewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val states = LocalStateColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Héroe: anillo + píldora de estado ──────────────────────────
            BatteryRing(
                levelPercent = state.levelPercent,
                isCharging = state.isCharging
            ) {
                    val displayedPercent by animateIntAsState(
                        targetValue = state.levelPercent,
                        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
                        label = "percentCountUp"
                    )
                    val screenW = LocalConfiguration.current.screenWidthDp
                    val percentFont = (screenW * 0.105f).sp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Text(
                        text = "$displayedPercent%",
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = percentFont),
                        fontWeight = FontWeight.Bold,
                        color = batteryLevelColor(state.levelPercent)
                    )
                }
            }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = state.isCharging,
            transitionSpec = {
                (fadeIn(tween(200, easing = EaseOutCubic)) +
                    slideInVertically(tween(220, easing = EaseOutCubic)) { it / 3 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "statusPill"
        ) { charging ->
            StatusPill(
                text = if (charging) stringResource(R.string.home_charging_now)
                else stringResource(R.string.home_no_power),
                container = if (charging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                content = if (charging) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Métricas del día: composición asimétrica ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(250, easing = EaseOutCubic)),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                title = stringResource(R.string.home_gained_today),
                value = state.todayGainedPercent.signedPercent(),
                emphasized = true,
                modifier = Modifier.weight(1f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    title = stringResource(R.string.home_charges_today),
                    value = "${state.todaySessions.size}",
                    modifier = Modifier.fillMaxWidth()
                )
                StatTile(
                    title = stringResource(R.string.home_time_powered),
                    value = state.todayTotalSeconds.formatShortDuration(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Gráfico de las últimas 10 cargas (RF-04) ───────────────────
        SectionCard(
            title = stringResource(R.string.home_last_10),
            helpText = stringResource(R.string.home_last_10_help)
        ) {
            if (state.lastSessions.isEmpty()) {
                EmptyChartHint()
            } else {
                LastSessionsBarChart(sessions = state.lastSessions)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "50%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyChartHint() {
    Text(
        text = stringResource(R.string.home_empty_chart),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 14.dp, horizontal = 2.dp)
    )
}
