package cu.jcmachadoh.simplecargador.domain.model

data class ChargingSession(
    val id: Long = 0L,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val startLevelPercent: Int,
    val endLevelPercent: Int? = null,
    val durationSeconds: Long? = null,
    val avgTemperatureCelsius: Float? = null,
    val avgVoltageMillivolts: Float? = null,
    val accumulatedMah: Float? = null
) {
    val inProgress: Boolean get() = endTimeMillis == null

    val gainedPercent: Int?
        get() {
            if (endLevelPercent == null) return null
            return endLevelPercent - startLevelPercent
        }
}
