package cu.jcmachadoh.simplecargador.data.system

import android.content.Context
import android.util.Log
import cu.jcmachadoh.simplecargador.domain.model.BatterySnapshot
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.domain.repository.BatteryInfoRepository
import cu.jcmachadoh.simplecargador.domain.repository.ChargingSessionRepository
import cu.jcmachadoh.simplecargador.utils.extensions.formatShortDuration
import cu.jcmachadoh.simplecargador.utils.notifications.NotificationHelper
import cu.jcmachadoh.simplecargador.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * RF-01 / RF-02 / RNF-08: ciclo de vida de una sesión de carga.
 *
 * - Al conectar: crea la sesión IN_PROGRESS en Room inmediatamente (a prueba de cierres abruptos)
 *   y arranca el muestreo de temperatura/voltaje/corriente (RF-02, RF-08).
 * - Al desconectar: cierra la sesión escribiendo instantáneamente y notifica (RF-05).
 * - Alertas inteligentes mientras dura el proceso (RF-11).
 *
 * Fix: reintento con delay cuando isCharging=false (OEM timing issue).
 * Fix: notifyChargeSummary se llama DESPUÉS de la transición de FGS (race condition fix).
 */
class ChargingSessionManager(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: ChargingSessionRepository,
    private val batteryReader: BatteryInfoRepository,
    private val notifier: NotificationHelper,
    private val alertsEnabled: () -> Boolean,
    private val isPremium: () -> Boolean = { false }
) {

    private val appContext: Context = context.applicationContext

    private var samplingJob: Job? = null

    // Acumuladores del muestreo (se pierden solo si matan el proceso; la sesión sobrevive en Room)
    private var tempSum = 0f
    private var tempCount = 0
    private var voltSum = 0L
    private var voltCount = 0
    private var mahAccumulated = 0f
    private var lastSampleAt = 0L

    private var startLevel = 0
    private var slowChargeNotified = false
    private var eightyNotified = false
    private var lastCriticalNotifyAt = 0L

    private val sessionMutex = Mutex()

    /**
     * Crea la sesión de carga. Reintenta hasta MAX_RETRIES veces con RETRY_DELAY_MS
     * si isCharging==false (algunos OEMs tardan en estabilizar BatteryManager tras el broadcast).
     */
    fun onPowerConnected() {
        Log.d(TAG, "onPowerConnected() invocado")
        scope.launch {
            sessionMutex.withLock {
                val existing = repository.getInProgress()
                if (existing != null) {
                    Log.d(TAG, "Ya existe sesión abierta id=${existing.id}; iniciando sampling si hace falta")
                    if (samplingJob == null) startSampling()
                    return@withLock
                }
                // Reintento: BatteryManager puede tardar en estabilizarse tras el broadcast
                var snap = batteryReader.snapshot()
                var attempts = 0
                while (!snap.isCharging && attempts < MAX_RETRIES) {
                    Log.d(TAG, "isCharging=false, reintento ${attempts + 1}/$MAX_RETRIES en ${RETRY_DELAY_MS}ms")
                    delay(RETRY_DELAY_MS)
                    snap = batteryReader.snapshot()
                    attempts++
                }
                if (!snap.isCharging) {
                    Log.w(TAG, "Sin corriente tras $MAX_RETRIES reintentos; no se crea sesión")
                    return@withLock
                }
                startLevel = snap.levelPercent
                Log.d(TAG, "Creando sesión desde nivel=$startLevel")
                resetAccumulators()
                val session = ChargingSession(
                    startTimeMillis = System.currentTimeMillis(),
                    startLevelPercent = startLevel
                )
                repository.startSession(session) // RNF-08: persistencia inmediata
                startSampling()
                WidgetUpdater.updateAll(appContext, scope, repository, batteryReader, isPremium())
            }
        }
    }

    /**
     * Cierra la sesión activa y publica el resumen.
     * Fix: notifyChargeSummary se ejecuta DESPUÉS de que el FGS haya transitado a MONITOR
     * para evitar cancelar la notificación del servicio en primer plano.
     */
    fun onPowerDisconnected() {
        Log.d(TAG, "onPowerDisconnected() procesando")
        scope.launch {
            sessionMutex.withLock {
                stopSampling()
                val snap = batteryReader.snapshot()
                checkCriticalLevel(snap)
                val open = repository.getInProgress()
                if (open != null) {
                    val now = System.currentTimeMillis()
                    val endLevel = snap.levelPercent
                    val updated = open.copy(
                        endTimeMillis = now,
                        endLevelPercent = endLevel,
                        durationSeconds = (now - open.startTimeMillis).coerceAtLeast(0) / 1000,
                        avgTemperatureCelsius = if (tempCount > 0) tempSum / tempCount else null,
                        avgVoltageMillivolts = if (voltCount > 0) (voltSum.toFloat() / voltCount) else null,
                        accumulatedMah = if (mahAccumulated > 0.5f) mahAccumulated else null
                    )
                    repository.updateSession(updated) // RNF-08: escritura al instante
                    WidgetUpdater.updateAll(appContext, scope, repository, batteryReader, isPremium())
                    // Notificación DESPUÉS de soltar el mutex, para que el FGS ya haya
                    // transitado a MONITOR antes de que se cancele cualquier notificación.
                    delay(NOTIFY_DELAY_MS) // Da tiempo al FGS a promover con ID_MONITOR
                    notifier.notifyChargeSummary(
                        gained = updated.gainedPercent,
                        durationText = updated.durationSeconds.formatShortDuration(),
                        tempText = updated.avgTemperatureCelsius?.let { "${"%.1f".format(it)} °C" }
                    )
                    Log.d(TAG, "Sesión cerrada: +${updated.gainedPercent}% en ${updated.durationSeconds}s")
                } else {
                    Log.d(TAG, "onPowerDisconnected: no había sesión abierta")
                }
            }
        }
    }

    /** T2.3: al abrir la app, reconcilia el estado real del cargador con las sesiones. */
    fun reconcileOnAppOpen() {
        scope.launch {
            val snap = batteryReader.snapshot()
            val open = repository.getInProgress()
            when {
                snap.isCharging && open == null -> onPowerConnected()
                !snap.isCharging && open != null -> onPowerDisconnected()
                snap.isCharging && open != null -> {
                    // Sesión ya abierta y sigue cargando: asegurar sampling
                    if (samplingJob == null) {
                        startSampling()
                        WidgetUpdater.updateAll(appContext, scope, repository, batteryReader, isPremium())
                    }
                }
                else -> Unit
            }
        }
    }

    /** T2.3: tras reinicio del teléfono con cable puesto y sin sesión abierta. */
    fun recoverIfPlugged() {
        Log.d(TAG, "recoverIfPlugged()")
        scope.launch {
            val snap = batteryReader.snapshot()
            val open = repository.getInProgress()
            when {
                snap.isCharging && open == null -> onPowerConnected()
                !snap.isCharging && open != null -> onPowerDisconnected()
                snap.isCharging && open != null -> {
                    if (samplingJob == null) startSampling()
                }
                else -> Unit
            }
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = scope.launch {
            lastSampleAt = System.currentTimeMillis()
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                sampleOnce()
            }
        }
    }

    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    private suspend fun sampleOnce() {
        val now = System.currentTimeMillis()
        val dtHours = (now - lastSampleAt) / 3_600_000f
        lastSampleAt = now

        val snap = batteryReader.snapshot()
        snap.temperatureCelsius?.let {
            if (it in -10f..80f) { tempSum += it; tempCount++ }
        }
        snap.voltageMillivolts?.let { if (it > 0) { voltSum += it; voltCount++ } }

        // Integración de corriente → mAh acumulados (RF-08); unidades varían por fabricante.
        snap.currentMilliamps?.let { rawMa ->
            val ma = abs(rawMa).takeIf { it in 1..100_000 } ?: return@let
            mahAccumulated += ma * dtHours
        }

        if (!isPremium() || !alertsEnabled()) return
        val elapsedMinutes = (now - sessionStartOrDefault()) / 60_000
        val gained = snap.levelPercent - startLevel

        if (!slowChargeNotified && elapsedMinutes >= SLOW_CHARGE_MINUTES && gained <= SLOW_CHARGE_GAIN) {
            slowChargeNotified = true
            notifier.notifySlowCharge(elapsedMinutes, gained.coerceAtLeast(0))
        }
        if (!eightyNotified && snap.isCharging && snap.levelPercent >= EIGHTY_PERCENT) {
            eightyNotified = true
            notifier.notifyEightyPercent()
        }
    }

    private suspend fun checkCriticalLevel(snap: BatterySnapshot) {
        if (!isPremium() || !alertsEnabled()) return
        val now = System.currentTimeMillis()
        if (!snap.isCharging && snap.levelPercent <= CRITICAL_LEVEL &&
            now - lastCriticalNotifyAt > CRITICAL_COOLDOWN_MS
        ) {
            lastCriticalNotifyAt = now
            notifier.notifyCriticalLevel(snap.levelPercent)
        }
    }

    private var sessionStartMillis = 0L

    private fun sessionStartOrDefault(): Long =
        if (sessionStartMillis == 0L) lastSampleAt else sessionStartMillis

    private fun resetAccumulators() {
        tempSum = 0f; tempCount = 0
        voltSum = 0L; voltCount = 0
        mahAccumulated = 0f
        slowChargeNotified = false
        eightyNotified = false
        sessionStartMillis = System.currentTimeMillis()
        lastSampleAt = sessionStartMillis
    }

    companion object {
        private const val TAG = "ChargingSession"
        private const val ACTION_START_SVC =
            cu.jcmachadoh.simplecargador.utils.services.ChargingForegroundService.ACTION_START
        private const val SAMPLE_INTERVAL_MS = 60_000L          // RNF-04: muestreo ligero
        private const val SLOW_CHARGE_MINUTES = 20L             // RF-11
        private const val SLOW_CHARGE_GAIN = 2                  // RF-11
        private const val EIGHTY_PERCENT = 80                   // RF-11
        private const val CRITICAL_LEVEL = 15                   // RF-11
        private const val CRITICAL_COOLDOWN_MS = 60L * 60 * 1000
        private const val MAX_RETRIES = 5                        // Reintentos para isCharging
        private const val RETRY_DELAY_MS = 500L                 // 500ms entre reintentos
        private const val NOTIFY_DELAY_MS = 300L                // Espera antes de notificar resumen
    }
}
