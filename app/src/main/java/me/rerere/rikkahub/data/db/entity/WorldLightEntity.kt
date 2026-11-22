package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」留灯:灯下留言条,双向可留(用户/TA)。
 * 亮灯=out_at 为 null(暖光晕+未读);点开即熄(写 out_at),不强制回句。
 * 亮灯永留,熄灯 30 天后由仓库层惰性清理;碎碎念(词云)不受此影响。
 */
@Entity(
    tableName = "world_lights",
    indices = [
        Index(value = ["assistant_id"]),
    ]
)
data class WorldLightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("author")
    val author: String, // user | assistant
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("lit_at")
    val litAt: Long,
    @ColumnInfo("out_at")
    val outAt: Long? = null, // null=亮着;非空=已点开已读
)
