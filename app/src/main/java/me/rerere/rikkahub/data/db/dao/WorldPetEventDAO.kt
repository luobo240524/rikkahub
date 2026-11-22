package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldPetEventEntity

@Dao
interface WorldPetEventDAO {
    @Insert
    suspend fun insert(event: WorldPetEventEntity): Long

    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    fun flowRecent(assistantId: String, limit: Int = 50): Flow<List<WorldPetEventEntity>>

    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(assistantId: String, limit: Int = 50): List<WorldPetEventEntity>

    /** 日记去重用:最新一条(仓库层比对 actor+action 是否连续重复) */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(assistantId: String): WorldPetEventEntity?
}
