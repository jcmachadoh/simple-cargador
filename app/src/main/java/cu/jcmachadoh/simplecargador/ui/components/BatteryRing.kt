package cu.jcmachadoh.simplecargador.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import android.provider.Settings
import cu.jcmachadoh.simplecargador.ui.theme.LocalStateColors
import cu.jcmachadoh.simplecargador.ui.theme.batt_green_bright
import cu.jcmachadoh.simplecargador.ui.theme.batt_green_deep
import cu.jcmachadoh.simplecargador.ui.theme.batt_orange_bright
import cu.jcmachadoh.simplecargador.ui.theme.batt_orange_deep
import cu.jcmachadoh.simplecargador.ui.theme.batt_red_bright
import cu.jcmachadoh.simplecargador.ui.theme.batt_red_deep
import kotlin.math.cos
import kotlin.math.sin

/** Curva de desaceleración confiable y decidida para el llenado. */
private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * RNF-01: anillo circular animado a 60 FPS con Canvas puro.
 * El color comunica el nivel (rojo < 25%, naranja 25–75%, verde 75–100%) con un
 * degradado profundo→vivo a lo largo del arco. Mientras carga, un cometa de luz
 * recorre el arco lleno simulando la corriente entrando, y un halo suave respira
 * para atraer la mirada. Sin carga, el anillo queda en calma (confiable y calmado).
 */
@Composable
fun BatteryRing(
    levelPercent: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    ringSize: Dp = Dp.Unspecified,
    strokeWidth: Dp = 16.dp,
    content: @Composable () -> Unit = {}
) {
    val trackColor = trackColor()
    val reduceMotion = rememberReduceMotion()
    val (deep, bright) = batteryLevelPair(levelPercent)

    // Tamaño responsivo: ~62% del ancho de pantalla, acotado para que quepa
    // en pantallas pequeñas y no sea desproporcionado en las grandes.
    val screenW = LocalConfiguration.current.screenWidthDp
    val effectiveSize = if (ringSize == Dp.Unspecified) {
        (screenW * 0.62f).dp.coerceIn(180.dp, 300.dp)
    } else ringSize

    val target = levelPercent.coerceIn(0, 100) / 100f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1100, easing = EaseOutQuint),
        label = "batteryRingProgress"
    )

    val transition = rememberInfiniteTransition(label = "ringCharge")
    // Cometa: recorre el arco lleno de principio a fin.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cometPhase"
    )
    // Halo: respiración suave para señalar que hay corriente.
    val halo by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Box(modifier = modifier.size(effectiveSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(effectiveSize)) {
            val stroke = strokeWidth.toPx()
            // Margen para el halo (≈1.7× trazo más allá del arco) y el cometa.
            val inset = stroke * 3.5f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = arcSize.width / 2
            val arcBrush = Brush.linearGradient(
                colors = listOf(deep, bright),
                start = Offset(0f, inset),
                end = Offset(0f, size.height - inset)
            )

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            if (progress > 0.001f) {
                if (isCharging && !reduceMotion) {
                    drawCircle(
                        color = bright.copy(alpha = halo),
                        radius = radius + stroke * 0.6f,
                        center = center,
                        style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round)
                    )
                }

                drawArc(
                    brush = arcBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Cometa de corriente: cola de segmentos brillantes que viaja
                // por el arco lleno, con un destello suave en la cabeza.
                if (isCharging && !reduceMotion && progress > 0.02f) {
                    val headRel = 360f * progress * phase
                    val tailCount = 14
                    val tailStep = 4.5f
                    for (i in 0 until tailCount) {
                        val rel = headRel - i * tailStep
                        if (rel < 0f) break
                        val t = i.toFloat() / tailCount
                        drawArc(
                            color = Color.White.copy(alpha = (1f - t) * 0.85f),
                            startAngle = -90f + rel,
                            sweepAngle = tailStep * 0.95f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(
                                width = stroke * (1f - t * 0.45f),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    val headRad = (-90f + headRel) * (Math.PI / 180f)
                    val headPos = Offset(
                        center.x + radius * cos(headRad).toFloat(),
                        center.y + radius * sin(headRad).toFloat()
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0f)
                            ),
                            center = headPos,
                            radius = stroke * 2f
                        ),
                        radius = stroke * 2f,
                        center = headPos
                    )
                }
            }
        }
        // Margen interno para que el contenido (el %) nunca quede pegado al arco.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(effectiveSize * 0.17f)
        ) {
            content()
        }
    }
}

/**
 * Par profundo→vivo según el nivel de batería: rojo < 25%, naranja 25–75%,
 * verde 75–100%. El degradado del arco va de [deep] a [bright].
 */
fun batteryLevelPair(level: Int): Pair<Color, Color> {
    val l = level.coerceIn(0, 100)
    return when {
        l < 25 -> batt_red_deep to batt_red_bright
        l < 75 -> batt_orange_deep to batt_orange_bright
        else -> batt_green_deep to batt_green_bright
    }
}

/**
 * Color del anillo según el nivel de batería. El tono se intensifica a medida
 * que el nivel sube dentro de cada franja.
 */
fun batteryLevelColor(level: Int): Color {
    val l = level.coerceIn(0, 100)
    val t = when {
        l < 25 -> l / 25f
        l < 75 -> ((l - 25) / 50f)
        else -> ((l - 75) / 25f)
    }.coerceIn(0f, 1f)
    val (deep, bright) = batteryLevelPair(level)
    return lerp(deep, bright, t)
}

@Composable
private fun trackColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

/**
 * Respeta "Reducir movimiento" del sistema: desactiva el cometa y el halo,
 * dejando solo el llenado puro del estado de la batería.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val view = LocalView.current
    return remember {
        try {
            Settings.Global.getInt(view.context.contentResolver, "reduce_motion", 0) == 1
        } catch (_: Throwable) { false }
    }
}

/** Variante compacta para indicadores pequeños (filas de historial). */
@Composable
fun MiniLevelRing(
    levelPercent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp
) {
    val states = LocalStateColors.current
    val ringColor = when {
        levelPercent <= 15 -> states.low
        levelPercent <= 35 -> states.mid
        else -> states.charging
    }
    val progress by animateFloatAsState(
        targetValue = levelPercent.coerceIn(0, 100) / 100f,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "miniRing"
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.toPx() - stroke, size.toPx() - stroke)
        drawArc(
            color = Color.Gray.copy(alpha = 0.18f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = ringColor,
            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
