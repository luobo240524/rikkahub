package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心里话/小秘密:双方都能写,可设置对对方是否可见。
 * visible=0 时对方侧(工具/界面)只显示"对方设置为不可见",不吐内容。
 * author 区分作者(user/assistant):各自主写的内容对自己始终可见。
 */
@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("visible")
    val visible: Int = 1, // 1 对方可见 / 0 不可见
    @ColumnInfo("author")
    val author: String = "user", // user / assistant
    @ColumnInfo("conversation_id")
    val conversationId: String?,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
