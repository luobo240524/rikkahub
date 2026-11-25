package me.rerere.rikkahub.data.world

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.GardenLogEntity
import me.rerere.rikkahub.data.db.entity.isDue
import me.rerere.rikkahub.data.db.entity.WorldLightEntity
import me.rerere.rikkahub.data.db.entity.WorldLetterEntity
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
    val hatchNeed: Int, // 满格信号值(页面按 /10 显示 10.0)
    val hatchReady: Boolean, // 满格且跨本地自然日,可破壳(跨天前前端不许演破壳)
    val eggCool: List<String>, // 蛋冷却中的动作 key:feed/care/ta
    val careCooldowns: Map<String, Long> = emptyMap(), // 成体对方动作冷却截止时间:feed/play/groom
    val taCooldownUntil: Long? = null,
    val rare: Boolean,
    val growthTotal: Int = 0, // 有效成长(照顾累计+陪伴天数);蛋=0
    val stageName: String = "", // 阶段名(幼年期/成长期/成熟期/完全体...)
    val origin: String? = null, // 破壳由来(关系信号孵化)
    val nextGrowth: Int? = null, // 下一阶段成长门槛(进度条用;顶期 null)
    val nextDays: Int? = null, // 下一阶段天数门槛
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
data class LightsState(val lit: List<PageLight>, val dim: List<PageLight>, val name: String, val userAlias: String = "")

@Serializable
data class PageLetter(
    val id: Long,
    val author: String,
    val who: String,
    val text: String,
    val createdAt: Long,
    val scheduledAt: Long?,
    val due: Boolean,
    val openedAt: Long?,
)

@Serializable
data class PostOfficeState(val letters: List<PageLetter>, val name: String, val userAlias: String = "")

@Serializable
data class EnvelopeState(val thoughts: Int, val letters: Int, val pendingLetters: Int)

@Serializable
data class GardenState(val plants: List<PagePlant>, val logs: List<PageLog>, val name: String = "", val userAlias: String = "")

@Serializable
data class PetState(val pets: List<PagePet>, val pantry: Int, val full: Boolean, val fail: String? = null, val name: String = "", val userAlias: String = "")

// ---------- 「我的世界」音乐(复用一起听网易云接口) ----------

@Serializable
data class PageSong(
    val id: Long,
    val name: String,
    val artist: String,
    val duration: Long, // ms
    val fee: Int, // 0 免费 1 VIP 4 购买专辑 8 低音免费
)

@Serializable
data class MusicPageState(
    val phase: String = "", // Idle/Loading/Playing/Paused/Error
    val name: String = "", // 当前歌名
    val artist: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val songs: List<PageSong> = emptyList(), // 最近一次搜索结果
)

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

fun toPageLights(entities: List<WorldLightEntity>, assistantName: String, userAlias: String = ""): LightsState {
    val lit = entities.filter { it.outAt == null }
        .sortedByDescending { it.litAt }
        .map { toPageLight(it, assistantName, userAlias) }
    val dim = entities.filter { it.outAt != null }
        .sortedByDescending { it.outAt ?: it.litAt }
        .map { toPageLight(it, assistantName, userAlias) }
    return LightsState(lit = lit, dim = dim, name = assistantName, userAlias = userAlias)
}

private fun toPageLight(entity: WorldLightEntity, assistantName: String, userAlias: String) = PageLight(
    id = entity.id,
    // 用户署名复用昵称(未设置退「我」,不用代词口径);TA 侧用助手名
    who = if (entity.author == "user") userAlias.ifBlank { "我" } else assistantName,
    me = entity.author == "user",
    text = entity.content,
    time = worldTime(entity.litAt),
)

fun toPageLetter(entity: WorldLetterEntity, assistantName: String, userAlias: String = "", now: Long = System.currentTimeMillis()): PageLetter =
    PageLetter(
        id = entity.id,
        author = entity.author,
        who = if (entity.author == "user") userAlias.ifBlank { "我" } else assistantName,
        text = entity.content,
        createdAt = entity.createdAt,
        scheduledAt = entity.scheduledAt,
        due = isDue(entity.scheduledAt, now),
        openedAt = entity.openedAt,
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

/** 破壳前置:满信号 + 至少跨一个本地自然日(领养当天算第1天,隔天才能孵) */
fun eggCrossedDays(startAt: Long, now: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val d0 = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), zone).toLocalDate()
    val d1 = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    return ChronoUnit.DAYS.between(d0, d1) >= WorldConstants.CROSS_DAYS - 1L
}

/** 从同一只宠物最近事件里取仍在冷却的动作 key(展示用;真值判定在 WorldRepository)。 */
private fun eggCoolingKeys(events: List<WorldPetEventEntity>, petId: Long, now: Long): List<String> {
    val scoped = events.filter { it.petId == petId }
    val rules = listOf(
        WorldConstants.HATCH_ACT_FEED to WorldConstants.COOLDOWN_FEED_MS,
        WorldConstants.HATCH_ACT_CARE to WorldConstants.COOLDOWN_CARE_MS,
        WorldConstants.HATCH_ACT_TA to WorldConstants.COOLDOWN_TA_MS,
    )
    return rules.filter { (action, ms) ->
        scoped.firstOrNull { it.action == action }?.let { now - it.createdAt < ms } == true
    }.map { (action, _) -> when (action) {
        WorldConstants.HATCH_ACT_FEED -> "feed"
        WorldConstants.HATCH_ACT_CARE -> "care"
        else -> "ta"
    } }
}

private fun latestCareEvent(
    events: List<WorldPetEventEntity>,
    petId: Long,
    actor: String,
    action: String? = null,
): WorldPetEventEntity? = events
    .asSequence()
    .filter { it.petId == petId && it.actor == actor && (action == null || it.action == action) }
    .maxByOrNull { it.createdAt }

private fun careCooldowns(events: List<WorldPetEventEntity>, petId: Long, now: Long): Map<String, Long> =
    listOf("feed", "play", "groom").mapNotNull { action ->
        latestCareEvent(events, petId, "user", action)
            ?.let { (it.createdAt + WorldConstants.COOLDOWN_CARE_MS).takeIf { until -> until > now } }
            ?.let { action to it }
    }.toMap()

private fun taCooldownUntil(events: List<WorldPetEventEntity>, petId: Long, now: Long): Long? =
    latestCareEvent(events, petId, "assistant")?.let { event ->
        (event.createdAt + WorldConstants.COOLDOWN_TA_MS).takeIf { it > now }
    }

/** 下一阶段门槛(成长/天数):进入档 stage+1 所需;顶期返回 null */
fun petNextStage(stage: Int): Pair<Int, Int>? {
    val thresholds = WorldConstants.PET_STAGE_THRESHOLDS
    val days = WorldConstants.PET_STAGE_DAYS
    return when {
        stage < thresholds.size ->
            thresholds[stage] to days[stage]
        else -> {
            val k = stage - thresholds.size + 1
            (thresholds.last() + k * WorldConstants.PET_STAGE_STEP_GROWTH) to
                (days.last() + k * WorldConstants.PET_STAGE_STEP_DAYS)
        }
    }
}

fun toPagePet(pet: WorldPetEntity, events: List<WorldPetEventEntity>, now: Long): PagePet {
    val isEgg = pet.species == "egg"
    val ageBase = pet.bornAt ?: pet.createdAt
    val hungry = !isEgg && now - pet.lastFedAt > WorldConstants.HUNGRY_AFTER_HOURS * 3600_000L
    val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
    val full = pet.hatchSignals >= need
    val days = WorldConstants.petDays(pet.bornAt, pet.createdAt, now)
    val eff = WorldConstants.petEffGrowth(pet.growth, pet.bornAt, pet.createdAt, now)
    val stageIdx = if (isEgg) 0 else WorldConstants.petStageOf(eff, days).coerceAtLeast(pet.stage)
    val next = if (isEgg) null else petNextStage(stageIdx)
    return PagePet(
        id = pet.id,
        sp = pet.species,
        name = pet.name,
        gender = if (isEgg) "?" else if (pet.gender == "boy") "弟弟" else "妹妹",
        months = ((now - ageBase) / (30L * 86_400_000L)).toInt().coerceAtLeast(0),
        joined = worldDate(pet.createdAt),
        days = ((now - pet.createdAt) / 86_400_000L).toInt().coerceAtLeast(0),
        mood = when {
            isEgg -> when {
                !full -> "熟睡"
                eggCrossedDays(pet.createdAt, now) -> "可以孵啦"
                else -> "快醒啦"
            }
            hungry -> "饿饿"
            now - pet.lastPlayAt < 2 * 3600_000L -> "开心"
            now - pet.lastFedAt < 4 * 3600_000L -> "满足"
            else -> "慵懒"
        },
        hungry = hungry,
        playCool = false,
        hatch = pet.hatchSignals,
        hatchNeed = need,
        hatchReady = isEgg && full && eggCrossedDays(pet.createdAt, now),
        eggCool = if (isEgg) eggCoolingKeys(events, pet.id, now) else emptyList(),
        careCooldowns = if (isEgg) emptyMap() else careCooldowns(events, pet.id, now),
        taCooldownUntil = if (isEgg) null else taCooldownUntil(events, pet.id, now),
        rare = pet.isGolden,
        growthTotal = eff,
        stageName = if (isEgg) "" else WorldConstants.petStageName(stageIdx),
        origin = pet.origin?.takeIf { !isEgg },
        nextGrowth = next?.first,
        nextDays = next?.second,
        logs = events.filter { it.petId == pet.id }.map {
            PageLog(a = if (it.actor == "user") "you" else "ta", t = it.detail, time = worldTime(it.createdAt))
        },
    )
}

fun toPagePets(pets: List<WorldPetEntity>, events: List<WorldPetEventEntity>, now: Long): List<PagePet> =
    pets.map { toPagePet(it, events, now) }
