package cu.jcmachadoh.simplecargador.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cu.jcmachadoh.simplecargador.domain.repository.LicenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RF-12 / RNF-05: licencia Premium cifrada con AES mediante EncryptedSharedPreferences.
 * Lectura inicial síncrona (una vez) para que la verificación en el arranque sea
 * instantánea y sin conectividad (RF-13).
 */
class LicenseManager(context: Context) : LicenseRepository {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _isPremium = MutableStateFlow(false)

    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    init {
        _isPremium.value = prefs.getBoolean(KEY_PREMIUM, false)
    }

    fun warmUpAsync(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch(Dispatchers.IO) { prefs.getBoolean(KEY_PREMIUM, false) }
    }

    override fun activate(licenseKey: String) {
        prefs.edit()
            .putBoolean(KEY_PREMIUM, true)
            .putString(KEY_LICENSE, licenseKey)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
        _isPremium.value = true
    }

    override fun deactivate() {
        prefs.edit().clear().apply()
        _isPremium.value = false
    }

    fun activatedAtMillis(): Long = prefs.getLong(KEY_ACTIVATED_AT, 0L)

    private companion object {
        const val FILE_NAME = "sc_secure_prefs"
        const val KEY_PREMIUM = "premium_active"
        const val KEY_LICENSE = "license_key"
        const val KEY_ACTIVATED_AT = "activated_at"
    }
}
