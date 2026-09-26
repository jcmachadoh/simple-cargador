package cu.jcmachadoh.simplecargador.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String? = null,
    val expires_in: Long? = null
)

@Serializable
data class CreatePaymentRequest(
    val description: String,
    val currency: String,
    /** Monto con 2 decimales como String, formato exigido por Enzona. */
    val amount: String,
    val merchant_uuid: String,
    val invoice_number: String,
    val return_url: String,
    val cancel_url: String,
    @Deprecated("Opcional según pasarela") val terminal_id: String? = null
)

@Serializable
data class EnzonaLink(
    val rel: String? = null,
    val href: String? = null
)

@Serializable
data class CreatePaymentResponse(
    val transaction_uuid: String? = null,
    val status_code: String? = null,
    val message: String? = null,
    val invoice_number: String? = null,
    val links: List<EnzonaLink>? = null,
    // fallback: algunas integraciones devuelven el link directo bajo otro nombre
    val confirm_url: String? = null,
    val approval_url: String? = null
) {
    /** Extrae link_confirm según variantes que devuelve Enzona. */
    fun linkConfirm(): String? = links
        ?.firstOrNull { it.rel == "approval_url" || it.rel == "approve" || it.rel == "confirm" }
        ?.href
        ?: confirm_url
        ?: approval_url
        ?: links?.firstOrNull()?.href
}

@Serializable
data class PaymentStatusResponse(
    val transaction_uuid: String? = null,
    val status_code: String? = null,
    val status_name: String? = null,
    val message: String? = null
)
