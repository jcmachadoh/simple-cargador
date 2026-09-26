package cu.jcmachadoh.simplecargador

import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.domain.usecase.CalculateHealthUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateHealthUseCaseTest {

    private val week = 7L * 24 * 60 * 60 * 1000
    private val now = 100L * week

    private fun session(
        start: Long,
        gained: Int,
        mah: Float
    ): ChargingSession = ChargingSession(
        startTimeMillis = start,
        endTimeMillis = start + 3_600_000,
        startLevelPercent = 100 - gained,
        endLevelPercent = 100,
        durationSeconds = 3600,
        accumulatedMah = mah
    )

    @Test
    fun `salud 100 por ciento con bateria perfecta`() {
        // 30% ganados consumiendo 1200 mAh → capacidad real 4000 mAh == nominal
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(1, gained = 30, mah = 1200f)),
            nominalCapacityMah = 4000,
            nowMillis = now
        )
        assertNotNull(result)
        assertEquals(100f, result!!.healthPercent, 0.5f)
    }

    @Test
    fun `bateria degradada da menos de 100`() {
        // 40% ganados con 1200 mAh → capacidad real 3000 mAh de 4000 de fábrica → 75%
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(2, gained = 40, mah = 1200f)),
            nominalCapacityMah = 4000,
            nowMillis = now
        )
        assertNotNull(result)
        assertEquals(75f, result!!.healthPercent, 1f)
        assertEquals(3000f, result.estimatedCapacityMah, 1f)
    }

    @Test
    fun `la salud nunca supera 100 con capacidad mayor que la de fabrica`() {
        // 20% ganados con 1000 mAh → capacidad real 5000 mAh (el doble de la de fábrica)
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(3, gained = 20, mah = 1000f)),
            nominalCapacityMah = 4000,
            nowMillis = now
        )
        assertNotNull(result)
        assertEquals(100f, result!!.healthPercent, 0.01f)
    }

    @Test
    fun `un cero en el contador de carga no produce salud`() {
        val result = CalculateHealthUseCase.fromSessions(
            sessions = emptyList(),
            nominalCapacityMah = 4000,
            nowMillis = now,
            fullChargeCapacityMah = 0
        )
        assertNull(result)
    }

    @Test
    fun `usa la capacidad medida al 100 por ciento en lugar de la estimacion por corriente`() {
        // Sesiones estiman 3000 mAh, pero el contador de carga midió 3400 mAh al 100%
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(4, gained = 40, mah = 1200f)),
            nominalCapacityMah = 4000,
            nowMillis = now,
            fullChargeCapacityMah = 3400
        )
        assertNotNull(result)
        assertEquals(3400f, result!!.estimatedCapacityMah, 1f)
        assertEquals(85f, result.healthPercent, 0.5f)
    }

    @Test
    fun `descarta una capacidad medida absurda y cae a la estimacion por corriente`() {
        // Contador roto: 200 mAh en una batería de 4000 → no se usa
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(5, gained = 40, mah = 1200f)),
            nominalCapacityMah = 4000,
            nowMillis = now,
            fullChargeCapacityMah = 200
        )
        assertNotNull(result)
        assertEquals(75f, result!!.healthPercent, 1f)
    }

    @Test
    fun `sin contador de carga la capacidad medida manda igual sobre sesiones`() {
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(session(6, gained = 40, mah = 1200f)),
            nominalCapacityMah = 4000,
            nowMillis = now,
            fullChargeCapacityMah = 3200
        )
        assertNotNull(result)
        assertEquals(80f, result!!.healthPercent, 0.5f)
    }

    @Test
    fun `sesiones sin datos de mAh no producen salud`() {
        val s = ChargingSession(
            startTimeMillis = 1, endTimeMillis = 2,
            startLevelPercent = 50, endLevelPercent = 80, durationSeconds = 300
        )
        assertNull(CalculateHealthUseCase.fromSessions(listOf(s), 4000, now))
    }

    @Test
    fun `lista vacia devuelve null`() {
        assertNull(CalculateHealthUseCase.fromSessions(emptyList(), 4000, now))
    }

    @Test
    fun `capacidad de fabrica invalida devuelve null`() {
        val s = listOf(session(7, gained = 40, mah = 1200f))
        assertNull(CalculateHealthUseCase.fromSessions(s, 0, now))
    }

    @Test
    fun `tendencia semanal positiva cuando la salud mejora`() {
        // Semana anterior: capacidad 3200 mAh (80%) | semana actual: 3600 mAh (90%)
        val previousWeek = session(now - week - 1000, gained = 20, mah = 640f)
        val currentWeek = session(now - 1000, gained = 20, mah = 720f)
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(previousWeek, currentWeek),
            nominalCapacityMah = 4000,
            nowMillis = now
        )
        assertNotNull(result)
        assertEquals(10f, result!!.weekTrendPercentPoints, 1f)
    }

    @Test
    fun `tendencia semanal negativa cuando la salud empeora`() {
        // Semana anterior: capacidad 3600 mAh (90%) | semana actual: 3200 mAh (80%)
        val previousWeek = session(now - week - 1000, gained = 20, mah = 720f)
        val currentWeek = session(now - 1000, gained = 20, mah = 640f)
        val result = CalculateHealthUseCase.fromSessions(
            sessions = listOf(previousWeek, currentWeek),
            nominalCapacityMah = 4000,
            nowMillis = now
        )
        assertNotNull(result)
        assertEquals(-10f, result!!.weekTrendPercentPoints, 1f)
    }
}
