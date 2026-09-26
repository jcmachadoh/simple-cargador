package cu.jcmachadoh.simplecargador.domain.usecase

import cu.jcmachadoh.simplecargador.domain.model.BatteryHealth
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession

/**
 * RF-08: estima la salud (vida) de la batería comparando la capacidad que admite hoy
 * contra la capacidad nominal de fábrica.
 *
 * Fórmula (la misma en ambas fuentes):
 *
 *     salud% = capacidadActual * 100 / capacidadDeFabrica
 *
 * El resultado siempre queda acotado a 0..100. Una batería nunca puede "vivir más" que
 * la de fábrica, así que un porcentaje por encima de 100 es un dato malo, no una mejora.
 *
 * Fuentes de [capacityMah], en orden de preferencia:
 *
 *  1. **Medida** — la capacidad de carga completa que da el contador de carga del sistema
 *     con la batería al 100% ([fullChargeCapacityMah]). Es un valor directo, sin estimar.
 *  2. **Estimada** — integración de la corriente de las sesiones que terminaron en carga
 *     completa: capacidadActual = (mAhAcumulados / %ganado) * 100. Solo se usan sesiones
 *     completas porque las parciales fallan por la no linealidad del voltaje.
 *
 * Las lecturas fuera de la ventana de plausibilidad (20%..150% de la capacidad de fábrica)
 * se descartan: preferimos no mostrar vida antes que mostrar un número inventado.
 */
object CalculateHealthUseCase {

    private const val FULL_CHARGE_THRESHOLD = 95

    private const val MIN_PLAUSIBLE_RATIO = 0.2f
    private const val MAX_PLAUSIBLE_RATIO = 1.5f

    fun fromSessions(
        sessions: List<ChargingSession>,
        nominalCapacityMah: Int,
        nowMillis: Long,
        weekMillis: Long = 7L * 24 * 60 * 60 * 1000,
        fullChargeCapacityMah: Int? = null
    ): BatteryHealth? {
        if (nominalCapacityMah <= 0) return null

        val perSession = sessions.mapNotNull { s ->
            if (s.endLevelPercent == null || s.endLevelPercent < FULL_CHARGE_THRESHOLD) return@mapNotNull null
            sessionCapacityMah(s)
        }

        val capacityMah = fullChargeCapacityMah?.toFloat()?.takeIf { isPlausible(it, nominalCapacityMah) }
            ?: weighted(perSession)?.takeIf { isPlausible(it, nominalCapacityMah) }
            ?: return null

        val recentCutoff = nowMillis - weekMillis
        val previousCutoff = nowMillis - 2 * weekMillis
        val recent = weighted(perSession.filter { it.timeMillis >= recentCutoff })
        val previous = weighted(perSession.filter { it.timeMillis in previousCutoff until recentCutoff })

        // La tendencia se mide sobre la capacidad, sin acotar, para que el delta en puntos
        // porcentuales siga reflejando cambios pequeños.
        val trend = if (recent != null && previous != null) {
            (healthPercentOf(recent, nominalCapacityMah) - healthPercentOf(previous, nominalCapacityMah))
                .coerceIn(-100f, 100f)
        } else 0f

        return BatteryHealth(
            nominalCapacityMah = nominalCapacityMah,
            estimatedCapacityMah = capacityMah,
            healthPercent = healthPercentOf(capacityMah, nominalCapacityMah).coerceIn(0f, 100f),
            weekTrendPercentPoints = trend
        )
    }

    /** capacidadActual = (mAhAcumulados / %ganado) * 100 */
    private fun sessionCapacityMah(s: ChargingSession): HealthSample? {
        val gained = s.gainedPercent ?: return null
        val mah = s.accumulatedMah ?: return null
        if (gained <= 0 || mah <= 0f) return null
        return HealthSample(s.startTimeMillis, (mah / gained) * 100f, weight = gained.toFloat())
    }

    /** Parte/Total = %/100 → % = Parte * 100 / Total */
    private fun healthPercentOf(capacityMah: Float, nominalCapacityMah: Int): Float =
        if (nominalCapacityMah > 0) capacityMah / nominalCapacityMah * 100f else 0f

    private fun isPlausible(capacityMah: Float, nominalCapacityMah: Int): Boolean =
        capacityMah >= nominalCapacityMah * MIN_PLAUSIBLE_RATIO &&
            capacityMah <= nominalCapacityMah * MAX_PLAUSIBLE_RATIO

    private fun weighted(samples: List<HealthSample>): Float? {
        if (samples.isEmpty()) return null
        val w = samples.sumOf { it.weight.toDouble() }.toFloat()
        if (w <= 0f) return null
        return samples.sumOf { (it.currentCapacityMah * it.weight).toDouble() }.toFloat() / w
    }

    private data class HealthSample(
        val timeMillis: Long,
        val currentCapacityMah: Float,
        val weight: Float
    )
}
