package cu.jcmachadoh.simplecargador.data.remote

import cu.jcmachadoh.simplecargador.data.remote.EnzonaStatusCodes
import cu.jcmachadoh.simplecargador.domain.model.PaymentState
import cu.jcmachadoh.simplecargador.domain.model.PaymentTicket
import cu.jcmachadoh.simplecargador.domain.repository.PaymentGateway
import java.util.Locale

class PaymentGatewayImpl(private val client: EnzonaApiClient) : PaymentGateway {

    override suspend fun createPayment(amountCup: Double, invoiceNumber: String): PaymentTicket {
        val response = client.createPayment(
            description = DESCRIPTION,
            amount = String.format(Locale.US, "%.2f", amountCup),
            invoiceNumber = invoiceNumber
        )
        val uuid = response.transaction_uuid
            ?: throw IllegalStateException(response.message ?: "Enzona no devolvió transacción")
        return PaymentTicket(
            transactionUuid = uuid,
            invoiceNumber = invoiceNumber,
            amountCup = amountCup,
            linkConfirm = response.linkConfirm()
        )
    }

    override suspend fun checkPayment(transactionUuid: String): PaymentState =
        mapState(client.paymentStatus(transactionUuid).status_code)

    override suspend fun completePayment(transactionUuid: String): PaymentState =
        mapState(client.completePayment(transactionUuid).status_code)

    private fun mapState(code: String?): PaymentState = when (code) {
        EnzonaStatusCodes.COMPLETED -> PaymentState.COMPLETED
        EnzonaStatusCodes.PENDING -> PaymentState.PENDING
        EnzonaStatusCodes.CANCELLED -> PaymentState.CANCELLED
        null -> PaymentState.UNKNOWN
        else -> PaymentState.FAILED
    }

    private companion object {
        const val DESCRIPTION = "Simple Cargador - Versión Premium (pago único)"
    }
}
