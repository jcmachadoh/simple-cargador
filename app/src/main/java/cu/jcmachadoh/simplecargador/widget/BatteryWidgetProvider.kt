package cu.jcmachadoh.simplecargador.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import cu.jcmachadoh.simplecargador.SimpleCargadorApp

/**
 * RF-09: widget redimensionable con nivel actual y datos de la última carga.
 * Se actualiza:
 *  - en eventos de energía (POWER_CONNECTED/DISCONNECTED)
 *  - al pedir refresco manual (ACTION_REFRESH)
 *  - periódicamente según el intervalo elegido en Ajustes (5–60 min) vía WorkManager
 *  - en vivo por nivel de batería vía SimpleCargadorApp (ACTION_BATTERY_CHANGED)
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        pushUpdate(context)
        // Garantiza que el trabajo periódico siga programado tras cada update del launcher.
        try { WidgetUpdateScheduler.schedule(context) } catch (_: Throwable) {}
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try { WidgetUpdateScheduler.schedule(context) } catch (_: Throwable) {}
        pushUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try { WidgetUpdateScheduler.cancel(context) } catch (_: Throwable) {}
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            ACTION_REFRESH -> pushUpdate(context)
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                pushUpdate(context)
                try { WidgetUpdateScheduler.schedule(context) } catch (_: Throwable) {}
            }
        }
    }

    private fun pushUpdate(context: Context) {
        val app = context.applicationContext as? SimpleCargadorApp ?: return
        WidgetUpdater.updateAll(
            app,
            app.applicationScope,
            app.container.sessionRepository,
            app.container.batteryReader,
            isPremium = app.container.licenseRepository.isPremium.value
        )
    }

    companion object {
        const val ACTION_REFRESH = "cu.jcmachadoh.simplecargador.WIDGET_REFRESH"
    }
}
