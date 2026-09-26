package cu.jcmachadoh.simplecargador

import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.utils.security.ActivationCodeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ExportAndActivationTest {

    @Test
    fun `csv tiene cabecera y filas correctas`() {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val session = ChargingSession(
            id = 1,
            startTimeMillis = 0,
            endTimeMillis = 3600_000,
            startLevelPercent = 20,
            endLevelPercent = 45,
            durationSeconds = 3600,
            avgTemperatureCelsius = 30.5f,
            avgVoltageMillivolts = 4000f,
            accumulatedMah = 900f
        )
        val csv = cu.jcmachadoh.simplecargador.domain.usecase.ExportHistoryUseCase.buildCsv(listOf(session), fmt)
        val lines = csv.trim().lines()
        assertEquals(
            "fecha_inicio,fecha_fin,duracion_seg,nivel_inicial,nivel_final,ganancia,temp_media_c,voltaje_medio_mv,mah_acumulados",
            lines[0]
        )
        assertEquals(2, lines.size)
        assert(lines[1].contains(",3600,20,45,25,"))
        assert(lines[1].endsWith("900.0"))
    }

    @Test
    fun `json serializa sesiones`() {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val session = ChargingSession(id = 7, startTimeMillis = 1, startLevelPercent = 10)
        val json = cu.jcmachadoh.simplecargador.domain.usecase.ExportHistoryUseCase.buildJson(listOf(session), fmt)
        assert(json.contains("\"id\": 7") || json.contains("\"id\":7"))
        assert(json.contains("nivelInicial"))
    }

    @Test
    fun `codigo offline valido se acepta con o sin guiones`() {
        // Generamos el código esperado para un deviceId fijo y validamos variantes.
        val expected = ActivationCodeValidator.expectedCode("TEST_DEVICE_123")
        assertTrue(expected.contains("-"))
        assertTrue(ActivationCodeValidator.isValid(expected, "TEST_DEVICE_123"))
        assertTrue(ActivationCodeValidator.isValid(expected.lowercase().replace("-", ""), "TEST_DEVICE_123"))
    }

    @Test
    fun `codigo alterado o de otro dispositivo falla`() {
        val expected = ActivationCodeValidator.expectedCode("DEVICE_A")
        val tampered = if (expected[0] == 'F') "G${expected.drop(1)}" else "F${expected.drop(1)}"
        assertFalse(ActivationCodeValidator.isValid(tampered, "DEVICE_A"))
        assertFalse(ActivationCodeValidator.isValid(expected, "DEVICE_B"))
        assertFalse(ActivationCodeValidator.isValid("", "DEVICE_A"))
    }
}
