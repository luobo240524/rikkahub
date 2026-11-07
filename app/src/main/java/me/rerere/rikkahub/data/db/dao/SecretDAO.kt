package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.SecretEntity

@Dao
interface SecretDAO {
    @Insert
    suspend fun insert(secret: SecretEntity): Long

    @Query("SELECT * FROM secrets ORDER BY created_at DESC")
    fun flowAll(): Flow<List<SecretEntity>>

    /** 最近 N 条(secrets_read 工具用) */
    @Query("SELECT * FROM secrets ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SecretEntity>

    @Query("DELETE FROM secrets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
