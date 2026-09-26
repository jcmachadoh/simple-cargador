package cu.jcmachadoh.simplecargador.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cu.jcmachadoh.simplecargador.data.local.model.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert
    suspend fun insert(entity: ChargingSessionEntity): Long

    @Update
    suspend fun update(entity: ChargingSessionEntity)

    /** RNF-08: la sesión abierta se localiza al instante para cerrarla ante cualquier evento. */
    @Query("SELECT * FROM charging_sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getInProgress(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY start_time DESC")
    fun observeAll(): Flow<List<ChargingSessionEntity>>

    @Query(
        "SELECT * FROM charging_sessions WHERE end_time IS NOT NULL " +
            "ORDER BY start_time DESC LIMIT :limit"
    )
    fun observeCompletedLast(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    fun observeInProgress(): Flow<ChargingSessionEntity?>

    @Query(
        "SELECT * FROM charging_sessions WHERE end_time IS NOT NULL " +
            "AND start_time BETWEEN :from AND :to ORDER BY start_time ASC"
    )
    fun observeCompletedInRange(from: Long, to: Long): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE start_time BETWEEN :from AND :to ORDER BY start_time ASC")
    suspend fun getByRange(from: Long, to: Long): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE end_time IS NOT NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getLastCompleted(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY start_time ASC")
    suspend fun getAllOnce(): List<ChargingSessionEntity>

    @Query("DELETE FROM charging_sessions WHERE start_time < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
