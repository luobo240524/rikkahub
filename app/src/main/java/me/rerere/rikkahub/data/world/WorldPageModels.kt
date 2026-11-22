package me.rerere.rikkahub.data.world

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.GardenLogEntity
import me.rerere.rikkahub.data.db.entity.WorldLightEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEventEntity
import me.rerere.rikkahub.data.db.entity.WorldPlantEntity
import me.rerere.rikkahub.data.db.entity.WorldThoughtEntity

/**
 * 「你的世界」页面数据映射:DB 实体 → WebView 页面 JS 期望的形状。
 * 字段名与预览页定稿(thoughts-v3/lights-v1/garden-v2/pet-v4)的 JS 常量一致,页面渲染零改动。
 * 时间戳口径(七问拍板):今天/昨天/M月D日 HH:mm。
 */

// 心情 key → 泡泡底色(前五项取自定稿 demo,后六项同族粉彩由 AI 定,可调)
val MOOD_COLORS = mapOf(
    "happy" to "#FFE9C7",
    "miss" to "#E3E9FB",
    "calm" to "#E3F2E7",
    "sad" to "#DFE8F5",
    "expect" to "#FDE9E4",
    "shy" to "#FAE1EC",
    "aggrieved" to "#E2EAF6",
    "lazy" to "#F3ECDD",
    "content" to "#E7F0E2",
    "excited" to "#FFF2D9",
    "emo" to "#E6E3F2",
)

@Serializable
data class PageThought(
    val id: Long,
    val mood: String,
    val e: String,
    val c: String,
    val text: String,
    val time: String,
    val author: String,
)

@Serializable
data class PageLight(
    val id: Long,
    val who: String,
    val me: Boolean,
    val text: String,
    val time: String,
)

@Serializable
data class PageLog(val a: String, val t: String, val time: String)

@Serializable
data class PagePlant(
    val id: Long,
    val pot: Int,
    val sp: String?,
    val stage: Int,
    val thirsty: Boolean,
    val bug: Boolean,
    val planted: String,
    val days: Int,
    val waters: Int,
    val last: String,
)

@Serializable
data class PagePet(
    val id: Long,
    val sp: String,
    val name: String,
    val gender: String,
    val months: Int,
    val joined: String,
    val days: Int,
    val mood: String,
    val hungry: Boolean,
    val playCool: Boolean,
    val hatch: Int,
    val rare: Boolean,
    val logs: List<PageLog>,
)

@Serializable
data class CoverState(
    val wallKey: String,
    val decoKey: String,
    val name: String,
    val avatarChar: String,
    val avatarEmoji: String? = null, // 助手 emoji 头像(封面圆窗复用)
    val avatarUrl: String? = null, // 助手图片头像(http/https 可直接加载;content:// 由页面兜底为字)
)

@Serializable
data class HomeState(val lights: Int, val mailbox: Int, val name: String)

@Serializable
data class ThoughtsState(val thoughts: List<PageThought>)

@Serializable
data class DrawnState(val drawn: PageThought?)

@Serializable
data class LightsState(val lit: List<PageLight>, val dim: List<PageLight>, val name: String)

@Serializable
data class GardenState(val plants: List<PagePlant>, val logs: List<PageLog>)

@Serializable
data class PetState(val pets: List<PagePet>, val pantry: Int, val full: Boolean, val fail: String? = null)

private val ZONE: ZoneId = ZoneId.systemDefault()

/** 七问口径:今天 HH:mm / 昨天 HH:mm / M月d日 HH:mm */
fun worldTime(epoch: Long): String {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE)
    val today = LocalDateTime.now(ZONE).toLocalDate()
    val date = ldt.toLocalDate()
    val hm = DateTimeFormatter.ofPattern("HH:mm").format(ldt)
    return when (date) {
        today -> "今天 $hm"
        today.minusDays(1) -> "昨天 $hm"
        else -> DateTimeFormatter.ofPattern("M月d日 HH:mm").format(ldt)
    }
}

/** M月d日( joining/播种日期用) */
fun worldDate(epoch: Long): String =
    DateTimeFormatter.ofPattern("M月d日").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE))

fun toPageThought(entity: WorldThoughtEntity): PageThought {
    val mood = WorldConstants.MOODS[entity.mood] ?: (entity.mood to "🍃")
    return PageThought(
        id = entity.id,
        mood = mood.first,
        e = mood.second,
        c = MOOD_COLORS[entity.mood] ?: "#E3F2E7",
        text = entity.content,
        time = worldTime(entity.createdAt),
        author = entity.author,
    )
}

fun toPageLights(entities: List<WorldLightEntity>, assistantName: String): LightsState {
    val lit = entities.filter { it.outAt == null }
        .sortedByDescending { it.litAt }
        .map { toPageLight(it, assistantName) }
    val dim = entities.filter { it.outAt != null }
        .sortedByDescending { it.outAt ?: it.litAt }
        .map { toPageLight(it, assistantName) }
    return LightsState(lit = lit, dim = dim, name = assistantName)
}

private fun toPageLight(entity: WorldLightEntity, assistantName: String) = PageLight(
    id = entity.id,
    who = if (entity.author == "user") "我" else assistantName,
    me = entity.author == "user",
    text = entity.content,
    time = worldTime(entity.litAt),
)

/** 4 盆:缺槽补空盆(sp=null/stage=-1),与 garden-v2 的 PLANTS demo 形状一致 */
fun toPagePlants(entities: List<WorldPlantEntity>): List<PagePlant> {
    val now = System.currentTimeMillis()
    val bySlot = entities.associateBy { it.slot }
    return (0..3).map { slot ->
        val p = bySlot[slot]
        if (p == null) {
            PagePlant(id = 0, pot = slot, sp = null, stage = -1, thirsty = false, bug = false, planted = "—", days = 0, waters = 0, last = "—")
        } else {
            PagePlant(
                id = p.id,
                pot = slot,
                sp = p.species,
                stage = stageOf(p.growth),
                thirsty = p.thirst > WorldConstants.THIRSTY_THRESHOLD,
                bug = p.hasPest || p.hasWeed,
                planted = worldDate(p.plantedAt),
                days = ((now - p.plantedAt) / 86_400_000L).toInt().coerceAtLeast(0),
                waters = p.waters,
                last = if (p.lastCareAt > 0) worldTime(p.lastCareAt) else "—",
            )
        }
    }
}

/** growth → 生长四段档位(种子/新芽/打苞/盛放) */
fun stageOf(growth: Int): Int {
    var stage = 0
    WorldConstants.STAGE_THRESHOLDS.forEachIndexed { i, t -> if (growth >= t) stage = i }
    return stage
}

fun toPageLogs(list: List<GardenLogEntity>): List<PageLog> = list.map {
    PageLog(a = if (it.actor == "user") "you" else "ta", t = it.detail, time = worldTime(it.createdAt))
}

fun toPagePet(pet: WorldPetEntity, events: List<WorldPetEventEntity>, now: Long): PagePet {
    val isEgg = pet.species == "egg"
    val ageBase = pet.bornAt ?: pet.createdAt
    val hungry = !isEgg && now - pet.lastFedAt > WorldConstants.HUNGRY_AFTER_HOURS * 3600_000L
    return PagePet(
        id = pet.id,
        sp = pet.species,
        name = pet.name,
        gender = if (isEgg) "?" else if (pet.gender == "boy") "弟弟" else "妹妹",
        months = ((now - ageBase) / (30L * 86_400_000L)).toInt().coerceAtLeast(0),
        joined = worldDate(pet.createdAt),
        days = ((now - pet.createdAt) / 86_400_000L).toInt().coerceAtLeast(0),
        mood = when {
            isEgg -> if (pet.hatchSignals >= (if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED)) "可以孵啦" else "熟睡"
            hungry -> "饿饿"
            now - pet.lastPlayAt < 2 * 3600_000L -> "开心"
            now - pet.lastFedAt < 4 * 3600_000L -> "满足"
            else -> "慵懒"
        },
        hungry = hungry,
        playCool = false, // 一期不设陪玩冷却(页面按钮不灰置)
        hatch = pet.hatchSignals,
        rare = pet.isGolden,
        logs = events.filter { it.petId == pet.id }.map {
            PageLog(a = if (it.actor == "user") "you" else "ta", t = it.detail, time = worldTime(it.createdAt))
        },
    )
}

fun toPagePets(pets: List<WorldPetEntity>, events: List<WorldPetEventEntity>, now: Long): List<PagePet> =
    pets.map { toPagePet(it, events, now) }
