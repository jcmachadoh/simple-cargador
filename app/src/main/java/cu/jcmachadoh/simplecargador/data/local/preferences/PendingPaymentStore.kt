package cu.jcmachadoh.simplecargador.data.local.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistencia del transaction_uuid pendiente.
 * Vital: si el usuario cierra la app o el sistema la destruye mientras paga,
 * al volver (onResume / deep link) re-dispara la verificación (Paso 5).
 */
class PendingPaymentStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun savePending(transactionUuid: String, invoiceNumber: String) {
        prefs.edit()
            .putString(KEY_UUID, transactionUuid)
            .putString(KEY_INVOICE, invoiceNumber)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_STATUS, STATUS_PENDING)
            .apply()
    }

    fun getPendingUuid(): String? = prefs.getString(KEY_UUID, null)

    fun getPendingInvoice(): String? = prefs.getString(KEY_INVOICE, null)

    fun hasPending(): Boolean = getPendingUuid() != null

    fun clear() { prefs.edit().clear().apply() }

    fun markCompleted() {
        prefs.edit().putString(KEY_STATUS, STATUS_COMPLETED).apply()
    }

    fun status(): String? = prefs.getString(KEY_STATUS, null)

    companion object {
        private const val FILE_NAME = "enzona_pending"
        private const val KEY_UUID = "pending_transaction_uuid"
        private const val KEY_INVOICE = "pending_invoice_number"
        private const val KEY_TIMESTAMP = "pending_timestamp"
        private const val KEY_STATUS = "pending_status"
        const val STATUS_PENDING = "Pendiente"
        const val STATUS_COMPLETED = "Confirmada"
    }
}
