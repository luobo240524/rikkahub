package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」宠物日记:喂食/陪玩/梳毛/孵蛋信号/破壳/领养等动作流水。
 * actor=user(用户点按)或 assistant(「让 TA 照顾」本地模拟记 TA,二期接唤醒轮)。
 * 日记去重(同一动作连续点只记一条)由仓库层比对最新一条同 actor+action 实现,信号照涨。
 */
@Entity(
    tableName = "world_pet_events",
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["pet_id"]),
    ]
)
data class WorldPetEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("pet_id")
    val petId: Long? = null, // null=全局事件(领养前/多宠通用)
    @ColumnInfo("actor")
    val actor: String, // user | assistant
    @ColumnInfo("action")
    val action: String, // feed | play | groom | hatch_signal | hatch | adopt | rename...
    @ColumnInfo("detail")
    val detail: String = "", // 渲染用补充文本(如「喂了小鱼干」)
    @ColumnInfo("created_at")
    val createdAt: Long,
)
