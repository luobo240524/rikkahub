package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldThoughtEntity

@Dao
interface WorldThoughtDAO {
    @Insert
    suspend fun insert(thought: WorldThoughtEntity): Long

    @Query("DELETE FROM world_thoughts WHERE id = :id AND assistant_id = :assistantId")
    suspend fun deleteById(assistantId: String, id: Long)

    @Query("SELECT * FROM world_thoughts WHERE assistant_id = :assistantId ORDER BY created_at DESC")
    fun flowByAssistant(assistantId: String): Flow<List<WorldThoughtEntity>>

    @Query("SELECT * FROM world_thoughts WHERE assistant_id = :assistantId ORDER BY created_at DESC")
    suspend fun getByAssistant(assistantId: String): List<WorldThoughtEntity>

    /** 抽取信箱:未抽过的随机一张(全部抽完=空结果,由仓库层重置重抽) */
    @Query("SELECT * FROM world_thoughts WHERE assistant_id = :assistantId AND drawn_at IS NULL ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomUndrawn(assistantId: String): WorldThoughtEntity?

    @Query("UPDATE world_thoughts SET drawn_at = :drawnAt WHERE id = :id")
    suspend fun markDrawn(id: Long, drawnAt: Long)

    @Query("UPDATE world_thoughts SET drawn_at = NULL WHERE assistant_id = :assistantId")
    suspend fun resetDrawn(assistantId: String)

    @Query("SELECT COUNT(*) FROM world_thoughts WHERE assistant_id = :assistantId AND drawn_at IS NULL")
    suspend fun countUndrawn(assistantId: String): Int
}
