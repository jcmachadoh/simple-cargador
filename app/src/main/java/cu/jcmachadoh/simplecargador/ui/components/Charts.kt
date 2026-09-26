package cu.jcmachadoh.simplecargador.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession

/**
 * RF-04: barras HORIZONTALES de las últimas 10 cargas.
 * Eje Y = sesión (de más antigua arriba a más reciente abajo), eje X = nivel 0..100%.
 * Cada barra va del nivel inicial al final: se ve de un vistazo cuánto subió.
 */
@Composable
fun LastSessionsBarChart(
    sessions: List<ChargingSession>,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.Gray.copy(alpha = 0.15f),
    barColor: Color = Color(0xFF2BD97C),
    axisColor: Color = Color.Gray.copy(alpha = 0.4f)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((sessions.size.coerceAtLeast(1) * 26).dp)
    ) {
        if (sessions.isEmpty()) return@Canvas
        val rowHeight = size.height / sessions.size
        val labelSpace = 34f
        val chartWidth = size.width - labelSpace

        // Líneas guía verticales 0/25/50/75/100
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            val x = labelSpace + chartWidth * pct / 100f
            drawLine(
                color = axisColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
        }

        val radius = CornerRadius(rowHeight / 3)
        sessions.forEachIndexed { index, session ->
            val centerY = rowHeight * index + rowHeight / 2
            val barHeight = rowHeight * 0.55f
            val start = (session.startLevelPercent.coerceIn(0, 100)) / 100f
            val end = ((session.endLevelPercent ?: session.startLevelPercent).coerceIn(0, 100)) / 100f
            val left = labelSpace + chartWidth * minOf(start, end)
            val width = (kotlin.math.abs(end - start) * chartWidth).coerceAtLeast(6f)

            // pista completa
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(labelSpace, centerY - barHeight / 2),
                size = Size(chartWidth, barHeight),
                cornerRadius = radius
            )
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, centerY - barHeight / 2),
                size = Size(width, barHeight),
                cornerRadius = radius
            )
        }
    }
}

/** Puntos (x normalizado 0..1, y 0..100) para línea/dispersión. */
@Composable
fun LineChart(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF5BA8F5),
    dotColor: Color = Color(0xFF5BA8F5),
    gridColor: Color = Color.Gray.copy(alpha = 0.18f),
    drawDots: Boolean = true,
    fillUnderLine: Boolean = false
) {
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.size < 2) return@Canvas
        val padding = 16f
        val w = size.width - padding * 2
        val h = size.height - padding * 2

        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(gridColor, Offset(padding, padding + h * f), Offset(padding + w, padding + h * f), 2f)
        }

        val mapped = points.map { (xNorm, y) ->
            Offset(padding + w * xNorm.coerceIn(0f, 1f), padding + h * (1f - y.coerceIn(0f, 100f) / 100f))
        }
        val path = Path().apply {
            moveTo(mapped.first().x, mapped.first().y)
            for (i in 1 until mapped.size) lineTo(mapped[i].x, mapped[i].y)
        }
        if (fillUnderLine) {
            val fill = Path().apply {
                addPath(path)
                lineTo(mapped.last().x, padding + h)
                lineTo(mapped.first().x, padding + h)
                close()
            }
            drawPath(fill, lineColor.copy(alpha = 0.15f))
        }
        drawPath(path, lineColor, style = Stroke(width = 6f))
        if (drawDots) {
            mapped.forEach { drawCircle(dotColor, radius = 7f, center = it) }
        }
    }
}

/** RF-07: dispersión duración (min) vs % ganado por sesión. */
@Composable
fun ScatterChart(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    dotColor: Color = Color(0xFFFFC24A),
    gridColor: Color = Color.Gray.copy(alpha = 0.18f)
) {
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val padding = 20f
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        if (points.isEmpty()) return@Canvas

        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(gridColor, Offset(padding, padding + h * f), Offset(padding + w, padding + h * f), 2f)
        }
        val maxX = points.maxOf { it.first }.coerceAtLeast(1f)
        points.forEach { (xMinutes, yPercent) ->
            val cx = padding + w * (xMinutes / maxX)
            val cy = padding + h * (1f - yPercent.coerceIn(0f, 100f) / 100f)
            drawCircle(dotColor.copy(alpha = 0.85f), radius = 9f, center = Offset(cx, cy))
        }
    }
}
