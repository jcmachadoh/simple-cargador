package cu.jcmachadoh.simplecargador.domain.model

data class BatteryHealth(
    val nominalCapacityMah: Int,
    val estimatedCapacityMah: Float,
    val healthPercent: Float,
    val weekTrendPercentPoints: Float
)
