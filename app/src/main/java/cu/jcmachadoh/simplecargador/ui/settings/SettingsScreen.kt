package cu.jcmachadoh.simplecargador.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.ui.theme.ThemeMode
import kotlin.math.roundToInt

/** RF-06: tema manual (Premium) + alertas; la gratuita sigue el sistema. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onGoToPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPremium by container.licenseRepository.isPremium.collectAsStateWithLifecycle()
    val themeMode by container.settingsStore.themeMode.collectAsStateWithLifecycle()
    val widgetInterval by container.settingsStore.widgetUpdateIntervalMinutesFlow.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var alertsEnabled by remember {
        mutableStateOf(container.settingsStore.alertsEnabled)
    }
    var monitorEnabled by remember {
        mutableStateOf(container.settingsStore.monitorEnabled)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isPremium) "Elige tu tema favorito."
                else "En la versión gratuita el tema sigue al sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { container.settingsStore.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        enabled = isPremium || mode == ThemeMode.SYSTEM,
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alertas inteligentes", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isPremium) "Carga lenta, 80% y nivel crítico."
                    else "Premium requerido para alertas inteligentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isPremium) {
                    Text(
                        "Desbloquea Premium para activarlas.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(
                checked = alertsEnabled && isPremium,
                enabled = isPremium,
                onCheckedChange = {
                    alertsEnabled = it
                    container.settingsStore.alertsEnabled = it
                }
            )
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Monitor permanente", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mantiene un servicio silencioso que garantiza registrar TODAS las cargas, " +
                        "incluso con las restricciones de Xiaomi, Realme o Huawei. Consumo casi nulo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = monitorEnabled,
                onCheckedChange = { enabled ->
                    monitorEnabled = enabled
                    container.settingsStore.monitorEnabled = enabled
                    if (enabled) {
                        (context.applicationContext as? cu.jcmachadoh.simplecargador.SimpleCargadorApp)?.startMonitor()
                    } else {
                        context.stopService(
                            Intent(
                                context,
                                cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService::class.java
                            )
                        )
                    }
                }
            )
        }

        HorizontalDivider()

        // ── Widget: intervalo de refresco (Slider 5–60 min) ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_widget_interval),
                    style = MaterialTheme.typography.titleMedium
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            stringResource(R.string.settings_widget_interval_current, widgetInterval),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            Text(
                stringResource(R.string.settings_widget_interval_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = widgetInterval.toFloat(),
                onValueChange = { v ->
                    val snapped = v.roundToInt().coerceIn(5, 60)
                    val stepped = ((snapped + 2) / 5) * 5
                    container.settingsStore.widgetUpdateIntervalMinutes = stepped.coerceIn(5, 60)
                },
                valueRange = 5f..60f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_widget_interval_value, 5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.settings_widget_interval_value, 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Fiabilidad de registro", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Si tu teléfono cierra la app al cargar (Xiaomi, Realme, Huawei…), " +
                        "permítele funcionar sin optimización de batería.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    try {
                        val i = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(i)
                    } catch (_: Throwable) { /* ROM sin esa pantalla */ }
                }
            ) {
                Text("Permitir")
            }
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isPremium) "Versión Premium ✔" else "Versión Gratuita",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (!isPremium) {
                Button(onClick = onGoToPayment) { Text("Desbloquear") }
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Sistema"
        ThemeMode.LIGHT -> "Claro"
        ThemeMode.DARK -> "Oscuro"
    }
