package cu.jcmachadoh.simplecargador.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Aplica el idioma seleccionado por el usuario a los recursos de la app
 * mediante un ContextWrapper que sobreescribe la configuración de la locale.
 * La locale persistida se aplica en attachBaseContext de las actividades.
 */
object LocaleManager {

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        System.setProperty("user.language", languageCode)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }

    fun isSupported(code: String): Boolean =
        code == "es" || code == "en"
}
