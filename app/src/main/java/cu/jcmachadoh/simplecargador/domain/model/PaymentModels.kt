package cu.jcmachadoh.simplecargador.domain.model

enum class PaymentState { PENDING, COMPLETED, CANCELLED, FAILED, UNKNOWN }

data class PaymentTicket(
    val transactionUuid: String,
    val invoiceNumber: String,
    val amountCup: Double,
    /** Link de confirmación (approval_url) devuelto por Enzona. Null si la API no lo devolvió. */
    val linkConfirm: String? = null
)
