package cu.jcmachadoh.simplecargador.di

import android.content.Context
import android.util.Log
import cu.jcmachadoh.simplecargador.data.local.database.AppDatabase
import cu.jcmachadoh.simplecargador.data.local.preferences.LicenseManager
import cu.jcmachadoh.simplecargador.data.local.preferences.PendingPaymentStore
import cu.jcmachadoh.simplecargador.data.local.preferences.SettingsStore
import cu.jcmachadoh.simplecargador.data.local.repository.ChargingSessionRepositoryImpl
import cu.jcmachadoh.simplecargador.data.remote.EnzonaApiClient
import cu.jcmachadoh.simplecargador.data.remote.PaymentGatewayImpl
import cu.jcmachadoh.simplecargador.data.system.BatteryReader
import cu.jcmachadoh.simplecargador.data.system.ChargingSessionManager
import cu.jcmachadoh.simplecargador.data.update.AppUpdater
import cu.jcmachadoh.simplecargador.data.update.UpdateApiClient
import cu.jcmachadoh.simplecargador.domain.repository.ChargingSessionRepository
import cu.jcmachadoh.simplecargador.domain.usecase.CheckLicenseUseCase
import cu.jcmachadoh.simplecargador.domain.usecase.ProcessPaymentUseCase
import cu.jcmachadoh.simplecargador.utils.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope

/**
 * RNF-09: contenedor de dependencias manual, sin Hilt/Dagger para mantener la APK ligera.
 */
class AppContainer(val appContext: Context, private val scope: CoroutineScope) {

    val settingsStore by lazy { SettingsStore(appContext) }

    /** Directorio para exportaciones CSV/JSON (RF-10). */
    val cacheDir: java.io.File by lazy {
        java.io.File(appContext.cacheDir, "exports").apply { mkdirs() }
    }

    val notificationHelper by lazy { NotificationHelper(appContext) }

    private val database by lazy { AppDatabase.get(appContext) }

    val sessionRepository: ChargingSessionRepository by lazy {
        ChargingSessionRepositoryImpl(database.chargingSessionDao())
    }

    val batteryReader by lazy { BatteryReader(appContext) }

    val licenseRepository: LicenseManager by lazy { LicenseManager(appContext) }

    val pendingPaymentStore: PendingPaymentStore by lazy { PendingPaymentStore(appContext) }

    private val enzonaClient by lazy { EnzonaApiClient() }

    private val paymentGateway by lazy { PaymentGatewayImpl(enzonaClient) }

    val updateApiClient by lazy { UpdateApiClient() }

    val appUpdater by lazy { AppUpdater(appContext, updateApiClient) }

    val processPaymentUseCase by lazy {
        ProcessPaymentUseCase(paymentGateway, licenseRepository, pendingPaymentStore)
    }

    val checkLicenseUseCase by lazy { CheckLicenseUseCase(licenseRepository) }

    /**
     * Núcleo de detección de carga.
     * Fix: no se cachea null. Si la inicialización falla, se reintenta en el próximo acceso.
     * Un error transitorio (DB bloqueada) no debe deshabilitar el tracking permanentemente.
     */
    val chargingSessionManager: ChargingSessionManager by lazy {
        ChargingSessionManager(
            context = appContext,
            scope = scope,
            repository = sessionRepository,
            batteryReader = batteryReader,
            notifier = notificationHelper,
            alertsEnabled = { settingsStore.alertsEnabled },
            isPremium = { licenseRepository.isPremium.value }
        )
    }

    /** Llamado desde MainActivity al abrir la app: reconcilia sesiones con el estado real. */
    fun onAppOpened() {
        try {
            chargingSessionManager.reconcileOnAppOpen()
        } catch (e: Throwable) {
            Log.e(TAG, "reconcileOnAppOpen failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AppContainer"
    }
}
