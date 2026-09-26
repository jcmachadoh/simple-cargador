package cu.jcmachadoh.simplecargador.utils.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cu.jcmachadoh.simplecargador.SimpleCargadorApp
import java.util.Calendar

/**
 * T5.7 / RNF-04: limpieza semanal.
 * La versión gratuita limita el historial a 30 días; Premium es ilimitado.
 */
class CleanOldSessionsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SimpleCargadorApp ?: return Result.failure()
        if (app.container.licenseRepository.isPremium.value) return Result.success()

        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, -FREE_HISTORY_DAYS)
        }
        app.container.sessionRepository.deleteOlderThan(cal.timeInMillis)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "clean_old_sessions"
        private const val FREE_HISTORY_DAYS = 30
    }
}
