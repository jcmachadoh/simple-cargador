package cu.jcmachadoh.simplecargador.utils.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cu.jcmachadoh.simplecargador.MainActivity
import cu.jcmachadoh.simplecargador.R

/**
 * RF-05 / RF-11: canales y plantillas de notificación local.
 * Sin permisos adicionales; respeta POST_NOTIFICATIONS en API 33+.
 */
class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATE,
                "Estado de carga",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos al conectar o desconectar el cargador" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Alertas inteligentes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Carga lenta, batería al 80% y nivel crítico" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                "Monitor de fondo",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Servicio silencioso que registra las cargas"
                setShowBadge(false)
            }
        )
    }

    fun notifyConnected(levelPercent: Int) = post(
        channel = CHANNEL_STATE,
        id = ID_CONNECTED,
        title = "Cargador conectado ⚡",
        text = "Registrando la carga desde $levelPercent%. Puedes cerrar la app."
    )

    /**
     * Notificación persistente del servicio en primer plano (RF-01):
     * indica que el proceso de recogida de datos comenzó.
     */
    fun buildCollectingNotification(): android.app.Notification {
        val pending = mainActivityPendingIntent()
        // Full-screen intent: mecanismo sancionado por Android para abrir la app
        // automáticamente desde un servicio en primer plano al conectar el cargador
        // (el arranque directo de actividades desde segundo plano está bloqueado en
        // Android 10+). La notificación queda en segundo plano y la app se abre.
        val fullScreen = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_STATE)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Simple Cargador")
            .setContentText("Cargando y registrando tu batería.")
            .setContentIntent(pending)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    /**
     * Monitor permanente y silencioso: mantiene el proceso vivo para que TODAS las
     * cargas queden registradas aunque el sistema salte los receivers de manifest.
     * Coste energético ~0 (RNF-04): no hay polling, solo receivers dormidos.
     */
    fun buildMonitorNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Simple Cargador vigila tu batería")
            .setContentText("Listo para registrar cada carga automáticamente.")
            .setContentIntent(mainActivityPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyDisconnected(gained: Int?) {
        val detail = if (gained != null && gained >= 0) " Ganaste +$gained%." else ""
        post(CHANNEL_STATE, ID_DISCONNECTED, "Cargador desconectado", "Carga guardada.$detail")
    }

    /** Resumen al terminar la carga: % ganado, duración y temperatura media. */
    fun notifyChargeSummary(gained: Int?, durationText: String, tempText: String?) {
        // Fix: NO cancelamos ID_COLLECTING aquí. El FGS ya transitó a MONITOR
        // con su propia notificación. Solo publicamos el resumen con ID_SUMMARY.
        val gainedText = when {
            gained == null -> "nivel sin cambio"
            gained >= 0 -> "+$gained% de batería"
            else -> "$gained% de batería"
        }
        val body = buildString {
            append("Ganaste $gainedText en $durationText.")
            tempText?.let { append(" Temperatura media: $it.") }
            append(" Sesión guardada en tu historial.")
        }
        post(CHANNEL_STATE, ID_SUMMARY, "Resumen de la carga ✔", body)
    }

    fun notifySlowCharge(elapsedMinutes: Long, gained: Int) = post(
        channel = CHANNEL_ALERTS,
        id = ID_SLOW,
        title = "¡Carga lenta detectada!",
        text = "$elapsedMinutes min enchufado y solo +$gained%. Revisa el cable o el cargador."
    )

    fun notifyEightyPercent() = post(
        channel = CHANNEL_ALERTS,
        id = ID_EIGHTY,
        title = "Batería al 80%",
        text = "Para cuidar la salud de tu batería puedes desconectarla ya."
    )

    fun notifyCriticalLevel(level: Int) = post(
        channel = CHANNEL_ALERTS,
        id = ID_CRITICAL,
        title = "Nivel crítico ($level%)",
        text = "Queda poca batería y no hay corriente disponible. Busca una ventana de carga."
    )

    private fun post(channel: String, id: Int, title: String, text: String) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    companion object {
        const val CHANNEL_STATE = "charging_state"
        const val CHANNEL_ALERTS = "smart_alerts"
        const val CHANNEL_MONITOR = "background_monitor"

        const val ID_CONNECTED = 1001
        const val ID_DISCONNECTED = 1002
        const val ID_SUMMARY = 1003       // Fix: ID separado para resumen (no cancela FGS)
        const val ID_SLOW = 2001
        const val ID_EIGHTY = 2002
        const val ID_CRITICAL = 2003
        const val ID_COLLECTING = 3001
        const val ID_MONITOR = 3002       // Fix: ID separado para monitor (no comparte con collecting)
    }
}
