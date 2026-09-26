package cu.jcmachadoh.simplecargador.data.remote

/**
 * Configuración central de Enzona.
 *
 * MODO DESARROLLO (placeholder): USE_MOCK = true simula TODA la API sin
 * credenciales reales. Al tocar "Hacerse Premium" abre la apk Enzona o el
 * navegador con datos falsos (EnzonaMockInterceptor).
 *
 * Para producción: poner USE_MOCK = false y reemplazar las claves en
 * ObfuscatedKeys (ver enzona.md).
 */
object EnzonaConfig {

    /** Activa el interceptor mock. Debe ser false en producción. */
    const val USE_MOCK = true

    const val BASE_URL = "https://api.enzona.net/"

    /** Deep links hacia nuestra app: la pasarela redirige aquí tras pagar/cancelar. */
    const val RETURN_URL = "simplecargador://enzona/success"
    const val CANCEL_URL = "simplecargador://enzona/cancel"

    const val CURRENCY = "CUP"

    /** Nombre del paquete Enzona nativo. */
    const val ENZONA_PACKAGE = "cu.xetid.apk.enzona"
}
