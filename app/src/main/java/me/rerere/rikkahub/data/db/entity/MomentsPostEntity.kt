package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 朋友圈帖子：一期纯文字 + emoji + 可选心情标签，用户/助手都可发。
 */
@Entity(tableName = "moments_posts")
data class MomentsPostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("author")
    val author: String, // user / assistant
    @ColumnInfo("conversation_id")
    val conversationId: String? = null, // 助手发帖时可关联来源会话
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("mood")
    val mood: String? = null, // 可选心情标签(emoji)
    @ColumnInfo("created_at")
    val createdAt: Long,
)
