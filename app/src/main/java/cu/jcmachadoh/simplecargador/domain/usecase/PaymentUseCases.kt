package cu.jcmachadoh.simplecargador.domain.usecase

import cu.jcmachadoh.simplecargador.data.local.preferences.PendingPaymentStore
import cu.jcmachadoh.simplecargador.domain.model.PaymentState
import cu.jcmachadoh.simplecargador.domain.model.PaymentTicket
import cu.jcmachadoh.simplecargador.domain.repository.LicenseRepository
import cu.jcmachadoh.simplecargador.domain.repository.PaymentGateway
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RF-12: flujo de pago único con Enzona.
 * Internet se usa SOLO aquí; la licencia resultante es perpetua y local.
 */
class ProcessPaymentUseCase(
    private val gateway: PaymentGateway,
    private val licenseRepository: LicenseRepository,
    private val pendingStore: PendingPaymentStore? = null
) {

    sealed interface Result {
        data class Created(val ticket: PaymentTicket) : Result
        data object Confirmed : Result
        data class Error(val message: String) : Result
    }

    suspend fun createPayment(amountCup: Double): Result {
        return try {
            val invoice = "SC-" + SimpleDateFormat("yyMMddHHmmss", Locale.US).format(Date())
            val ticket = gateway.createPayment(amountCup, invoice)
            // Persistir pendiente (vital por si el usuario cierra la app mientras paga)
            pendingStore?.savePending(ticket.transactionUuid, ticket.invoiceNumber)
            Result.Created(ticket)
        } catch (e: Exception) {
            Result.Error("No se pudo crear el pago: ${e.message ?: "sin conexión"}")
        }
    }

    /** El usuario pulsa "Ya pagué" o vuelve por deep link/onResume: verificamos y confirmamos en Enzona. */
    suspend fun verifyPayment(transactionUuid: String): Result {
        return try {
            var state = gateway.completePayment(transactionUuid)
            if (state == PaymentState.PENDING || state == PaymentState.UNKNOWN) {
                state = gateway.checkPayment(transactionUuid)
            }
            when (state) {
                PaymentState.COMPLETED -> {
                    licenseRepository.activate(transactionUuid)
                    pendingStore?.clear()
                    Result.Confirmed
                }
                PaymentState.PENDING -> Result.Error("El pago sigue pendiente en Enzona.")
                else -> Result.Error("El pago no se ha completado (estado: $state).")
            }
        } catch (e: Exception) {
            Result.Error("Error verificando el pago: ${e.message ?: "sin conexión"}")
        }
    }

    /** Limpia el pendiente si el usuario cancela. */
    fun cancelPending() { pendingStore?.clear() }
}

/** RF-13: verificación local instantánea al arrancar, sin conectividad. */
class CheckLicenseUseCase(private val licenseRepository: LicenseRepository) {
    fun isPremiumNow(): Boolean = licenseRepository.isPremium.value
}
