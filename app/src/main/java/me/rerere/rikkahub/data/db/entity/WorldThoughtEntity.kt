package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」碎碎念:词云泡泡池 + 抽取信箱来源。
 * 双向写入:用户在页面手写,TA 的写入留二阶段(author 预留 assistant)。
 * mood 为 11 词表 key(happy/miss/calm/sad/expect/shy/aggrieved/lazy/content/excited/emo),
 * 词云泡泡底色与 emoji 由 key 映射,不存 emoji 本身。
 * drawn_at 为抽取信箱抽出时间(null=未抽过);全部抽完由仓库层重置重抽。
 */
@Entity(
    tableName = "world_thoughts",
    indices = [
        Index(value = ["assistant_id"]),
    ]
)
data class WorldThoughtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("author")
    val author: String, // user | assistant
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("mood")
    val mood: String,
    @ColumnInfo("drawn_at")
    val drawnAt: Long? = null,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
