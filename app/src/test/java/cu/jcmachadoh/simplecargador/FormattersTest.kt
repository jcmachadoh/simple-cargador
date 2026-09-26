package cu.jcmachadoh.simplecargador

import cu.jcmachadoh.simplecargador.utils.extensions.formatLongDuration
import cu.jcmachadoh.simplecargador.utils.extensions.formatShortDuration
import cu.jcmachadoh.simplecargador.utils.extensions.signedPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `duracion corta en horas`() {
        assertEquals("2 h 05 min", (2 * 3600L + 5 * 60L).formatShortDuration())
    }

    @Test
    fun `duracion corta solo minutos`() {
        assertEquals("48 min", (48 * 60L).formatShortDuration())
    }

    @Test
    fun `duracion corta solo segundos`() {
        assertEquals("35 s", 35L.formatShortDuration())
    }

    @Test
    fun `nulo o cero devuelve guion`() {
        assertEquals("—", null.formatShortDuration())
        assertEquals("—", 0L.formatShortDuration())
    }

    @Test
    fun `duracion larga`() {
        assertEquals("1h 23m", (3600L + 23 * 60L).formatLongDuration())
    }

    @Test
    fun `porcentaje con signo`() {
        assertEquals("+12%", 12.signedPercent())
        assertEquals("-3%", (-3).signedPercent())
    }
}
