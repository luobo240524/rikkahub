package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.GardenLogEntity

@Dao
interface GardenLogDAO {
    @Insert
    suspend fun insert(log: GardenLogEntity): Long

    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    fun flowRecent(assistantId: String, limit: Int = 50): Flow<List<GardenLogEntity>>

    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(assistantId: String, limit: Int = 50): List<GardenLogEntity>

    /** 小园日记去重用:最新一条 */
    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(assistantId: String): GardenLogEntity?
}
