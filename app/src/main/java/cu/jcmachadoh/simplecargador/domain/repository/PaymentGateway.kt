package cu.jcmachadoh.simplecargador.domain.repository

import cu.jcmachadoh.simplecargador.domain.model.PaymentState
import cu.jcmachadoh.simplecargador.domain.model.PaymentTicket

interface PaymentGateway {

    /** Crea una solicitud de pago en Enzona. Único momento que requiere internet (RF-12). */
    suspend fun createPayment(amountCup: Double, invoiceNumber: String): PaymentTicket

    /** Consulta el estado actual de la transacción. */
    suspend fun checkPayment(transactionUuid: String): PaymentState

    /** Intenta confirmar/completar el pago en Enzona. */
    suspend fun completePayment(transactionUuid: String): PaymentState
}
