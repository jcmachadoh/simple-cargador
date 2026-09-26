package cu.jcmachadoh.simplecargador.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "start_time") val startTimeMillis: Long,
    @ColumnInfo(name = "end_time") val endTimeMillis: Long? = null,
    @ColumnInfo(name = "start_level") val startLevelPercent: Int,
    @ColumnInfo(name = "end_level") val endLevelPercent: Int? = null,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long? = null,
    @ColumnInfo(name = "avg_temperature") val avgTemperatureCelsius: Float? = null,
    @ColumnInfo(name = "avg_voltage_mv") val avgVoltageMillivolts: Float? = null,
    @ColumnInfo(name = "accumulated_mah") val accumulatedMah: Float? = null
)

fun ChargingSessionEntity.toDomain(): ChargingSession = ChargingSession(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    startLevelPercent = startLevelPercent,
    endLevelPercent = endLevelPercent,
    durationSeconds = durationSeconds,
    avgTemperatureCelsius = avgTemperatureCelsius,
    avgVoltageMillivolts = avgVoltageMillivolts,
    accumulatedMah = accumulatedMah
)

fun ChargingSession.toEntity(): ChargingSessionEntity = ChargingSessionEntity(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    startLevelPercent = startLevelPercent,
    endLevelPercent = endLevelPercent,
    durationSeconds = durationSeconds,
    avgTemperatureCelsius = avgTemperatureCelsius,
    avgVoltageMillivolts = avgVoltageMillivolts,
    accumulatedMah = accumulatedMah
)
