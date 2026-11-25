package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "world_letters",
    indices = [Index(value = ["assistant_id", "scheduled_at"])],
)
data class WorldLetterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("author")
    val author: String,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("scheduled_at")
    val scheduledAt: Long? = null,
    @ColumnInfo("notified_at")
    val notifiedAt: Long? = null,
    @ColumnInfo("opened_at")
    val openedAt: Long? = null,
)
