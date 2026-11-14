package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 纪念日:公历月/日 + 是否每年重复 + 备注。
 * 触达三路:当天聊天注入提示词 / 早上问候唤醒模板 / 时间轴当日展示。
 * 录入双通道:用户手动管理页 + TA 的 anniversary_write 工具(本地工具开关门控)。
 */
@Entity(
    tableName = "anniversaries",
    indices = [
        Index(value = ["month", "day"]),
    ]
)
data class AnniversaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("name")
    val name: String, // 名称,如「你的生日」「相识纪念日」
    @ColumnInfo("month")
    val month: Int, // 公历月 1-12
    @ColumnInfo("day")
    val day: Int, // 公历日 1-31
    @ColumnInfo("target_year")
    val targetYear: Int? = null, // 单次日期的目标年份(每年重复的为 null)
    @ColumnInfo("repeat_yearly")
    val repeatYearly: Boolean = true, // true=每年重复,false=单次(如相识第100天,须带 target_year)
    @ColumnInfo("note")
    val note: String = "", // 备注(TA 的准备计划/用户的提醒)
    @ColumnInfo("created_at")
    val createdAt: Long,
)
