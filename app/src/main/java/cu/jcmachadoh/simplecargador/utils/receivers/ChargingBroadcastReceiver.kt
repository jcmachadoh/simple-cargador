package cu.jcmachadoh.simplecargador.utils.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cu.jcmachadoh.simplecargador.SimpleCargadorApp
import cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService

/**
 * RF-01: detecta la conexión/desconexión del cargador.
 * Registrado en el Manifest (proceso muerto) Y dinámicamente en la Application
 * (proceso vivo): los OEM restrictivos saltan receivers de manifest, así que
 * cubrimos ambas vías. El trabajo real lo ejecuta el servicio en primer plano,
 * que impide que el sistema congele el proceso durante la carga.
 */
class ChargingBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SimpleCargadorApp ?: return
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                android.util.Log.d(TAG, "POWER_CONNECTED recibido")
                try {
                    ChargingForegroundService.start(context, ChargingForegroundService.ACTION_START)
                } catch (e: Exception) {
                    // FGS no permitido en este momento: registramos sin servicio.
                    android.util.Log.w(TAG, "FGS bloqueado (${e.message}); registro directo")
                    app.container.chargingSessionManager.onPowerConnected()
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                android.util.Log.d(TAG, "POWER_DISCONNECTED recibido")
                try {
                    ChargingForegroundService.start(context, ChargingForegroundService.ACTION_STOP)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "FGS bloqueado (${e.message}); cierre directo")
                    app.container.chargingSessionManager.onPowerDisconnected()
                }
            }
        }
    }

    private companion object {
        const val TAG = "SimpleCargador"
    }
}
