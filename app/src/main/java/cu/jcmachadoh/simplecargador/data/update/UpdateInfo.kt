package cu.jcmachadoh.simplecargador.data.update

import kotlinx.serialization.Serializable

/**
 * Información de una versión publicada para la app, descrita en el repositorio
 * de releases (simplecargador_apk.json). Mismo formato que las demás apps.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val notes: String = ""
)
