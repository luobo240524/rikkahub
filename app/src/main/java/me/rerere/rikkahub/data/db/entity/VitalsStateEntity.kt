package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 助手生命体征状态：每个助手一行(upsert)，存当前内在状态快照。
 * valence(效价 -1难受~+1开心) / arousal(唤醒 0~1) 决定心跳快慢；
 * safety/confidence/moodBase 是慢变底色，几天才变一点。
 * 纯本地公式计算，不调模型，不影响助手回复行为(仅监控展示)。
 */
@Entity(tableName = "vitals_state")
data class VitalsStateEntity(
    @PrimaryKey
    @ColumnInfo("subject")
    val subject: String, // assistantId
    @ColumnInfo("valence", defaultValue = "0")
    val valence: Float = 0f,
    @ColumnInfo("arousal", defaultValue = "0")
    val arousal: Float = 0f,
    @ColumnInfo("safety", defaultValue = "0.7")
    val safety: Float = 0.7f,
    @ColumnInfo("confidence", defaultValue = "0.5")
    val confidence: Float = 0.5f,
    @ColumnInfo("mood_base", defaultValue = "0")
    val moodBase: Float = 0f,
    @ColumnInfo("pulse", defaultValue = "62")
    val pulse: Int = 62, // 最近一次算出的心跳快照
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
