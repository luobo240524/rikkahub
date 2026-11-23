package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「你的世界」宠物/蛋:一行一只(蛋也是一行,species=egg)。
 * 神秘蛋:hatch_signals 0-7 攒信号(暖蛋/摇蛋/盖毯各+1),满 7 破壳云绒猫/狗;
 * 稀有金蛋 is_golden=true 需 9 信号,破壳出金色调(滤镜由页面渲染)。
 * 破壳=同行列改 species/name/born_at/gender。多宠上限 6 只含蛋;档案卡年龄由 born_at 派生,
 * 状态(饿/困/开心)由 last_fed_at/last_play_at 派生,均不落列。
 */
@Entity(
    tableName = "world_pet_state",
    indices = [
        Index(value = ["assistant_id"]),
    ]
)
data class WorldPetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("species")
    val species: String, // egg | cove-cat | cove-dog | moon | forest | stardust | tide
    @ColumnInfo("name")
    val name: String, // 默认名仓库层常量(小蛋蛋/奶油/泡芙),档案卡 ✎ 可改
    @ColumnInfo("gender")
    val gender: String = "girl", // boy | girl,领养/破壳时随机
    @ColumnInfo("is_golden")
    val isGolden: Boolean = false,
    @ColumnInfo("hatch_signals")
    val hatchSignals: Int = 0, // 蛋专用:普通满 7 / 金蛋满 9 破壳
    @ColumnInfo("born_at")
    val bornAt: Long? = null, // 蛋=null;破壳/领养时刻
    @ColumnInfo("last_fed_at")
    val lastFedAt: Long = 0,
    @ColumnInfo("last_play_at")
    val lastPlayAt: Long = 0,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
