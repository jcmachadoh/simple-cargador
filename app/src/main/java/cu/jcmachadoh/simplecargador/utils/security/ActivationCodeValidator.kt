package cu.jcmachadoh.simplecargador.utils.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RF-12 (flujo offline): el usuario paga por fuera y recibe del desarrollador un código
 * derivado de su deviceId con HMAC-SHA256. La validación es 100% local, sin backend.
 * Formato: XXXX-XXXX-XXXX-XXXX (20 caracteres hex agrupados).
 */
object ActivationCodeValidator {

    fun expectedCode(deviceId: String): String =
        rawHmacHex(deviceId).chunked(4).joinToString("-")

    /** Acepta mayúsculas/minúsculas y separadores opcionales. */
    fun isValid(code: String, deviceId: String): Boolean {
        val normalized = normalize(code)
        if (normalized.isEmpty()) return false
        return constantTimeEquals(normalized, rawHmacHex(deviceId))
    }

    private fun rawHmacHex(deviceId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ObfuscatedKeys.offlineSecret().toByteArray(), "HmacSHA256"))
        val digest = mac.doFinal(deviceId.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.take(16).uppercase()
    }

    private fun normalize(code: String): String =
        code.trim().replace("-", "").replace(" ", "").uppercase()

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
