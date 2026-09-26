package cu.jcmachadoh.simplecargador.data.remote.api

import cu.jcmachadoh.simplecargador.data.remote.model.CreatePaymentRequest
import cu.jcmachadoh.simplecargador.data.remote.model.CreatePaymentResponse
import cu.jcmachadoh.simplecargador.data.remote.model.PaymentStatusResponse
import cu.jcmachadoh.simplecargador.data.remote.model.TokenResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EnzonaApi {

    @POST("token")
    suspend fun requestToken(
        @Header("Authorization") basicAuth: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): TokenResponse

    @POST("payment/v1.0/payments")
    suspend fun createPayment(
        @Header("Authorization") bearerAuth: String,
        @Body body: CreatePaymentRequest
    ): CreatePaymentResponse

    @GET("payment/v1.0/payments/{uuid}")
    suspend fun getPaymentStatus(
        @Header("Authorization") bearerAuth: String,
        @Path("uuid") transactionUuid: String
    ): PaymentStatusResponse

    @POST("payment/v1.0/payments/{uuid}/complete")
    suspend fun completePayment(
        @Header("Authorization") bearerAuth: String,
        @Path("uuid") transactionUuid: String,
        @Body emptyBody: RequestBody
    ): PaymentStatusResponse
}
