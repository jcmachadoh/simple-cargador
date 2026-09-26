package cu.jcmachadoh.simplecargador.domain.model

data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
    val voltageMillivolts: Int?,
    val temperatureCelsius: Float?,
    val currentMilliamps: Int?,
    val chargeCounterMicroAh: Int?
)
