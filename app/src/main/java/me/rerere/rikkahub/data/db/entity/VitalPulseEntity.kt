package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 生命体征事件日志：一条 = 一次情绪脉冲(某事件对状态的冲击)。
 * 供生命体征页潮汐带浮标与 24h 曲线重放；只保留最近 7 天(插入时顺手清理)。
 */
@Entity(
    tableName = "vital_pulses",
    indices = [
        Index(value = ["subject", "created_at"], unique = false),
    ]
)
data class VitalPulseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("subject")
    val subject: String, // assistantId
    @ColumnInfo("kind")
    val kind: String, // msg_in / msg_out / checkin / moment_like / moment_comment / secret / conflict...
    @ColumnInfo("valence_delta")
    val valenceDelta: Float,
    @ColumnInfo("arousal_delta")
    val arousalDelta: Float,
    @ColumnInfo("label")
    val label: String, // 潮汐带浮标显示的一行字(如"被夸了")
    @ColumnInfo("created_at")
    val createdAt: Long,
)
