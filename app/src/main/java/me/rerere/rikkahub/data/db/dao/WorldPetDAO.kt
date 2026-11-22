package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldPetEntity

@Dao
interface WorldPetDAO {
    @Insert
    suspend fun insert(pet: WorldPetEntity): Long

    @Update
    suspend fun update(pet: WorldPetEntity)

    @Delete
    suspend fun delete(pet: WorldPetEntity)

    @Query("SELECT * FROM world_pet_state WHERE assistant_id = :assistantId ORDER BY created_at ASC")
    fun flowByAssistant(assistantId: String): Flow<List<WorldPetEntity>>

    @Query("SELECT * FROM world_pet_state WHERE assistant_id = :assistantId ORDER BY created_at ASC")
    suspend fun getByAssistant(assistantId: String): List<WorldPetEntity>

    @Query("SELECT * FROM world_pet_state WHERE id = :id AND assistant_id = :assistantId")
    suspend fun getById(assistantId: String, id: Long): WorldPetEntity?

    @Query("SELECT COUNT(*) FROM world_pet_state WHERE assistant_id = :assistantId")
    suspend fun count(assistantId: String): Int
}
