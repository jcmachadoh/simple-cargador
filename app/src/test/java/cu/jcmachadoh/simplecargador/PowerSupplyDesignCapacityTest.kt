package cu.jcmachadoh.simplecargador

import cu.jcmachadoh.simplecargador.data.system.PowerSupplyDesignCapacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Cubre la lectura de /sys/class/power_supply: unidades (µAh, mAh, µWh) y descarte de
 * lecturas corruptas. El denominador de la fórmula de salud sale de aquí, así que una
 * unidad mal interpretada convierte el "vida de la batería" en basura.
 */
class PowerSupplyDesignCapacityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun battery(name: String = "battery", files: Map<String, String>) =
        temp.newFolder(name).also { dir ->
            files.forEach { (file, content) -> dir.resolve(file).writeText(content) }
        }

    private fun reader() = PowerSupplyDesignCapacity(temp.root)

    @Test
    fun `lee charge_full_design en micro amperio hora`() {
        battery(files = mapOf("type" to "Battery", "charge_full_design" to "4500000"))
        assertEquals(4500, reader().readMah())
    }

    @Test
    fun `acepta el valor si el vendor ya lo da en miliamperio hora`() {
        battery(files = mapOf("type" to "Battery", "charge_full_design" to "3200"))
        assertEquals(3200, reader().readMah())
    }

    @Test
    fun `convierte de micro watt hora usando el voltaje de diseño`() {
        battery(
            files = mapOf(
                "type" to "Battery",
                "charge_full_design" to "15500000",
                "energy_full_design" to "15500000",
                "voltage_max_design" to "3850000"
            )
        )
        // 15.5 Wh / 3.85 V = 4026 mAh
        assertEquals(4025, reader().readMah())
    }

    @Test
    fun `usa voltage_now cuando no hay voltage_max_design`() {
        battery(
            files = mapOf(
                "type" to "Battery",
                "energy_full_design" to "15000000",
                "voltage_now" to "3750000"
            )
        )
        assertEquals(4000, reader().readMah())
    }

    @Test
    fun `devuelve null si el valor es energia pero no hay voltaje para convertir`() {
        battery(files = mapOf("type" to "Battery", "charge_full_design" to "15000000"))
        assertNull(reader().readMah())
    }

    @Test
    fun `ignora una fuente que no es la bateria`() {
        battery(name = "usb", files = mapOf("type" to "USB", "charge_full_design" to "500"))
        battery(name = "battery", files = mapOf("type" to "Battery", "charge_full_design" to "4100000"))
        assertEquals(4100, reader().readMah())
    }

    @Test
    fun `devuelve null con valores corruptos`() {
        battery(files = mapOf("type" to "Battery", "charge_full_design" to "0"))
        assertNull(reader().readMah())

        battery(name = "b2", files = mapOf("type" to "Battery", "charge_full_design" to "abc"))
        assertNull(reader().readMah())

        battery(name = "b3", files = mapOf("type" to "Battery", "charge_full_design" to "-4500000"))
        assertNull(reader().readMah())

        battery(name = "b4", files = mapOf("type" to "Battery", "charge_full_design" to "999999999999999"))
        assertNull(reader().readMah())
    }

    @Test
    fun `devuelve null si el kernel no expone nada`() {
        battery(files = mapOf("type" to "Battery", "capacity" to "87"))
        assertNull(reader().readMah())
    }

    @Test
    fun `devuelve null si el directorio no existe`() {
        assertNull(PowerSupplyDesignCapacity(temp.root.resolve("nope")).readMah())
    }
}
