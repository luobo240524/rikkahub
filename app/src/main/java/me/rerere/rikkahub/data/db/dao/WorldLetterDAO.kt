package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldLetterEntity

@Dao
interface WorldLetterDAO {
    @Insert
    suspend fun insert(letter: WorldLetterEntity): Long

    @Update
    suspend fun update(letter: WorldLetterEntity)

    @Query("SELECT * FROM world_letters WHERE assistant_id = :assistantId ORDER BY COALESCE(scheduled_at, created_at) DESC, created_at DESC")
    fun flowByAssistant(assistantId: String): Flow<List<WorldLetterEntity>>

    @Query("SELECT * FROM world_letters WHERE assistant_id = :assistantId ORDER BY COALESCE(scheduled_at, created_at) DESC, created_at DESC")
    suspend fun getByAssistant(assistantId: String): List<WorldLetterEntity>

    @Query("SELECT * FROM world_letters WHERE id = :id AND assistant_id = :assistantId LIMIT 1")
    suspend fun getById(assistantId: String, id: Long): WorldLetterEntity?

    @Query("SELECT * FROM world_letters WHERE scheduled_at IS NOT NULL AND scheduled_at <= :now AND notified_at IS NULL ORDER BY scheduled_at ASC")
    suspend fun getDueUnnotified(now: Long): List<WorldLetterEntity>

    @Query("UPDATE world_letters SET notified_at = :now WHERE id = :id AND notified_at IS NULL")
    suspend fun markNotified(id: Long, now: Long): Int

    @Query("UPDATE world_letters SET opened_at = :now WHERE id = :id AND assistant_id = :assistantId AND opened_at IS NULL")
    suspend fun markOpened(assistantId: String, id: Long, now: Long): Int
}
