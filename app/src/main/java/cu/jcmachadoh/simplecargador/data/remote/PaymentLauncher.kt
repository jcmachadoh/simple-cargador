package cu.jcmachadoh.simplecargador.data.remote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Paso 3: intenta abrir la app Enzona nativa; si no está instalada, cae al navegador.
 *
 * Nota: el apk oficial de Enzona (cu.xetid.apk.enzona) NO declara ningún
 * <intent-filter> VIEW/BROWSABLE, por lo que un ACTION_VIEW con
 * https://... y setPackage("cu.xetid.apk.enzona") siempre falla
 * (ActivityNotFoundException) y acaba cayendo al navegador. Eso es
 * **esperado** tanto en mock como en real: el checkout de Enzona es
 * una página web (portal.enzona.net) que se abre en navegador/Custom Tab.
 *
 * Para que en MODO DESARROLLO el usuario VEA que la integración
 * "levanta la apk", este Launcher, cuando USE_MOCK=true y Enzona está
 * instalada, hace fallback a lanzar directamente la MainActivity de
 * Enzona (getLaunchIntentForPackage) tras fallar el VIEW. Así se
 * demuestra que la app es detectada; en producción el flujo real es
 * el VIEW+browser.
 */
object PaymentLauncher {

    private const val TAG = "PaymentLauncher"

    fun openPayment(context: Context, linkConfirm: String): Boolean {
        val uri = Uri.parse(linkConfirm)
        val pm = context.packageManager

        val isEnzonaInstalled = try {
            pm.getPackageInfo(EnzonaConfig.ENZONA_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

        // MODO MOCK: intenta VIEW con paquete y, si no lo maneja (caso real
        // de Enzona), lanza directamente la app para demostrar integración.
        if (EnzonaConfig.USE_MOCK && isEnzonaInstalled) {
            val viewWithPkg = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(EnzonaConfig.ENZONA_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val canHandle = pm.queryIntentActivities(viewWithPkg, 0).isNotEmpty()
            if (canHandle) {
                try {
                    context.startActivity(viewWithPkg)
                    Log.d(TAG, "Mock: Enzona handled VIEW for $linkConfirm")
                    return true
                } catch (_: Exception) { /* cae al launch */ }
            }
            pm.getLaunchIntentForPackage(EnzonaConfig.ENZONA_PACKAGE)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launch)
                    Log.d(TAG, "Mock: opened Enzona main directly (integration demo)")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Mock launch failed: ${e.message}")
                }
            }
            // si aun así falla, sigue al fallback de navegador
        }

        // Flujo real (y fallback del mock): VIEW con paquete → navegador
        val enzoneIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(EnzonaConfig.ENZONA_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(enzoneIntent)
            Log.d(TAG, "Opened Enzona app: $linkConfirm")
            true
        } catch (_: ActivityNotFoundException) {
            Log.d(TAG, "Enzona no maneja VIEW para $linkConfirm (esperado), fallback a navegador")
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open payment link: ${e.message}")
                false
            }
        }
    }

    /** Comprueba si Enzona está instalada (requiere <queries> en el Manifest). */
    fun isEnzonaInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(EnzonaConfig.ENZONA_PACKAGE, 0)
        true
    } catch (_: Exception) { false }
}
