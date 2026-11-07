package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity

@Dao
interface MomentsPostDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: MomentsPostEntity)

    @Query("SELECT * FROM moments_posts ORDER BY created_at DESC")
    fun flowAll(): Flow<List<MomentsPostEntity>>

    /** 最近 N 条(moments_read 工具用) */
    @Query("SELECT * FROM moments_posts ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MomentsPostEntity>

    @Query("SELECT * FROM moments_posts WHERE id = :id")
    suspend fun getById(id: Long): MomentsPostEntity?

    @Query("DELETE FROM moments_posts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
