package cu.jcmachadoh.simplecargador.utils.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cu.jcmachadoh.simplecargador.MainActivity
import cu.jcmachadoh.simplecargador.SimpleCargadorApp
import cu.jcmachadoh.simplecargador.utils.notifications.NotificationHelper

/**
 * Servicio en primer plano con tres modos (RF-01/02, RNF-08):
 *
 *  MONITOR    — silencioso y permanente: mantiene el proceso vivo para que los
 *               eventos de batería lleguen SIEMPRE, aunque el fabricante salte
 *               los receivers de manifest. Iniciado en BOOT y al abrir la app.
 *  COLLECTING — durante una carga: notificación "Registrando tu carga" + muestreo.
 *  STOP       — cierra la sesión, vuelve a MONITOR y publica el resumen.
 *
 * Fix: IDs de notificación separados para MONITOR (3002) y COLLECTING (3001).
 * Fix: ACTION_STOP primero promueve a MONITOR ANTES de llamar onPowerDisconnected,
 *      para que la notificación del FGS nunca sea cancelada mientras está en primer plano.
 */
class ChargingForegroundService : Service() {

    private var batteryReceiver: BroadcastReceiver? = null
    private var currentNotificationId = NotificationHelper.ID_MONITOR

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerBatteryReceiver()
    }

    override fun onDestroy() {
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Throwable) {} }
        batteryReceiver = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as SimpleCargadorApp
        val container = app.container
        val action = intent?.action ?: ACTION_MONITOR

        when (action) {
            ACTION_MONITOR -> {
                Log.d(TAG, "Modo MONITOR")
                promoteToForeground(
                    NotificationHelper.ID_MONITOR,
                    app.container.notificationHelper.buildMonitorNotification()
                )
            }
            ACTION_START -> {
                Log.d(TAG, "Modo COLLECTING")
                // Pasamos a primer plano ANTES de lanzar la actividad para cumplir
                // la restricción de arranque en segundo plano (el FGS puede abrirla).
                promoteToForeground(
                    NotificationHelper.ID_COLLECTING,
                    container.notificationHelper.buildCollectingNotification()
                )
                // Abrimos (o traemos al frente) la app al conectar el cargador.
                launchMainActivity()
                container.chargingSessionManager.onPowerConnected()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Modo STOP → transición a MONITOR")
                // Fix: PRIMERO promover a MONITOR (nuevo ID) ANTES de llamar onPowerDisconnected.
                // Así el FGS nunca se queda sin notificación.
                if (container.settingsStore.monitorEnabled) {
                    promoteToForeground(
                        NotificationHelper.ID_MONITOR,
                        app.container.notificationHelper.buildMonitorNotification()
                    )
                } else {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                // Ahora sí: cerrar sesión y notificar resumen (el FGS ya tiene notificación MONITOR).
                container.chargingSessionManager.onPowerDisconnected()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(notificationId: Int, notification: android.app.Notification) {
        currentNotificationId = notificationId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                0
            )
        }
    }

    /**
     * Recepción GARANTIZADA mientras el proceso vive: el sistema siempre entrega
     * broadcasts a receptores dinámicos de un proceso activo, sin importar las
     * políticas de receivers de manifest del fabricante.
     */
    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        // Abrimos la app al conectar el cargador (el FGS puede
                        // lanzar actividades desde primer plano).
                        launchMainActivity()
                        ContextCompat.startForegroundService(
                            this@ChargingForegroundService,
                            serviceIntent(ACTION_START)
                        )
                    }
                    Intent.ACTION_POWER_DISCONNECTED ->
                        ContextCompat.startForegroundService(
                            this@ChargingForegroundService,
                            serviceIntent(ACTION_STOP)
                        )
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // RECEIVER_EXPORTED: OEM restrictivos (Xiaomi/Realme/Huawei) silencian
        // RECEIVER_NOT_EXPORTED para broadcasts del sistema en receivers dinámicos.
        ContextCompat.registerReceiver(
            this,
            batteryReceiver!!,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun serviceIntent(action: String): Intent =
        Intent(this, ChargingForegroundService::class.java).apply { this.action = action }

    /** Lanza (o trae al frente) la actividad principal al conectar el cargador.
     *  En Android 10+ el arranque de actividades desde segundo plano está bloqueado
     *  (incluso para FGS). La vía permitida es un PendingIntent que opta por el
     *  arranque en segundo plano: FLAG_ALLOW_BACKGROUND_ACTIVITY_STARTS (API 30-33)
     *  y ActivityOptions.setPendingIntentBackgroundActivityStartMode (API 34+). */
    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        // 0x01000000 == PendingIntent.FLAG_ALLOW_BACKGROUND_ACTIVITY_STARTS (API 30+).
        // En API 34+ la constante se ignora; el arranque lo habilita ActivityOptions.
        val bgFlag = 0x01000000
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or bgFlag
        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
                pi.send(null, 0, null, null, null, null, options)
            } else {
                pi.send()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo abrir la actividad: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ChargingFgService"
        const val ACTION_MONITOR = "cu.jcmachadoh.simplecargador.action.MONITOR"
        const val ACTION_START = "cu.jcmachadoh.simplecargador.action.START_CHARGING"
        const val ACTION_STOP = "cu.jcmachadoh.simplecargador.action.STOP_CHARGING"

        fun start(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChargingForegroundService::class.java).apply {
                    this.action = action
                }
            )
        }
    }
}
