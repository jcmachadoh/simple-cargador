package cu.jcmachadoh.simplecargador.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.UUID

/**
 * Interceptor OkHttp que simula TODAS las respuestas de Enzona cuando
 * EnzonaConfig.USE_MOCK = true. No se necesita internet ni credenciales reales.
 *
 * Se activa en EnzonaApiClient cuando USE_MOCK está activo.
 * Rever: poner USE_MOCK = false y borrar este interceptor de la cadena (ver enzona.md).
 */
class EnzonaMockInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method

        val (code, body) = when {
            path.endsWith("/token") && method == "POST" -> 200 to tokenJson()
            path.contains("/payment") && path.endsWith("/complete") && method == "POST" -> 200 to completedJson(extractUuid(path))
            path.contains("/payment") && method == "GET" -> 200 to completedJson(extractUuid(path))
            path.contains("/payment") && method == "POST" -> 200 to createPaymentJson()
            else -> 200 to """{"status":"ok"}"""
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun tokenJson(): String = """
        {
          "access_token": "mock_dev_token_${UUID.randomUUID()}",
          "token_type": "Bearer",
          "expires_in": 3600
        }
    """.trimIndent()

    private fun createPaymentJson(): String {
        val uuid = "mock-tx-${UUID.randomUUID()}"
        return """
        {
          "transaction_uuid": "$uuid",
          "status_code": "1000",
          "invoice_number": "SC-MOCK-${System.currentTimeMillis() % 100000}",
          "links": [
            {"rel": "approval_url", "href": "https://portal.enzona.net/payment/checkout/$uuid"},
            {"rel": "execute", "href": "https://portal.enzona.net/payment/execute/$uuid"}
          ]
        }
        """.trimIndent()
    }

    private fun completedJson(uuid: String?): String {
        val id = uuid ?: "mock-tx-${UUID.randomUUID()}"
        return """
        {
          "transaction_uuid": "$id",
          "status_code": "1001",
          "status_name": "COMPLETED",
          "message": "Mock: pago confirmado (desarrollo)"
        }
        """.trimIndent()
    }

    private fun extractUuid(path: String): String? {
        // path = /payment/v1.0/payments/{uuid} o /payment/v1.0/payments/{uuid}/complete
        val segs = path.split("/")
        val idx = segs.indexOf("payments")
        return if (idx >= 0 && segs.size > idx + 1) {
            segs[idx + 1].takeIf { it.isNotBlank() && it != "complete" }
        } else null
    }
}
