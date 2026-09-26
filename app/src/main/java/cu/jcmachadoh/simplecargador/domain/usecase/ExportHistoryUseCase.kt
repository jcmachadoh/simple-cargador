package cu.jcmachadoh.simplecargador.domain.usecase

import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RF-10: exporta el historial completo a CSV y JSON.
 * Los constructores son puros (testeables); la escritura recibe un directorio destino.
 */
object ExportHistoryUseCase {

    @Serializable
    data class SessionDto(
        val id: Long,
        val fechaInicio: String,
        val fechaFin: String?,
        val inicioEpochMillis: Long,
        val finEpochMillis: Long?,
        val duracionSegundos: Long?,
        val nivelInicial: Int,
        val nivelFinal: Int?,
        val ganancia: Int?,
        val tempMediaCelsius: Double?,
        val voltajeMedioMv: Double?,
        val mahAcumulados: Double?
    )

    private val json = Json { prettyPrint = true }

    fun buildCsv(sessions: List<ChargingSession>, dateFormat: SimpleDateFormat): String {
        val sb = StringBuilder()
        sb.append("fecha_inicio,fecha_fin,duracion_seg,nivel_inicial,nivel_final,ganancia,temp_media_c,voltaje_medio_mv,mah_acumulados\n")
        for (s in sessions) {
            val row = listOf(
                dateFormat.format(Date(s.startTimeMillis)),
                s.endTimeMillis?.let { dateFormat.format(Date(it)) } ?: "",
                s.durationSeconds?.toString() ?: "",
                s.startLevelPercent.toString(),
                s.endLevelPercent?.toString() ?: "",
                s.gainedPercent?.toString() ?: "",
                s.avgTemperatureCelsius?.toString() ?: "",
                s.avgVoltageMillivolts?.toString() ?: "",
                s.accumulatedMah?.toString() ?: ""
            )
            sb.append(row.joinToString(",")).append('\n')
        }
        return sb.toString()
    }

    fun buildJson(sessions: List<ChargingSession>, dateFormat: SimpleDateFormat): String {
        val dtos = sessions.map { s ->
            SessionDto(
                id = s.id,
                fechaInicio = dateFormat.format(Date(s.startTimeMillis)),
                fechaFin = s.endTimeMillis?.let { dateFormat.format(Date(it)) },
                inicioEpochMillis = s.startTimeMillis,
                finEpochMillis = s.endTimeMillis,
                duracionSegundos = s.durationSeconds,
                nivelInicial = s.startLevelPercent,
                nivelFinal = s.endLevelPercent,
                ganancia = s.gainedPercent,
                tempMediaCelsius = s.avgTemperatureCelsius?.toDouble(),
                voltajeMedioMv = s.avgVoltageMillivolts?.toDouble(),
                mahAcumulados = s.accumulatedMah?.toDouble()
            )
        }
        return json.encodeToString(ListSerializer(SessionDto.serializer()), dtos)
    }

    /** Escribe ambos ficheros en [outputDir] y devuelve los archivos creados. */
    fun writeFiles(sessions: List<ChargingSession>, outputDir: File, timestamp: Long): Pair<File, File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val csvFile = File(outputDir, "simplecargador_historial_$stamp.csv")
        val jsonFile = File(outputDir, "simplecargador_historial_$stamp.json")
        csvFile.writeText(buildCsv(sessions, fmt))
        jsonFile.writeText(buildJson(sessions, fmt))
        return csvFile to jsonFile
    }
}
