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

    /** 助手自己发的心里话(secrets_visibility 列表模式用) */
    @Query("SELECT * FROM secrets WHERE author = :author ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentByAuthor(author: String, limit: Int): List<SecretEntity>

    /** 只改助手自己条目的可见性;返回受影响行数(0=id 不存在或不属于助手) */
    @Query("UPDATE secrets SET visible = :visible WHERE id = :id AND author = :author")
    suspend fun updateVisibilityForAuthor(id: Long, visible: Int, author: String): Int

    /** 编辑内容+可见性(心里话编辑/可见转换) */
    @Query("UPDATE secrets SET content = :content, visible = :visible WHERE id = :id")
    suspend fun updateRow(id: Long, content: String, visible: Int)

    @Query("SELECT * FROM secrets WHERE id = :id")
    suspend fun getById(id: Long): SecretEntity?

    @Query("DELETE FROM secrets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
