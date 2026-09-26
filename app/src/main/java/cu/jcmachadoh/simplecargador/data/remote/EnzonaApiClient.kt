package cu.jcmachadoh.simplecargador.data.remote

import cu.jcmachadoh.simplecargador.data.remote.api.EnzonaApi
import cu.jcmachadoh.simplecargador.data.remote.model.CreatePaymentRequest
import cu.jcmachadoh.simplecargador.data.remote.model.CreatePaymentResponse
import cu.jcmachadoh.simplecargador.data.remote.model.PaymentStatusResponse
import cu.jcmachadoh.simplecargador.data.remote.model.TokenResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Único punto de red de toda la app (RNF-02): se usa exclusivamente durante el pago.
 * Claves desencriptadas en memoria solo al instante de llamar (utils.security.ObfuscatedKeys).
 */
class EnzonaApiClient {

    val api: EnzonaApi by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (EnzonaConfig.USE_MOCK) {
            clientBuilder.addInterceptor(EnzonaMockInterceptor())
        }
        val client = clientBuilder.build()
        Retrofit.Builder()
            .baseUrl(EnzonaConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EnzonaApi::class.java)
    }

    suspend fun token(): TokenResponse =
        api.requestToken(basicAuth = "Basic ${basicCredentials()}")

    suspend fun createPayment(
        description: String,
        amount: String,
        invoiceNumber: String
    ): CreatePaymentResponse {
        val bearer = "Bearer ${token().access_token}"
        return api.createPayment(
            bearerAuth = bearer,
            body = CreatePaymentRequest(
                description = description,
                currency = EnzonaConfig.CURRENCY,
                amount = amount,
                merchant_uuid = ObfuscatedKeysCompat.merchantUuid(),
                invoice_number = invoiceNumber,
                return_url = EnzonaConfig.RETURN_URL,
                cancel_url = EnzonaConfig.CANCEL_URL
            )
        )
    }

    suspend fun paymentStatus(transactionUuid: String): PaymentStatusResponse {
        val bearer = "Bearer ${token().access_token}"
        return api.getPaymentStatus(bearer, transactionUuid)
    }

    suspend fun completePayment(transactionUuid: String): PaymentStatusResponse {
        val bearer = "Bearer ${token().access_token}"
        return api.completePayment(bearer, transactionUuid, "{}".toRequestBody(null))
    }

    private fun basicCredentials(): String {
        val raw = "${ObfuscatedKeysCompat.consumerKey()}:${ObfuscatedKeysCompat.consumerSecret()}"
        return android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
    }
}

/** Códigos de estado documentados de la pasarela Enzona. */
object EnzonaStatusCodes {
    const val COMPLETED = "1001"
    const val PENDING = "1000"
    const val CANCELLED = "1002"
}

/** Envoltorio para no exponer utils.security directamente desde la capa remota. */
private object ObfuscatedKeysCompat {
    fun consumerKey() = cu.jcmachadoh.simplecargador.utils.security.ObfuscatedKeys.consumerKey()
    fun consumerSecret() = cu.jcmachadoh.simplecargador.utils.security.ObfuscatedKeys.consumerSecret()
    fun merchantUuid() = cu.jcmachadoh.simplecargador.utils.security.ObfuscatedKeys.merchantUuid()
}
