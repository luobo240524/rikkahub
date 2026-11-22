package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」小园日记:花园动作流水(浇水/播种/除虫/除草/换新种),独立于宠物日记。
 * 拍板定案=新独立表(2026-11-22),不并入 world_pet_events。
 * 去重口径同宠物日记:同一动作连续点只记一条,由仓库层比对最新一条实现。
 */
@Entity(
    tableName = "garden_log",
    indices = [
        Index(value = ["assistant_id"]),
    ]
)
data class GardenLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("actor")
    val actor: String, // user | assistant
    @ColumnInfo("action")
    val action: String, // water | sow | weeding | pest | replant
    @ColumnInfo("plant_id")
    val plantId: Long? = null, // 播种前为 null
    @ColumnInfo("detail")
    val detail: String = "", // 如「给 2 号盆的星芒菊浇水」
    @ColumnInfo("created_at")
    val createdAt: Long,
)
