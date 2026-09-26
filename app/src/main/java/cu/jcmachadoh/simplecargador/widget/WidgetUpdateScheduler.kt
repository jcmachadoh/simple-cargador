package cu.jcmachadoh.simplecargador.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cu.jcmachadoh.simplecargador.SimpleCargadorApp
import java.util.concurrent.TimeUnit

/**
 * Gestiona el refresco periódico del widget según el intervalo configurado por el usuario
 * (5 min – 60 min). Usa WorkManager para respetar Doze sin permisos de alarma exacta.
 *
 * - interval >= 15 min -> PeriodicWorkRequest nativo (eficiente, batchable)
 * - interval < 15 min  -> cadena de OneTimeWorkRequest (permite 5/10 min, mínimo de Periodic es 15)
 *
 * El worker [WidgetRefreshWorker] re-encola el siguiente tramo cuando interval < 15.
 */
object WidgetUpdateScheduler {

    const val UNIQUE_WORK_NAME = "widget_refresh"
    const val TAG = "WidgetRefresh"
    private const val LOG_TAG = "WidgetScheduler"

    fun schedule(context: Context) {
        val app = context.applicationContext as? SimpleCargadorApp ?: return
        val interval = app.container.settingsStore.widgetUpdateIntervalMinutes
        schedule(context, interval)
    }

    fun schedule(context: Context, intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceIn(5, 60)

        val wm = WorkManager.getInstance(context.applicationContext)

        // Si no hay widgets activos, no tiene sentido programar trabajo.
        if (!hasWidget(context)) {
            Log.d(LOG_TAG, "No hay widgets; cancelando trabajo periódico (interval=$safeInterval)")
            cancel(context)
            return
        }

        // Cancelar cualquier trabajo previo de ambos tipos antes de programar el nuevo.
        // WorkManager comparte el namespace uniqueName entre periodic/one-time, así que cancelamos ambos.
        try { wm.cancelUniqueWork(UNIQUE_WORK_NAME) } catch (_: Throwable) {}
        // En algunos WorkManager cancelUniqueWork cancela periodic también; por seguridad esperamos.

        if (safeInterval >= 15) {
            Log.d(LOG_TAG, "Programando PeriodicWork cada $safeInterval min")
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(safeInterval.toLong(), TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            Log.d(LOG_TAG, "Programando OneTime chain cada $safeInterval min")
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(safeInterval.toLong(), TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        } catch (e: Throwable) {
            Log.w(LOG_TAG, "cancel failed: ${e.message}")
        }
    }

    /** Re-programa solo si hay widgets; útil tras BOOT o cambio de intervalo. */
    fun rescheduleIfWidgets(context: Context) {
        if (hasWidget(context)) schedule(context) else cancel(context)
    }

    fun hasWidget(context: Context): Boolean {
        return try {
            val mgr = AppWidgetManager.getInstance(context.applicationContext)
            val ids = mgr.getAppWidgetIds(ComponentName(context.applicationContext, BatteryWidgetProvider::class.java))
            ids.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
}
