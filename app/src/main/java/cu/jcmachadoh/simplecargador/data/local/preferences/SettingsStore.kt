package cu.jcmachadoh.simplecargador.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import cu.jcmachadoh.simplecargador.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes no sensibles: tema, alertas, precio premium configurable. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadTheme())

    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALERTS, value).apply()

    /** Monitor permanente: mantiene el proceso vivo para registrar TODAS las cargas. */
    var monitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR, value).apply()

    /** Idioma seleccionado por el usuario (código ISO 639-1, ej. "es", "en"). */
    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    // ── Widget: intervalo de refresco periódico (minutos) ──

    private val _widgetUpdateIntervalMinutes = MutableStateFlow(loadWidgetInterval())

    /** Flujo observable del intervalo del widget. */
    val widgetUpdateIntervalMinutesFlow: StateFlow<Int> = _widgetUpdateIntervalMinutes.asStateFlow()

    /** Intervalo actual en minutos. Rango válido: 5..60 (slider). */
    var widgetUpdateIntervalMinutes: Int
        get() = _widgetUpdateIntervalMinutes.value
        set(value) {
            val sanitized = value.coerceIn(5, 60)
            prefs.edit().putInt(KEY_WIDGET_INTERVAL, sanitized).apply()
            _widgetUpdateIntervalMinutes.value = sanitized
        }

    private fun loadWidgetInterval(): Int {
        val stored = prefs.getInt(KEY_WIDGET_INTERVAL, DEFAULT_WIDGET_INTERVAL_MINUTES)
        return stored.coerceIn(5, 60)
    }

    private fun loadTheme(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_MONITOR = "monitor_enabled"
        const val KEY_LANGUAGE = "language_code"
        const val DEFAULT_LANGUAGE = "es"
        const val KEY_WIDGET_INTERVAL = "widget_update_interval_minutes"
        const val DEFAULT_WIDGET_INTERVAL_MINUTES = 15
        val WIDGET_INTERVALS = listOf(5, 10, 15, 30, 60)
    }
}
