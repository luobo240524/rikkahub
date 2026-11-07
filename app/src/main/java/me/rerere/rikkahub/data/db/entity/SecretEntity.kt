package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心里话/小秘密:用户写的私密内容,可设置对助手是否可见。
 * visible=0 时 secrets_read 工具只回"对方设置为不可见",不吐内容。
 */
@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("visible")
    val visible: Int = 1, // 1 对方可见 / 0 不可见
    @ColumnInfo("conversation_id")
    val conversationId: String?,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
