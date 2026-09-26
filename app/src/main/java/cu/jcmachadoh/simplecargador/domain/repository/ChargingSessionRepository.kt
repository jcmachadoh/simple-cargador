package cu.jcmachadoh.simplecargador.domain.repository

import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import kotlinx.coroutines.flow.Flow

interface ChargingSessionRepository {

    suspend fun getInProgress(): ChargingSession?

    suspend fun startSession(session: ChargingSession): Long

    suspend fun updateSession(session: ChargingSession)

    suspend fun getById(id: Long): ChargingSession?

    fun observeAll(): Flow<List<ChargingSession>>

    fun observeCompletedLast(limit: Int): Flow<List<ChargingSession>>

    fun observeInProgress(): Flow<ChargingSession?>

    fun observeCompletedInRange(fromMillis: Long, toMillis: Long): Flow<List<ChargingSession>>

    suspend fun getByRange(fromMillis: Long, toMillis: Long): List<ChargingSession>

    suspend fun getLastCompletedOnce(): ChargingSession?

    suspend fun getAllOnce(): List<ChargingSession>

    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
