package cu.jcmachadoh.simplecargador.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.domain.model.BatterySnapshot
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.domain.repository.BatteryInfoRepository
import cu.jcmachadoh.simplecargador.domain.repository.ChargingSessionRepository
import cu.jcmachadoh.simplecargador.domain.usecase.CalculateHealthUseCase
import cu.jcmachadoh.simplecargador.utils.extensions.formatShortDuration
import cu.jcmachadoh.simplecargador.utils.extensions.startOfTodayMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Empuja el estado actual a todas las instancias del widget (RF-09). Solo Premium. */
object WidgetUpdater {

    fun updateAll(
        context: Context,
        scope: CoroutineScope,
        repository: ChargingSessionRepository,
        batteryReader: BatteryInfoRepository,
        isPremium: Boolean = true
    ) {
        val app = context.applicationContext
        scope.launch(Dispatchers.Default) {
            updateAllBlocking(app, repository, batteryReader, isPremium)
        }
    }

    /**
     * Versión suspendida / bloqueante para Workers y receivers que necesitan
     * garantizar que el widget quedó actualizado antes de retornar.
     */
    suspend fun updateAllBlocking(
        context: Context,
        repository: ChargingSessionRepository,
        batteryReader: BatteryInfoRepository,
        isPremium: Boolean = true
    ) {
        withContext(Dispatchers.Default) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) return@withContext

            val snap = batteryReader.snapshot()

            var last: ChargingSession? = null
            var todaySessions: List<ChargingSession> = emptyList()
            var inProgress: ChargingSession? = null
            var allSessions: List<ChargingSession> = emptyList()
            withContext(Dispatchers.IO) {
                last = repository.getLastCompletedOnce()
                todaySessions = repository.getByRange(startOfTodayMillis(), Long.MAX_VALUE)
                inProgress = repository.getInProgress()
                allSessions = repository.getAllOnce()
            }

            // Tiempo de carga del día (siempre visible, como en Home)
            val todayDoneSeconds = todaySessions.filter { it.endTimeMillis != null }
                .sumOf { it.durationSeconds ?: 0L }
            val liveToday = if (inProgress != null && inProgress.startTimeMillis >= startOfTodayMillis()) {
                ((System.currentTimeMillis() - inProgress.startTimeMillis).coerceAtLeast(0)) / 1000
            } else 0L
            val totalTodaySeconds = todayDoneSeconds + liveToday
            val todayText = if (totalTodaySeconds > 0) {
                app.getString(R.string.widget_time_today, totalTodaySeconds.formatShortDuration())
            } else {
                app.getString(R.string.widget_time_today_none)
            }

            // Salud / vida de la batería (Premium)
            val health = CalculateHealthUseCase.fromSessions(
                sessions = allSessions,
                nominalCapacityMah = batteryReader.nominalCapacityMah(),
                nowMillis = System.currentTimeMillis(),
                fullChargeCapacityMah = batteryReader.fullChargeCapacityMah()
            )
            val healthText = if (!isPremium) {
                app.getString(R.string.widget_health_premium)
            } else if (health != null) {
                app.getString(R.string.widget_health, health.healthPercent.toInt().coerceIn(0, 100))
            } else {
                "Vida: —"
            }

            val lastText = if (!isPremium) {
                app.getString(R.string.stats_premium_required)
            } else {
                describeLast(last, app)
            }

            // Gráfico de línea del día: historial de nivel (carga/descarga) para rellenar el widget
            val chartBitmap = generateDailyChartBitmap(
                context = app,
                todaySessions = todaySessions,
                inProgress = inProgress,
                snap = snap
            )

            // Tap abre la app
            val launchIntent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                android.app.PendingIntent.getActivity(
                    app, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }

            val views = RemoteViews(app.packageName, R.layout.widget_battery).apply {
                setTextViewText(
                    R.id.widget_level,
                    app.getString(R.string.widget_level_format, snap.levelPercent)
                )
                setTextViewText(
                    R.id.widget_status,
                    if (snap.isCharging) app.getString(R.string.widget_charging)
                    else app.getString(R.string.widget_idle)
                )
                setTextViewText(R.id.widget_last, lastText)
                setTextViewText(R.id.widget_time_today, todayText)
                setTextViewText(R.id.widget_health, healthText)
                setInt(
                    R.id.widget_bar,
                    "setBackgroundColor",
                    if (snap.isCharging) 0xFF2BD97C.toInt() else 0xFF8B938A.toInt()
                )
                if (chartBitmap != null) {
                    setImageViewBitmap(R.id.widget_chart, chartBitmap)
                }
                if (launchIntent != null) {
                    setOnClickPendingIntent(R.id.widget_root, launchIntent)
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }

    private fun describeLast(last: ChargingSession?, context: Context): String {
        if (last == null) return context.getString(R.string.widget_no_sessions)
        val gained = last.gainedPercent ?: 0
        val duration = last.durationSeconds?.formatShortDuration() ?: ""
        return context.getString(R.string.widget_last_session, gained, duration)
    }

    private fun generateDailyChartBitmap(
        context: Context,
        todaySessions: List<ChargingSession>,
        inProgress: ChargingSession?,
        snap: BatterySnapshot
    ): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val width = (360 * density).toInt().coerceAtLeast(240)
            val height = (96 * density).toInt().coerceAtLeast(48)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.TRANSPARENT)

            val padding = 8f * density
            val chartW = width - padding * 2
            val chartH = height - padding * 2
            if (chartW <= 0 || chartH <= 0) return bmp

            // Grid suave
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(38, 139, 147, 138)
                strokeWidth = 1f * density
                style = Paint.Style.STROKE
            }
            for (f in listOf(0f, 0.5f, 1f)) {
                val y = padding + chartH * f
                canvas.drawLine(padding, y, padding + chartW, y, gridPaint)
            }
            for (f in listOf(0f, 0.33f, 0.66f, 1f)) {
                val x = padding + chartW * f
                canvas.drawLine(x, padding, x, padding + chartH, gridPaint)
            }

            val startDay = startOfTodayMillis()
            val now = System.currentTimeMillis()
            val span = (24L * 60 * 60 * 1000).toFloat()

            val points = mutableListOf<Pair<Float, Float>>()
            // Sesiones de hoy (completadas + en curso incluida en todaySessions si empezó hoy)
            (todaySessions.sortedBy { it.startTimeMillis } + listOfNotNull(inProgress))
                .distinctBy { it.id }
                .sortedBy { it.startTimeMillis }
                .forEach { s ->
                    val x1 = ((s.startTimeMillis - startDay).toFloat() / span).coerceIn(0f, 1f)
                    points += x1 to (s.startLevelPercent.coerceIn(0, 100) / 100f)
                    val endTime = s.endTimeMillis
                    val endLevel = s.endLevelPercent
                    if (endTime != null && endLevel != null) {
                        val x2 = ((endTime - startDay).toFloat() / span).coerceIn(0f, 1f)
                        points += x2 to (endLevel.coerceIn(0, 100) / 100f)
                    }
                }
            // Punto vivo actual si hay sesión en curso hoy
            if (inProgress != null && inProgress.startTimeMillis >= startDay) {
                val x = ((now - startDay).toFloat() / span).coerceIn(0f, 1f)
                points += x to (snap.levelPercent.coerceIn(0, 100) / 100f)
            }

            if (points.size < 2) {
                // Sin datos suficientes: deja solo la grilla
                return bmp
            }
            points.sortBy { it.first }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(42, 43, 217, 124)
                style = Paint.Style.FILL
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2BD97C")
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2BD97C")
                style = Paint.Style.FILL
            }

            val path = Path()
            val fillPath = Path()
            points.forEachIndexed { i, (xN, yN) ->
                val x = padding + chartW * xN
                val y = padding + chartH * (1f - yN.coerceIn(0f, 1f))
                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, padding + chartH)
                    fillPath.lineTo(x, y)
                } else {
                    // Curva suave: usa lineTo (suficiente para widget pequeño)
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            // Cerrar relleno por abajo
            val lastX = padding + chartW * points.last().first
            fillPath.lineTo(lastX, padding + chartH)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, linePaint)
            // Puntitos discretos en cambios de nivel
            points.forEach { (xN, yN) ->
                val x = padding + chartW * xN
                val y = padding + chartH * (1f - yN.coerceIn(0f, 1f))
                canvas.drawCircle(x, y, 2.2f * density, dotPaint)
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}
