package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「你的世界」每助手全局计数(vitals_state 同款 subject 主键 upsert 模式)。
 * granary=粮仓:每聊一轮 +1(ChatService.handleMessageComplete 钩子),喂食消耗;
 * 全助手每轮都涨,宠物页按 assistantId 读自己的粮仓。
 */
@Entity(tableName = "world_state")
data class WorldStateEntity(
    @PrimaryKey
    @ColumnInfo("subject")
    val subject: String, // assistantId
    @ColumnInfo("granary", defaultValue = "0")
    val granary: Int = 0,
    @ColumnInfo("wall_key", defaultValue = "")
    val wallKey: String = "", // 封面选中壁纸 key(WALLS key),DIY 挑选页落地前先记住封面选择
    @ColumnInfo("deco_key", defaultValue = "")
    val decoKey: String = "", // 花线款式 star/heart/moon(DIY 页提供切换)
    @ColumnInfo("last_notified_at", defaultValue = "0")
    val lastNotifiedAt: Long = 0, // 用户世界动作已注入告知助手的位点(该时刻之后的用户动作下一轮才报)
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
