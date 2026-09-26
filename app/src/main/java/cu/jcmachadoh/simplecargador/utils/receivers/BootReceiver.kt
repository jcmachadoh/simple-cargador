package cu.jcmachadoh.simplecargador.utils.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cu.jcmachadoh.simplecargador.SimpleCargadorApp

/**
 * T2.3: al arrancar el teléfono, el monitor queda listo para registrar cargas (RNF-08).
 * Fix: recoverIfPlugged() se llama SIEMPRE, no solo cuando falla el FGS.
 * Así se detecta si el cargador ya estaba conectado al arrancar.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? SimpleCargadorApp ?: return
        Log.d(TAG, "BOOT_COMPLETED recibido")
        // BOOT_COMPLETED está exento de las restricciones de arranque de FGS.
        try {
            cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService.start(
                context,
                cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService.ACTION_MONITOR
            )
        } catch (e: Throwable) {
            Log.w(TAG, "FGS start failed: ${e.message}")
        }
        // Fix: SIEMPRE reconciliar, no solo en caso de error del FGS.
        // Si el cargador ya estaba conectado al arrancar, crea la sesión.
        app.container.chargingSessionManager.recoverIfPlugged()
        // Reprogramar widget periódico tras reinicio
        try {
            cu.jcmachadoh.simplecargador.widget.WidgetUpdateScheduler.rescheduleIfWidgets(context)
        } catch (e: Throwable) {
            Log.w(TAG, "Widget reschedule failed: ${e.message}")
        }
        // Forzar un refresco inmediato si hay widgets
        try {
            app.applicationScope.let { scope ->
                cu.jcmachadoh.simplecargador.widget.WidgetUpdater.updateAll(
                    context,
                    scope,
                    app.container.sessionRepository,
                    app.container.batteryReader,
                    isPremium = app.container.licenseRepository.isPremium.value
                )
            }
        } catch (_: Throwable) {}
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
