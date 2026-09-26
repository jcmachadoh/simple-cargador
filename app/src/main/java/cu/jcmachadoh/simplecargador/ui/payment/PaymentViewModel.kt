package cu.jcmachadoh.simplecargador.ui.payment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.jcmachadoh.simplecargador.data.remote.PaymentLauncher
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.domain.model.PaymentTicket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentUiState(
    val step: Step = Step.IDLE,
    val ticket: PaymentTicket? = null,
    val message: String? = null,
    val isPremium: Boolean = false
) {
    enum class Step { IDLE, CREATING, AWAITING_USER, VERIFYING, DONE, ERROR }
}

class PaymentViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PaymentUiState())
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()

    /** Precio único del Premium en CUP. */
    val priceCup: Double = 150.0

    init {
        val premium = container.checkLicenseUseCase.isPremiumNow()
        // Restaurar pendiente si existe y aún no es premium
        val pendingUuid = container.pendingPaymentStore.getPendingUuid()
        val pendingInvoice = container.pendingPaymentStore.getPendingInvoice()
        if (!premium && pendingUuid != null) {
            _state.value = _state.value.copy(
                isPremium = false,
                step = PaymentUiState.Step.AWAITING_USER,
                ticket = PaymentTicket(
                    transactionUuid = pendingUuid,
                    invoiceNumber = pendingInvoice ?: "SC-PENDING",
                    amountCup = priceCup,
                    linkConfirm = null
                ),
                message = container.appContext.getString(
                    cu.jcmachadoh.simplecargador.R.string.pay_msg_waiting,
                    pendingInvoice ?: pendingUuid
                )
            )
        } else {
            _state.value = _state.value.copy(isPremium = premium)
        }
    }

    /** RF-12 paso 1-2: crear solicitud (único momento con internet). */
    fun startPayment() {
        if (_state.value.step == PaymentUiState.Step.CREATING) return
        viewModelScope.launch {
            _state.value = _state.value.copy(step = PaymentUiState.Step.CREATING, message = null)
            when (val result = container.processPaymentUseCase.createPayment(priceCup)) {
                is cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase.Result.Created -> {
                    _state.value = _state.value.copy(
                        step = PaymentUiState.Step.AWAITING_USER,
                        ticket = result.ticket,
                        message = container.appContext.getString(
                            cu.jcmachadoh.simplecargador.R.string.pay_msg_waiting,
                            result.ticket.invoiceNumber
                        )
                    )
                }
                is cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase.Result.Error -> {
                    _state.value = _state.value.copy(step = PaymentUiState.Step.ERROR, message = result.message)
                }
                else -> Unit
            }
        }
    }

    /** Intenta abrir la pasarela (Enzona app o navegador) con el link devuelto en el Paso 2. */
    fun launchPayment(context: Context): Boolean {
        val link = _state.value.ticket?.linkConfirm ?: return false
        return PaymentLauncher.openPayment(context, link)
    }

    /** RF-12 paso 3-4: el usuario ya autorizó; verificamos y guardamos licencia perpetua. */
    fun verifyPayment() {
        // Prioriza ticket en memoria; si no, cae al pendiente persistido (onResume / deep link)
        val uuid = _state.value.ticket?.transactionUuid
            ?: container.pendingPaymentStore.getPendingUuid()
            ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(step = PaymentUiState.Step.VERIFYING, message = null)
            when (val result = container.processPaymentUseCase.verifyPayment(uuid)) {
                is cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase.Result.Confirmed ->
                    _state.value = _state.value.copy(
                        step = PaymentUiState.Step.DONE, isPremium = true,
                        message = container.appContext.getString(
                            cu.jcmachadoh.simplecargador.R.string.pay_msg_activated
                        )
                    )
                is cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase.Result.Error ->
                    _state.value = _state.value.copy(
                        step = PaymentUiState.Step.AWAITING_USER, message = result.message
                    )
                else -> Unit
            }
        }
    }

    /** Verificación disparada desde MainActivity.onResume / deep link global (aunque no esté en PaymentScreen). */
    fun verifyPendingGlobal(onResult: (Boolean) -> Unit = {}) {
        val uuid = container.pendingPaymentStore.getPendingUuid() ?: return
        viewModelScope.launch {
            when (container.processPaymentUseCase.verifyPayment(uuid)) {
                is cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase.Result.Confirmed -> {
                    _state.value = _state.value.copy(isPremium = true, step = PaymentUiState.Step.DONE)
                    onResult(true)
                }
                else -> onResult(false)
            }
        }
    }

    fun cancelPending() {
        container.processPaymentUseCase.cancelPending()
        _state.value = _state.value.copy(step = PaymentUiState.Step.IDLE, ticket = null, message = null)
    }
}
