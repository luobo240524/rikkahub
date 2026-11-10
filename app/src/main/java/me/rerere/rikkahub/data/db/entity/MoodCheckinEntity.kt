package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日心情打卡：每条记录 = 某天某主体的一次打卡。
 * (date, subject) 普通索引(仅加速查询):一天内可多次打卡,各自独立成条不覆盖。
 */
@Entity(
    tableName = "mood_checkins",
    indices = [
        Index(value = ["date", "subject"], unique = false),
    ]
)
data class MoodCheckinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("date")
    val date: String, // yyyy-MM-dd
    @ColumnInfo("subject")
    val subject: String, // user / assistant
    @ColumnInfo("mood")
    val mood: String, // emoji
    @ColumnInfo("phrase")
    val phrase: String, // 一句话心情
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("scheduled_at")
    val scheduledAt: Long? = null, // 定时生效:到点前不在打卡页/时间轴显示(null=立即生效)
)
