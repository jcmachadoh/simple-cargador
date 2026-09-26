package cu.jcmachadoh.simplecargador.data.system

import java.io.File

/**
 * Capacidad de diseño de fábrica de la batería leída del kernel, en mAh.
 *
 * Fuente: `/sys/class/power_supply/<battery>/charge_full_design` (y sus variantes), que es
 * el valor de fábrica que el fabricante declara en el power supply class. Es la misma
 * información que expone `PowerProfile`, pero en crudo y sin depender de APIs ocultas.
 *
 * Con un fuel gauge por voltaje (sin sensor de coulomb) el vendor publica energía en µWh
 * en vez de carga en µAh, así que se intenta interpretar en ambos sentidos y solo se
 * acepta un resultado dentro de una ventana plausible. Preferimos NO devolver un número
 * antes que devolver la unidad equivocada: un denominador 10x desviado convierte la salud
 * en un número sin sentido. El único caso que no se puede detectar es un vendor que
 * publique µWh en `charge_full_design` sin exponer `energy_full_design` ni voltaje, y que
 * además dé un valor que caiga por debajo del techo de plausibilidad.
 *
 * NUNCA se usa `charge_full` para esto: ese archivo es la capacidad de carga completa
 * *actual* (la vida real de la batería), no la de fábrica. Como denominador daría siempre
 * 100% de salud.
 */
internal class PowerSupplyDesignCapacity(private val root: File = File(POWER_SUPPLY_ROOT)) {

    /** Capacidad de diseño en mAh, o null si el kernel no la expone. */
    fun readMah(): Int? {
        val dirs = root.listFiles()?.sortedBy { it.name }.orEmpty()
        // Las fuentes tipo Battery primero: en un puerto USB también hay archivos de carga.
        val ordered = dirs.filter { it.isBattery() } + dirs.filterNot { it.isBattery() }
        for (dir in ordered) {
            designMahOf(dir)?.let { return it }
        }
        return null
    }

    private fun File.isBattery(): Boolean =
        readText("type")?.trim()?.equals("Battery", ignoreCase = true) == true

    private fun designMahOf(dir: File): Int? {
        val chargeDesign = dir.readLong("charge_full_design")

        // energy_full_design está en µWh por definición: si existe, el vendor trabaja con energía.
        val energyDesign = dir.readLong("energy_full_design")
        if (energyDesign != null) {
            mahFromEnergy(energyDesign, dir.nominalMillivolts())?.let { return it }
        }

        if (chargeDesign != null) {
            // Sin señal de energía: µAh según la spec (algunos OEM ya lo dan en mAh).
            asMah(chargeDesign)?.let { return it }
            mahFromEnergy(chargeDesign, dir.nominalMillivolts())?.let { return it }
        }
        return null
    }

    /** µAh (spec) o directamente mAh (algunos OEM). */
    private fun asMah(raw: Long): Int? = when {
        raw in MIN_PLAUSIBLE_MAH.toLong()..MAX_PLAUSIBLE_MAH.toLong() -> raw.toInt()
        raw / 1000L in MIN_PLAUSIBLE_MAH.toLong()..MAX_PLAUSIBLE_MAH.toLong() -> (raw / 1000L).toInt()
        else -> null
    }

    /** mAh = µWh / voltios */
    private fun mahFromEnergy(energyMicroWh: Long, microVolts: Long?): Int? {
        if (microVolts == null || microVolts <= 0L) return null
        val mah = energyMicroWh.toDouble() * 1000.0 / microVolts.toDouble()
        return mah.takeIf { it in MIN_PLAUSIBLE_MAH.toDouble()..MAX_PLAUSIBLE_MAH.toDouble() }?.toInt()
    }

    private fun File.nominalMillivolts(): Long? =
        readLong("voltage_max_design") ?: readLong("voltage_now") ?: readLong("voltage_avg")

    private fun File.readText(name: String): String? = try {
        File(this, name).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    private fun File.readLong(name: String): Long? = readText(name)
        ?.toLongOrNull()
        ?.takeIf { it > 0L && it < MAX_SANE_RAW }

    companion object {
        const val POWER_SUPPLY_ROOT = "/sys/class/power_supply"

        /**
         * Ventana plausible para la capacidad de una batería de móvil, en mAh.
         *
         * El techo de 12000 mAh es deliberado: si un vendor con fuel gauge por voltaje
         * publica 15 Wh en `charge_full_design`, Dividirlo por 1000 daría 15000 mAh — un
         * valor que parece creíble pero es 3x una batería real. Con este techo esa lectura
         * se rechaza en vez de contaminar el denominador.
         */
        const val MIN_PLAUSIBLE_MAH = 200
        const val MAX_PLAUSIBLE_MAH = 12000

        /** 1e12 µAh o µWh sería 1 GWh: imposible, sirve para descartar basura. */
        private const val MAX_SANE_RAW = 1_000_000_000_000L
    }
}
