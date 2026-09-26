package cu.jcmachadoh.simplecargador.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cu.jcmachadoh.simplecargador.SimpleCargadorApp
import java.util.concurrent.TimeUnit

/**
 * Worker que refresca el widget y, para intervalos < 15 min, re-encola el siguiente tramo.
 * Para intervalos >=15 el WorkManager periódico se encarga del re-disparo automático.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SimpleCargadorApp
                ?: return Result.failure()

            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) {
                Log.d(TAG, "No hay widgets; no se refresca y no se reprograma")
                return Result.success()
            }

            // Actualización síncrona para garantizar que el widget quede fresco antes de retornar.
            WidgetUpdater.updateAllBlocking(
                context = app,
                repository = app.container.sessionRepository,
                batteryReader = app.container.batteryReader,
                isPremium = app.container.licenseRepository.isPremium.value
            )

            // Para intervalos cortos (<15) el Worker debe encadenar el siguiente OneTime.
            val interval = app.container.settingsStore.widgetUpdateIntervalMinutes
            if (interval < 15) {
                // Verificar que siguen existiendo widgets antes de re-encolar
                val stillHas = try {
                    mgr.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java)).isNotEmpty()
                } catch (_: Throwable) { false }
                if (stillHas) {
                    val next = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                        .setInitialDelay(interval.toLong(), TimeUnit.MINUTES)
                        .addTag(WidgetUpdateScheduler.TAG)
                        .build()
                    WorkManager.getInstance(app).enqueueUniqueWork(
                        WidgetUpdateScheduler.UNIQUE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        next
                    )
                    Log.d(TAG, "Re-encolado siguiente tramo en $interval min")
                }
            }

            Result.success()
        } catch (e: Throwable) {
            Log.w(TAG, "doWork failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WidgetRefreshWorker"
    }
}
