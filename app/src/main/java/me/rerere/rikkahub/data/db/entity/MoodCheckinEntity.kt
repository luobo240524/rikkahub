package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日心情打卡：每条记录 = 某天某主体(用户/助手)的一次打卡。
 * (date, subject) 唯一 → 同日同主体重复打卡覆盖(REPLACE)。
 */
@Entity(
    tableName = "mood_checkins",
    indices = [
        Index(value = ["date", "subject"], unique = true),
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
)
