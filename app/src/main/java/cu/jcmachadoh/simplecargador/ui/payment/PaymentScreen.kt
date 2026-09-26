package cu.jcmachadoh.simplecargador.ui.payment

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.data.remote.EnzonaConfig
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.ui.containerViewModel

@Composable
fun PaymentScreen(
    container: AppContainer,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(container, ::PaymentViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var launchedFor by remember { mutableStateOf<String?>(null) }

    // Paso 3: al obtener link_confirm, intenta abrir Enzona o navegador (solo una vez por transacción)
    val linkConfirm = state.ticket?.linkConfirm
    LaunchedEffect(linkConfirm, state.step) {
        if (linkConfirm != null &&
            state.step == PaymentUiState.Step.AWAITING_USER &&
            launchedFor != linkConfirm
        ) {
            launchedFor = linkConfirm
            viewModel.launchPayment(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }
            Text(
                stringResource(R.string.settings_back),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.pay_unlock_premium), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(
                R.string.pay_price_desc,
                "%.2f".format(viewModel.priceCup)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isPremium) {
            Text(
                stringResource(R.string.pay_premium_active),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        if (EnzonaConfig.USE_MOCK) {
            Text(
                "Modo pruebas activo: no se realiza ningún cobro real.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            )
        }

        when (state.step) {
            PaymentUiState.Step.CREATING -> CircularProgressIndicator()
            PaymentUiState.Step.VERIFYING -> CircularProgressIndicator()
            else -> Button(
                onClick = viewModel::startPayment,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.step != PaymentUiState.Step.AWAITING_USER
            ) {
                Text(
                    if (state.step == PaymentUiState.Step.AWAITING_USER)
                        stringResource(R.string.pay_waiting)
                    else stringResource(R.string.pay_with_enzona)
                )
            }
        }

        if (state.step == PaymentUiState.Step.AWAITING_USER && state.ticket != null) {
            OutlinedButton(onClick = viewModel::verifyPayment, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pay_verify))
            }
            if (linkConfirm != null) {
                OutlinedButton(
                    onClick = { viewModel.launchPayment(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir Enzona de nuevo") }
            }
        }

        state.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.step == PaymentUiState.Step.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(90.dp))
    }
}
