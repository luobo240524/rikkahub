package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 社交互动：点赞/评论,目标是朋友圈帖子或心情打卡。
 * actor='assistant' 且 is_read=0 的行构成发现页红点;用户打开对应页面即清零。
 */
@Entity(
    tableName = "social_interactions",
    indices = [
        Index(value = ["target_type", "target_id"]),
    ]
)
data class SocialInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("target_type")
    val targetType: String, // moment / checkin
    @ColumnInfo("target_id")
    val targetId: Long,
    @ColumnInfo("actor")
    val actor: String, // user / assistant
    @ColumnInfo("type")
    val type: String, // like / comment
    @ColumnInfo("content")
    val content: String?, // 评论内容,点赞为 null
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("is_read")
    val isRead: Int = 1,
)
