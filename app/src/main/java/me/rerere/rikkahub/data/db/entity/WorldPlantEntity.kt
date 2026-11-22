package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」花园花盆:每助手 4 盆(slot 0-3),8 种花 species key。
 * decay-on-read(vitals 先例):thirst/虫/草在仓库层读取时按距 last_care_at 的时长推算并落回,
 * 浇水(浇最渴盆)/除虫/除草清零对应项并刷 last_care_at;stage(生长四段)由 growth 派生不落列。
 * 换新种=删行重播种;growth 由园务动作累计。
 */
@Entity(
    tableName = "world_plants",
    indices = [
        Index(value = ["assistant_id"]),
    ]
)
data class WorldPlantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("slot")
    val slot: Int, // 0-3 四盆
    @ColumnInfo("species")
    val species: String, // 8 种花 key,仓库层常量表映射显示名
    @ColumnInfo("growth")
    val growth: Int = 0, // 生长点数,四段阈值由仓库层常量派生 stage
    @ColumnInfo("waters")
    val waters: Int = 0, // 浇水次数(页面详情卡展示)
    @ColumnInfo("thirst")
    val thirst: Int = 0, // 0-100,越高越渴;浇水清零
    @ColumnInfo("has_pest")
    val hasPest: Boolean = false,
    @ColumnInfo("has_weed")
    val hasWeed: Boolean = false,
    @ColumnInfo("last_care_at")
    val lastCareAt: Long = 0,
    @ColumnInfo("planted_at")
    val plantedAt: Long,
)
