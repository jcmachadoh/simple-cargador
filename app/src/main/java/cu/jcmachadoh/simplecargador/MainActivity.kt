package cu.jcmachadoh.simplecargador

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cu.jcmachadoh.simplecargador.ui.navigation.AppNavHost
import cu.jcmachadoh.simplecargador.ui.theme.SimpleCargadorTheme
import cu.jcmachadoh.simplecargador.ui.theme.ThemeMode
import cu.jcmachadoh.simplecargador.utils.LocaleManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val stored = (newBase.applicationContext as SimpleCargadorApp)
            .container.settingsStore.languageCode
        super.attachBaseContext(LocaleManager.applyLocale(newBase, stored))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        (application as SimpleCargadorApp).container.onAppOpened() // T2.3: sesión huérfana
        handleEnzonaDeepLink(intent)

        val container = (application as SimpleCargadorApp).container
        setContent {
            val themeMode by container.settingsStore.themeMode.collectAsStateWithLifecycle()
            SimpleCargadorTheme(themeMode = themeMode) {
                AppNavHost(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEnzonaDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Paso 4: si vuelve a primer plano y hay un pago pendiente, re-disparamos el Paso 5.
        verifyPendingPaymentIfNeeded()
    }

    private fun handleEnzonaDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "simplecargador" && data.host == "enzona") {
            Log.d("MainActivity", "Enzona deep link: $data")
            verifyPendingPaymentIfNeeded()
        }
    }

    private fun verifyPendingPaymentIfNeeded() {
        val container = (application as SimpleCargadorApp).container
        if (container.checkLicenseUseCase.isPremiumNow()) return
        val uuid = container.pendingPaymentStore.getPendingUuid() ?: return
        // Evita verificar si no hay pendiente o ya está completado
        lifecycleScope.launch {
            Log.d("MainActivity", "Verificando pago pendiente: $uuid")
            try {
                container.processPaymentUseCase.verifyPayment(uuid)
            } catch (e: Exception) {
                Log.e("MainActivity", "verifyPending failed: ${e.message}")
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
