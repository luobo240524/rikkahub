package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldLightEntity

@Dao
interface WorldLightDAO {
    @Insert
    suspend fun insert(light: WorldLightEntity): Long

    @Query("SELECT * FROM world_lights WHERE assistant_id = :assistantId ORDER BY lit_at DESC")
    fun flowByAssistant(assistantId: String): Flow<List<WorldLightEntity>>

    @Query("SELECT * FROM world_lights WHERE assistant_id = :assistantId ORDER BY lit_at DESC")
    suspend fun getByAssistant(assistantId: String): List<WorldLightEntity>

    /** 点开即熄(写 out_at 即视为已读) */
    @Query("UPDATE world_lights SET out_at = :outAt WHERE id = :id AND assistant_id = :assistantId")
    suspend fun extinguish(assistantId: String, id: Long, outAt: Long)

    @Query("SELECT COUNT(*) FROM world_lights WHERE assistant_id = :assistantId AND out_at IS NULL")
    suspend fun countLit(assistantId: String): Int

    /** 按 author 计亮灯数(首页徽标只数 TA 留的未读) */
    @Query("SELECT COUNT(*) FROM world_lights WHERE assistant_id = :assistantId AND out_at IS NULL AND author = :author")
    suspend fun countLitByAuthor(assistantId: String, author: String): Int

    /** 熄灯 30 天惰性清理(亮灯永留) */
    @Query("DELETE FROM world_lights WHERE assistant_id = :assistantId AND out_at IS NOT NULL AND out_at < :threshold")
    suspend fun cleanupExtinguished(assistantId: String, threshold: Long)

    /** 用户留的灯(时间位点后,升序)——注入告知助手用 */
    @Query("SELECT * FROM world_lights WHERE assistant_id = :assistantId AND author = 'user' AND lit_at >= :since ORDER BY lit_at ASC")
    suspend fun getUserSince(assistantId: String, since: Long): List<WorldLightEntity>
}
