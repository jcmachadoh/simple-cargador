package cu.jcmachadoh.simplecargador.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Resultado de comprobar si hay una versión más reciente publicada. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: UpdateInfo) : UpdateCheckResult
    data class Error(val message: String?) : UpdateCheckResult
}

/**
 * Lógica de actualización in-app: consulta el JSON de versiones, compara con la
 * versión instalada, descarga la APK a un directorio interno y lanza el
 * instalador del sistema.
 */
class AppUpdater(private val context: Context, private val client: UpdateApiClient) {

    /** Comprueba si existe una versión superior a la instalada. No descarga nada. */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val info = client.latest()
            val installed = installedVersionCode()
            if (info.versionCode > installed) UpdateCheckResult.Available(info)
            else UpdateCheckResult.UpToDate
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message)
        }
    }

    /**
     * Descarga la APK y solicita su instalación a través de la UI del sistema.
     * Devuelve true si se lanzó el instalador; el usuario confirma la instalación.
     */
    suspend fun downloadAndInstall(info: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkFile = downloadApk(info.apkUrl)
            launchInstaller(apkFile)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun installedVersionCode(): Int =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) {
            0
        }

    private fun downloadApk(url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update.apk")
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw RuntimeException("Sin cuerpo")
        }
        return file
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** true si la app puede lanzar el instalador (Android 8+ requiere permiso). */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre la pantalla del sistema para autorizar la instalación de apps. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) { /* ROM sin esa pantalla */ }
    }
}
