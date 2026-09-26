package cu.jcmachadoh.simplecargador.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import cu.jcmachadoh.simplecargador.domain.model.BatterySnapshot
import cu.jcmachadoh.simplecargador.domain.repository.BatteryInfoRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Lee el estado de la batería con múltiples estrategias para sobrevivir a OEM
 * restrictivos (Xiaomi/Realme/Huawei) que pueden:
 *  - Devolver datos obsoletos en el sticky ACTION_BATTERY_CHANGED
 *  - Bloquear broadcasts a receivers dinámicos RECEIVER_NOT_EXPORTED
 *
 * Estrategia:
 *   1. BatteryManager direct API (siempre fiable, sin broadcasts)
 *   2. Sticky intent como respaldo
 *   3. Polling cada 10 s como última línea de defensa
 *
 * Además guarda la capacidad de carga completa real ([fullChargeCapacityMah]), que se
 * mide con el contador de carga cada vez que la batería llega al 100%.
 */
class BatteryReader(private val context: Context) : BatteryInfoRepository {

    private val bm: BatteryManager?
        get() = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    override fun snapshot(): BatterySnapshot {
        // ── 1) BatteryManager direct API: la fuente más fiable ──
        val bmLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 1..100 }

        // batteryManager.isCharging() es API 23+ (nuestro minSdk).
        val bmCharging = bm?.isCharging == true

        // Corriente: negativa = descargando, positiva = cargando (varía por OEM).
        val bmCurrentMa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }

        val bmChargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 }

        // ── 2) Sticky intent como respaldo ──
        val intent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) { null }

        val intentLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
        val intentScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)?.takeIf { it > 0 }
        val intentPercent = if (intentLevel != null && intentScale != null) {
            (intentLevel * 100f / intentScale).toInt().coerceIn(0, 100)
        } else null

        val intentPlugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val intentStatus = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val intentCharging = intentPlugged != 0 ||
            intentStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            intentStatus == BatteryManager.BATTERY_STATUS_FULL

        // ── 3) Fusión: BM como fuente primaria, sticky como fallback ──
        val percent = (bmLevel ?: intentPercent ?: 0).coerceIn(0, 100)
        val charging = bmCharging || intentCharging

        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val tempC = if (tempRaw in -200..1000) tempRaw / 10f else null

        val voltRawUv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val voltageMv = if (voltRawUv > 0) {
            (if (voltRawUv > 100_000) voltRawUv / 1000 else voltRawUv).takeIf { it in 2_000..6_000 }
        } else null

        val currentMa = bmCurrentMa?.takeIf { kotlin.math.abs(it) in 1..100_000 }

        rememberFullChargeCapacity(percent, bmChargeCounter)

        return BatterySnapshot(
            levelPercent = percent,
            isCharging = charging,
            voltageMillivolts = voltageMv,
            temperatureCelsius = tempC,
            currentMilliamps = currentMa,
            chargeCounterMicroAh = bmChargeCounter
        )
    }

    /**
     * Emite actualizaciones cuando la batería cambia.
     * Doble protección: receiver dinámico + polling cada POLL_MS.
     * RECEIVER_EXPORTED: en OEM restrictivos, RECEIVER_NOT_EXPORTED silencia
     * broadcasts del sistema para receivers dinámicos.
     */
    override fun observeChanges(): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val s = snapshot()
                Log.d(TAG, "Broadcast battery: level=${s.levelPercent} charging=${s.isCharging}")
                trySend(s)
            }
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Throwable) {
            Log.w(TAG, "registerReceiver failed: ${e.message}")
        }

        // Emisión inmediata del estado actual
        trySend(snapshot())

        // Polling de respaldo: garantiza actualización aunque el OEM suprima broadcasts
        try {
            while (true) {
                delay(POLL_MS)
                val s = snapshot()
                trySend(s)
            }
        } finally {
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
        }
    }

    /**
     * Capacidad de diseño de fábrica (el "total" de la fórmula), en mAh. 0 = el kernel y
     * el framework no la exponen, y es preferible a inventar un valor: sin denominador
     * real la salud no se puede calcular y la UI lo muestra como pendiente.
     *
     * Orden: PowerProfile (API oculta) → power supply class del kernel.
     */
    override fun nominalCapacityMah(): Int {
        cachedNominalMah?.let { return it }
        val (value, source) = designCapacityFromPowerProfile()
            ?: (PowerSupplyDesignCapacity().readMah()?.let { it to "sysfs" } ?: (0 to "desconocida"))
        cachedNominalMah = value
        Log.d(TAG, "Capacidad de fábrica: $value mAh (fuente: $source)")
        return value
    }

    @SuppressLint("PrivateApi")
    private fun designCapacityFromPowerProfile(): Pair<Int, String>? = try {
        val clazz = Class.forName("com.android.internal.os.PowerProfile")
        val profile = clazz.getConstructor(Context::class.java).newInstance(context)
        val mah = (clazz.getMethod("getBatteryCapacity").invoke(profile) as Double).toInt()
        mah.takeIf { it in MIN_PLAUSIBLE_MAH..MAX_PLAUSIBLE_MAH }?.let { it to "PowerProfile" }
    } catch (_: Throwable) {
        null
    }

    /**
     * Capacidad de carga completa medida (mAh). La guarda el propio reader, que es el
     * único punto por el que pasa el contador de carga.
     */
    override fun fullChargeCapacityMah(): Int? =
        prefs.getInt(KEY_FULL_CHARGE_MAH, 0).takeIf { it > 0 }

    /**
     * Con la batería al 100% el contador de carga (µAh) es la capacidad de carga completa
     * real. Ese es el "total" que la fórmula necesita: salud% = capacidadMedida * 100 /
     * capacidadDeFábrica. Sin esto habría que deducirla integrando corriente, que depende
     * de lo que cada OEM reporte y por eso se desborda a más de 100%.
     *
     * Filtros:
     *  - Solo al tope del ciclo (evita medir a media carga, donde el voltage aún miente).
     *  - Se descartan lecturas fuera de 20%..150% de la capacidad de fábrica (ruido OEM,
     *    o contadores que devuelven directamente el diseño).
     *  - Caída suave: una muestra solo puede bajar un 3% el valor guardado, para que una
     *    lectura puntual mala no destruya el dato, pero la degradación real sí se note.
     */
    @Synchronized
    private fun rememberFullChargeCapacity(percent: Int, counterMicroAh: Int?) {
        if (counterMicroAh == null || percent < FULL_CHARGE_LEVEL) return
        val measuredMah = counterMicroAh / MICRO_AH_PER_MAH
        if (measuredMah < MIN_PLAUSIBLE_MAH) return
        if (measuredMah > MAX_PLAUSIBLE_MAH) return

        // Con la capacidad de fábrica conocida, la ventana es relativa a ella; si el kernel
        // no la expone, la validación se queda con la ventana absoluta de arriba.
        val nominal = nominalCapacityMah()
        if (nominal > 0) {
            if (measuredMah < nominal * MIN_COUNTER_RATIO) return
            if (measuredMah > nominal * MAX_COUNTER_RATIO) return
        }

        val storedMah = prefs.getInt(KEY_FULL_CHARGE_MAH, 0)
        val nextMah = maxOf(measuredMah, (storedMah * SMOOTHING_FACTOR).toInt())
        if (nextMah > storedMah) {
            prefs.edit().putInt(KEY_FULL_CHARGE_MAH, nextMah).apply()
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var cachedNominalMah: Int? = null

    private companion object {
        const val TAG = "BatteryReader"
        const val POLL_MS = 10_000L
        const val PREFS_NAME = "battery_info"
        const val KEY_FULL_CHARGE_MAH = "full_charge_capacity_mah"
        const val MICRO_AH_PER_MAH = 1000
        /** 99 y no 100: hay OEM que nunca reportan exactamente 100. */
        const val FULL_CHARGE_LEVEL = 99
        const val MIN_COUNTER_RATIO = 0.2f
        const val MAX_COUNTER_RATIO = 1.5f
        const val SMOOTHING_FACTOR = 0.97f
        val MIN_PLAUSIBLE_MAH = PowerSupplyDesignCapacity.MIN_PLAUSIBLE_MAH
        val MAX_PLAUSIBLE_MAH = PowerSupplyDesignCapacity.MAX_PLAUSIBLE_MAH
    }
}
