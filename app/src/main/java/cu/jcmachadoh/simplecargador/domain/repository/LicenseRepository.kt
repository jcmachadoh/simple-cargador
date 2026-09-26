package cu.jcmachadoh.simplecargador.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface LicenseRepository {

    /** true si la licencia Premium está activa. Persistida localmente (RF-13). */
    val isPremium: StateFlow<Boolean>

    fun activate(licenseKey: String)

    fun deactivate()
}
