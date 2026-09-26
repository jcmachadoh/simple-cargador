package cu.jcmachadoh.simplecargador.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.ui.containerViewModel
import kotlin.math.roundToInt

/**
 * Vista de configuración abierta desde la barra superior: selector de idioma,
 * intervalo del widget y actualización de la app.
 */
@Composable
fun SettingsView(
    container: AppContainer,
    onChangeLanguage: () -> Unit,
    onGoToPayment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var language by rememberSaveable {
        mutableStateOf(container.settingsStore.languageCode)
    }
    val widgetInterval by container.settingsStore.widgetUpdateIntervalMinutesFlow.collectAsStateWithLifecycle()

    val viewModel: UpdateViewModel = containerViewModel(container) { c ->
        UpdateViewModel(c.appUpdater)
    }
    val updateState by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )

        // ── Idioma ──
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageRow(
            name = stringResource(R.string.settings_spanish),
            selected = language == "es",
            onClick = {
                language = "es"
                container.settingsStore.languageCode = "es"
                onChangeLanguage()
            }
        )
        LanguageRow(
            name = stringResource(R.string.settings_english),
            selected = language == "en",
            onClick = {
                language = "en"
                container.settingsStore.languageCode = "en"
                onChangeLanguage()
            }
        )

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
                    // Snap to 5-min increments for cleaner values
                    val stepped = ((snapped + 2) / 5) * 5
                    container.settingsStore.widgetUpdateIntervalMinutes = stepped.coerceIn(5, 60)
                },
                valueRange = 5f..60f,
                steps = 10, // 5-min increments: 5,10,15...60 (10 intervals)
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

        // ── Actualización ──
        Text(stringResource(R.string.settings_update), style = MaterialTheme.typography.titleMedium)
        UpdateSection(state = updateState, viewModel = viewModel, updater = container.appUpdater)

        HorizontalDivider()

        // ── Acerca de / Premium ──
        val aboutOpen = remember { mutableStateOf(false) }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 320.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { aboutOpen.value = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.settings_about_title)) }
                    Button(
                        onClick = onGoToPayment,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.settings_about_premium)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { aboutOpen.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_about_title)) }
                    Button(
                        onClick = onGoToPayment,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_about_premium)) }
                }
            }
        }

        if (aboutOpen.value) {
            AlertDialog(
                onDismissRequest = { aboutOpen.value = false },
                confirmButton = {
                    TextButton(onClick = { aboutOpen.value = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.settings_about_title)) },
                text = { Text(stringResource(R.string.settings_about_body)) }
            )
        }
    }
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    updater: cu.jcmachadoh.simplecargador.data.update.AppUpdater
) {
    when (state) {
        UpdateUiState.Idle -> {
            OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
                Text(stringResource(R.string.settings_check_updates))
            }
        }
        UpdateUiState.Checking -> {
            withProgress(R.string.settings_checking)
        }
        UpdateUiState.UpToDate -> {
            Button(onClick = { viewModel.checkForUpdate() }) {
                Text(stringResource(R.string.settings_up_to_date))
            }
        }
        is UpdateUiState.Available -> {
            Text(
                text = stringResource(R.string.settings_new_version, state.update.versionName)
            )
            Text(
                text = state.update.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (updater.canRequestPackageInstalls()) {
                Button(onClick = { viewModel.downloadAndInstall() }) {
                    Text(stringResource(R.string.settings_download_install))
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_need_install_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { updater.openInstallPermissionSettings() }) {
                    Text(stringResource(R.string.settings_allow_install))
                }
            }
        }
        UpdateUiState.Downloading -> {
            withProgress(R.string.settings_downloading)
        }
        UpdateUiState.Installing -> {
            Button(onClick = { viewModel.checkForUpdate() }) {
                Text(stringResource(R.string.settings_installer_open))
            }
        }
        UpdateUiState.InstallFailed -> {
            OutlinedButton(onClick = { viewModel.downloadAndInstall() }) {
                Text(stringResource(R.string.settings_install_failed))
            }
        }
        is UpdateUiState.Error -> {
            Text(
                text = stringResource(R.string.settings_error),
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
                Text(stringResource(R.string.settings_retry))
            }
        }
    }
}

@Composable
private fun withProgress(@StringRes message: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
        Text(stringResource(message))
    }
}
