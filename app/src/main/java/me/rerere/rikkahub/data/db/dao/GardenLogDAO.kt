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

    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(assistantId: String, limit: Int = 50, offset: Int = 0): List<GardenLogEntity>

    /** 小园日记去重用:最新一条 */
    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(assistantId: String): GardenLogEntity?

    /** 用户的园务(时间位点后,升序)——注入告知助手用 */
    @Query("SELECT * FROM garden_log WHERE assistant_id = :assistantId AND actor = 'user' AND created_at >= :since ORDER BY created_at ASC")
    suspend fun getUserSince(assistantId: String, since: Long): List<GardenLogEntity>
}
