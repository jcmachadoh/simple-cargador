package cu.jcmachadoh.simplecargador.data.local.repository

import cu.jcmachadoh.simplecargador.data.local.database.ChargingSessionDao
import cu.jcmachadoh.simplecargador.data.local.model.toDomain
import cu.jcmachadoh.simplecargador.data.local.model.toEntity
import cu.jcmachadoh.simplecargador.domain.model.ChargingSession
import cu.jcmachadoh.simplecargador.domain.repository.ChargingSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChargingSessionRepositoryImpl(
    private val dao: ChargingSessionDao
) : ChargingSessionRepository {

    override suspend fun getInProgress(): ChargingSession? =
        withContext(Dispatchers.IO) { dao.getInProgress()?.toDomain() }

    /** RNF-08: escritura inmediata en IO; nunca bloquea la UI. */
    override suspend fun startSession(session: ChargingSession): Long =
        withContext(Dispatchers.IO) { dao.insert(session.toEntity()) }

    override suspend fun updateSession(session: ChargingSession) =
        withContext(Dispatchers.IO) { dao.update(session.toEntity()) }

    override suspend fun getById(id: Long): ChargingSession? =
        withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }

    override fun observeAll(): Flow<List<ChargingSession>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCompletedLast(limit: Int): Flow<List<ChargingSession>> =
        dao.observeCompletedLast(limit).map { list -> list.map { it.toDomain() } }

    override fun observeInProgress(): Flow<ChargingSession?> =
        dao.observeInProgress().map { it?.toDomain() }

    override fun observeCompletedInRange(fromMillis: Long, toMillis: Long): Flow<List<ChargingSession>> =
        dao.observeCompletedInRange(fromMillis, toMillis).map { list -> list.map { it.toDomain() } }

    override suspend fun getByRange(fromMillis: Long, toMillis: Long): List<ChargingSession> =
        withContext(Dispatchers.IO) { dao.getByRange(fromMillis, toMillis).map { it.toDomain() } }

    override suspend fun getLastCompletedOnce(): ChargingSession? =
        withContext(Dispatchers.IO) { dao.getLastCompleted()?.toDomain() }

    override suspend fun getAllOnce(): List<ChargingSession> =
        withContext(Dispatchers.IO) { dao.getAllOnce().map { it.toDomain() } }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int =
        withContext(Dispatchers.IO) { dao.deleteOlderThan(cutoffMillis) }
}
