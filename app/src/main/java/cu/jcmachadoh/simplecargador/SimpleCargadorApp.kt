package cu.jcmachadoh.simplecargador

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.content.ContextCompat
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.utils.receivers.ChargingBroadcastReceiver
import cu.jcmachadoh.simplecargador.utils.workers.CleanOldSessionsWorker
import cu.jcmachadoh.simplecargador.widget.BatteryWidgetProvider
import cu.jcmachadoh.simplecargador.widget.WidgetUpdateScheduler
import cu.jcmachadoh.simplecargador.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

class SimpleCargadorApp : Application() {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    private var dynamicChargingReceiver: BroadcastReceiver? = null
    private var batteryWidgetReceiver: BroadcastReceiver? = null
    private var lastWidgetLevel = -1
    private var lastWidgetCharging: Boolean? = null
    private var lastWidgetUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
        container.notificationHelper.ensureChannels()
        container.checkLicenseUseCase.isPremiumNow() // RF-13: verificación local instantánea
        registerDynamicChargingReceiver()
        registerBatteryWidgetReceiver()
        observeWidgetIntervalChanges()
        // Garantiza que el widget quede programado aunque ya existiera antes de instalar la actualización
        try { WidgetUpdateScheduler.rescheduleIfWidgets(this) } catch (_: Throwable) {}
        startMonitor()
        scheduleWeeklyCleanup()
    }

    /**
     * Monitor permanente: proceso siempre listo para recibir los eventos de
     * energía. Desde una Activity abierta, iniciar FGS siempre está permitido.
     * Fix: log de errores para diagnosticar fallos de FGS en OEM restrictivos.
     */
    fun startMonitor() {
        try {
            cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService.start(
                this,
                cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService.ACTION_MONITOR
            )
        } catch (e: Throwable) {
            android.util.Log.e("SimpleCargador", "FGS monitor start failed: ${e.message}")
        }
    }

    /**
     * RF-01: registro dinámico mientras el proceso vive; garantiza la recepción
     * incluso en imágenes/ROM que restringen los receivers de manifest.
     * El receiver de manifest se mantiene para cubrir el proceso muerto.
     */
    private fun registerDynamicChargingReceiver() {
        dynamicChargingReceiver = ChargingBroadcastReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // RECEIVER_EXPORTED: OEM restrictivos (Xiaomi/Realme/Huawei) silencian
        // RECEIVER_NOT_EXPORTED para broadcasts del sistema en receivers dinámicos.
        ContextCompat.registerReceiver(
            this,
            dynamicChargingReceiver!!,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun scheduleWeeklyCleanup() {
        val request = PeriodicWorkRequestBuilder<CleanOldSessionsWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CleanOldSessionsWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Reacción en vivo al nivel de batería: cada vez que el sistema reporta
     * ACTION_BATTERY_CHANGED (cada 1% aprox.) refrescamos el widget si existe.
     * Throttle de 30 s evita spam cuando el OEM envía múltiples broadcasts por segundo.
     * Esto garantiza que el valor de carga se vea "constante" sin esperar al intervalo periódico.
     */
    private fun registerBatteryWidgetReceiver() {
        batteryWidgetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                // Solo si hay widgets activos
                val mgr = AppWidgetManager.getInstance(this@SimpleCargadorApp)
                val ids = try {
                    mgr.getAppWidgetIds(ComponentName(this@SimpleCargadorApp, BatteryWidgetProvider::class.java))
                } catch (_: Throwable) { intArrayOf() }
                if (ids.isEmpty()) return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
                if (percent < 0) return

                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = plugged ||
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                val now = System.currentTimeMillis()
                val levelChanged = percent != lastWidgetLevel
                val chargingChanged = isCharging != lastWidgetCharging
                val timeSince = now - lastWidgetUpdateMs

                // Actualiza si cambió el nivel o el estado, respetando throttle de 25s.
                // También fuerza un refresh al menos cada 60s mientras carga (para tiempoHoy vivo).
                val shouldRefresh = when {
                    levelChanged || chargingChanged -> timeSince >= 25_000 || lastWidgetUpdateMs == 0L
                    isCharging && timeSince >= 60_000 -> true
                    else -> false
                }
                if (!shouldRefresh) return

                lastWidgetLevel = percent
                lastWidgetCharging = isCharging
                lastWidgetUpdateMs = now

                WidgetUpdater.updateAll(
                    this@SimpleCargadorApp,
                    applicationScope,
                    container.sessionRepository,
                    container.batteryReader,
                    isPremium = container.licenseRepository.isPremium.value
                )
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            ContextCompat.registerReceiver(
                this, batteryWidgetReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Throwable) {
            android.util.Log.w("SimpleCargador", "battery widget receiver failed: ${e.message}")
        }
    }

    /** Observa cambios del intervalo configurado y reprograma el WorkManager. */
    private fun observeWidgetIntervalChanges() {
        container.settingsStore.widgetUpdateIntervalMinutesFlow
            .onEach { interval ->
                try { WidgetUpdateScheduler.schedule(this@SimpleCargadorApp, interval) } catch (_: Throwable) {}
            }
            .launchIn(applicationScope)
    }
}
