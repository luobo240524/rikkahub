package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldPlantEntity

@Dao
interface WorldPlantDAO {
    @Insert
    suspend fun insert(plant: WorldPlantEntity): Long

    @Update
    suspend fun update(plant: WorldPlantEntity)

    @Delete
    suspend fun delete(plant: WorldPlantEntity)

    @Query("SELECT * FROM world_plants WHERE assistant_id = :assistantId ORDER BY slot ASC")
    fun flowByAssistant(assistantId: String): Flow<List<WorldPlantEntity>>

    @Query("SELECT * FROM world_plants WHERE assistant_id = :assistantId ORDER BY slot ASC")
    suspend fun getByAssistant(assistantId: String): List<WorldPlantEntity>

    @Query("SELECT * FROM world_plants WHERE id = :id AND assistant_id = :assistantId")
    suspend fun getById(assistantId: String, id: Long): WorldPlantEntity?

    @Query("SELECT * FROM world_plants WHERE assistant_id = :assistantId AND slot = :slot LIMIT 1")
    suspend fun getBySlot(assistantId: String, slot: Int): WorldPlantEntity?

    @Query("SELECT COUNT(*) FROM world_plants WHERE assistant_id = :assistantId")
    suspend fun count(assistantId: String): Int
}
