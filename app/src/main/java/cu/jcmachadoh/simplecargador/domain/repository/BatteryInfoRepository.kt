package cu.jcmachadoh.simplecargador.domain.repository

import cu.jcmachadoh.simplecargador.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow

interface BatteryInfoRepository {

    fun snapshot(): BatterySnapshot

    fun observeChanges(): Flow<BatterySnapshot>

    fun nominalCapacityMah(): Int

    /**
     * Capacidad de carga completa actual en mAh, medida con el contador de carga del
     * sistema cuando la batería llega al 100%. Es la fuente directa (no estimada) de la
     * salud. Null si el OEM no expone el contador o la batería aún no llegó al 100%.
     */
    fun fullChargeCapacityMah(): Int?
}
