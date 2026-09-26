package cu.jcmachadoh.simplecargador.utils.extensions

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatea segundos como duración legible: "2 h 05 min", "48 min", "35 s". */
fun Long?.formatShortDuration(): String {
    if (this == null || this <= 0) return "—"
    val totalMinutes = this / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = this % 60
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%d h %02d min", hours, minutes)
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}

fun Long?.formatLongDuration(): String {
    if (this == null || this <= 0) return "—"
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
}

private fun sdf(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.getDefault())

fun Long.formatDateTime(): String = sdf("dd MMM · HH:mm").format(Date(this))

fun Long.formatFullDate(): String = sdf("dd 'de' MMMM yyyy · HH:mm").format(Date(this))

/** Milisegundos del inicio del día actual (00:00). */
fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun Int.signedPercent(): String = if (this >= 0) "+$this%" else "$this%"
