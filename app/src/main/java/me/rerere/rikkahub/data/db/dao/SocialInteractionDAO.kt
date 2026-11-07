package me.rerere.rikkahub.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity

data class UnreadCount(
    @ColumnInfo("target_type")
    val targetType: String,
    val cnt: Int,
)

@Dao
interface SocialInteractionDAO {
    @Insert
    suspend fun insert(interaction: SocialInteractionEntity): Long

    @Query("SELECT * FROM social_interactions WHERE target_type = :targetType AND target_id = :targetId ORDER BY created_at ASC")
    fun flowForTarget(targetType: String, targetId: Long): Flow<List<SocialInteractionEntity>>

    @Query("SELECT * FROM social_interactions WHERE target_type = :targetType AND target_id = :targetId ORDER BY created_at ASC")
    suspend fun listForTarget(targetType: String, targetId: Long): List<SocialInteractionEntity>

    /** 取消点赞:同目标同主体同类型的 like 行删除 */
    @Query("DELETE FROM social_interactions WHERE target_type = :targetType AND target_id = :targetId AND actor = :actor AND type = 'like'")
    suspend fun deleteLike(targetType: String, targetId: Long, actor: String)

    /** 查重:该主体是否已点过赞(moments_interact/mood_interact 防重复) */
    @Query("SELECT COUNT(*) FROM social_interactions WHERE target_type = :targetType AND target_id = :targetId AND actor = :actor AND type = 'like'")
    suspend fun countLike(targetType: String, targetId: Long, actor: String): Int

    /** 红点数据:助手产生的未读互动按目标类型计数 */
    @Query("SELECT target_type, COUNT(*) as cnt FROM social_interactions WHERE actor = 'assistant' AND is_read = 0 GROUP BY target_type")
    fun flowUnreadCounts(): Flow<List<UnreadCount>>

    /** 打开对应页面即清零该类红点 */
    @Query("UPDATE social_interactions SET is_read = 1 WHERE actor = 'assistant' AND is_read = 0 AND target_type = :targetType")
    suspend fun markReadByType(targetType: String)

    /** 全局最近互动流(feed 页一次收集,UI 按 target 分组,避免每卡片一条 Flow) */
    @Query("SELECT * FROM social_interactions ORDER BY created_at ASC LIMIT :limit")
    fun flowRecent(limit: Int): Flow<List<SocialInteractionEntity>>

    /** 删除内容时清理其互动 */
    @Query("DELETE FROM social_interactions WHERE target_type = :targetType AND target_id = :targetId")
    suspend fun deleteByTarget(targetType: String, targetId: Long)

    /** 助手最近读到的互动(moments_interact 前置感知用,可留作扩展) */
    @Query("SELECT * FROM social_interactions ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SocialInteractionEntity>
}
