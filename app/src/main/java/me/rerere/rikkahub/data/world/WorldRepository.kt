package me.rerere.rikkahub.data.world

import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.data.db.dao.GardenLogDAO
import me.rerere.rikkahub.data.db.dao.WorldLightDAO
import me.rerere.rikkahub.data.db.dao.WorldLetterDAO
import me.rerere.rikkahub.data.db.dao.WorldPetDAO
import me.rerere.rikkahub.data.db.dao.WorldPetEventDAO
import me.rerere.rikkahub.data.db.dao.WorldPlantDAO
import me.rerere.rikkahub.data.db.dao.WorldStateDAO
import me.rerere.rikkahub.data.db.dao.WorldThoughtDAO
import me.rerere.rikkahub.data.db.entity.GardenLogEntity
import me.rerere.rikkahub.data.db.entity.WorldLightEntity
import me.rerere.rikkahub.data.db.entity.WorldLetterEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEventEntity
import me.rerere.rikkahub.data.db.entity.WorldPlantEntity
import me.rerere.rikkahub.data.db.entity.WorldStateEntity
import me.rerere.rikkahub.data.db.entity.WorldThoughtEntity
import me.rerere.rikkahub.data.db.entity.isDue
import me.rerere.rikkahub.utils.JsonInstant

/** 「你的世界」仓库层:所有玩法落库逻辑(按 assistantId 隔离),页面/桥与钩子只经此写库 */
class WorldRepository(
    private val thoughtDao: WorldThoughtDAO,
    private val lightDao: WorldLightDAO,
    private val letterDao: WorldLetterDAO,
    private val plantDao: WorldPlantDAO,
    private val petDao: WorldPetDAO,
    private val petEventDao: WorldPetEventDAO,
    private val gardenLogDao: GardenLogDAO,
    private val stateDao: WorldStateDAO,
    private val relationshipSignals: WorldRelationshipSignals,
) {
    // ================= 碎碎念 =================

    fun observeThoughts(assistantId: String): Flow<List<WorldThoughtEntity>> =
        thoughtDao.flowByAssistant(assistantId)

    suspend fun getThoughtsSnapshot(assistantId: String): List<WorldThoughtEntity> =
        thoughtDao.getByAssistant(assistantId)

    suspend fun addThought(assistantId: String, author: String, content: String, moodKey: String): WorldThoughtEntity {
        val mood = if (WorldConstants.MOODS.containsKey(moodKey)) moodKey else "calm"
        val entity = WorldThoughtEntity(
            assistantId = assistantId,
            author = author,
            content = content,
            mood = mood,
            createdAt = System.currentTimeMillis(),
        )
        val newId = thoughtDao.insert(entity)
        return entity.copy(id = newId)
    }

    /**
     * 抽取信箱:随机抽一张未读碎碎念;全部抽完则重置重抽(随机不重复)。
     * 返回 null = 没有任何碎碎念可抽。
     */
    suspend fun drawFromMailbox(assistantId: String): WorldThoughtEntity? {
        var picked = thoughtDao.getRandomUndrawn(assistantId)
        if (picked == null && thoughtDao.getByAssistant(assistantId).isNotEmpty()) {
            thoughtDao.resetDrawn(assistantId)
            picked = thoughtDao.getRandomUndrawn(assistantId)
        }
        picked?.let { thoughtDao.markDrawn(it.id, System.currentTimeMillis()) }
        return picked
    }

    suspend fun mailboxUndrawnCount(assistantId: String): Int = thoughtDao.countUndrawn(assistantId)

    // ================= 留灯 =================

    fun observeLights(assistantId: String): Flow<List<WorldLightEntity>> =
        lightDao.flowByAssistant(assistantId)

    suspend fun addLight(assistantId: String, author: String, content: String): WorldLightEntity {
        val entity = WorldLightEntity(
            assistantId = assistantId,
            author = author,
            content = content,
            litAt = System.currentTimeMillis(),
        )
        val newId = lightDao.insert(entity)
        return entity.copy(id = newId)
    }

    /** 点开即熄(写 out_at 即已读);不强制回句由页面/交互层决定 */
    suspend fun extinguishLight(assistantId: String, id: Long) {
        lightDao.extinguish(assistantId, id, System.currentTimeMillis())
    }

    // ================= 信件 =================

    fun observeLetters(assistantId: String): Flow<List<WorldLetterEntity>> =
        letterDao.flowByAssistant(assistantId)

    suspend fun getLettersSnapshot(assistantId: String): List<WorldLetterEntity> =
        letterDao.getByAssistant(assistantId)

    suspend fun addLetter(
        assistantId: String,
        author: String,
        content: String,
        scheduledAt: Long? = null,
    ): WorldLetterEntity? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val effectiveSchedule = scheduledAt?.takeIf { it > now }
        val entity = WorldLetterEntity(
            assistantId = assistantId,
            author = author,
            content = trimmed,
            createdAt = now,
            scheduledAt = effectiveSchedule,
        )
        val id = letterDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun openLetter(assistantId: String, id: Long): WorldLetterEntity? {
        val letter = letterDao.getById(assistantId, id) ?: return null
        if (!isDue(letter.scheduledAt)) return letter
        letterDao.markOpened(assistantId, id, System.currentTimeMillis())
        return letter.copy(openedAt = letter.openedAt ?: System.currentTimeMillis())
    }

    suspend fun markLetterNotified(id: Long, now: Long = System.currentTimeMillis()): Boolean =
        letterDao.markNotified(id, now) > 0

    suspend fun getDueLetters(now: Long = System.currentTimeMillis()): List<WorldLetterEntity> =
        letterDao.getDueUnnotified(now)

    /** 启动恢复:所有还没通知过的预约信(含已过点但没送达的),用于重新排程 */
    suspend fun getUnnotifiedScheduledLetters(): List<WorldLetterEntity> =
        letterDao.getUnnotifiedScheduled()

    suspend fun litLightCount(assistantId: String): Int = lightDao.countLit(assistantId)

    /** 首页徽标口径:只数 TA 留的还亮着的灯(用户自己的不算未读) */
    suspend fun litLightCountFromTa(assistantId: String): Int = lightDao.countLitByAuthor(assistantId, "assistant")

    /** 熄灯 30 天惰性清理(页面打开时调用) */
    suspend fun cleanupLights(assistantId: String) {
        val threshold = System.currentTimeMillis() - WorldConstants.LIGHT_KEEP_DAYS * 24L * 3600_000L
        lightDao.cleanupExtinguished(assistantId, threshold)
    }

    // ================= 花园 =================

    fun observePlants(assistantId: String): Flow<List<WorldPlantEntity>> =
        plantDao.flowByAssistant(assistantId)

    /**
     * decay-on-read(vitals 先例):读取时按距 last_care_at 的时长推进口渴/虫/草并落回。
     * 口渴线性增长;超 PEST_AFTER_HOURS 后按整点种子判一次生虫/长草(防反复抖动)。
     */
    suspend fun fetchPlantsWithDecay(assistantId: String): List<WorldPlantEntity> {
        val now = System.currentTimeMillis()
        val updated = plantDao.getByAssistant(assistantId).map { p ->
            val hours = ((now - p.lastCareAt).coerceAtLeast(0)) / 3600_000f
            val thirst = (p.thirst + (hours * WorldConstants.THIRST_PER_HOUR).toInt())
                .coerceIn(0, WorldConstants.THIRST_MAX)
            var pest = p.hasPest
            var weed = p.hasWeed
            if (!pest && hours > WorldConstants.PEST_AFTER_HOURS) {
                pest = seedRandom(p.id, now / 3600_000L) < WorldConstants.PEST_CHANCE
            }
            if (!weed && hours > WorldConstants.PEST_AFTER_HOURS) {
                weed = seedRandom(p.id + 1, now / 3600_000L) < WorldConstants.PEST_CHANCE
            }
            if (thirst != p.thirst || pest != p.hasPest || weed != p.hasWeed) {
                p.copy(thirst = thirst, hasPest = pest, hasWeed = weed).also { plantDao.update(it) }
            } else {
                p
            }
        }
        return updated
    }

    private fun seedRandom(seed: Long, salt: Long): Float {
        val r = Random((seed * 1_000_003L + salt).hashCode().toLong())
        return r.nextFloat()
    }

    /** 播种:空盆种下新花 */
    suspend fun sow(assistantId: String, slot: Int, species: String, actor: String): Boolean {
        if (!WorldConstants.FLOWERS.containsKey(species)) return false
        if (plantDao.getBySlot(assistantId, slot) != null) return false
        val now = System.currentTimeMillis()
        plantDao.insert(
            WorldPlantEntity(
                assistantId = assistantId,
                slot = slot,
                species = species,
                lastCareAt = now,
                plantedAt = now,
            )
        )
        logGarden(assistantId, actor, "sow", null, "在${slot + 1}号盆种下了${WorldConstants.FLOWERS[species]}", now)
        return true
    }

    /** 浇水:浇最渴的一盆(拍板口径);全部不渴则提示无需浇 */
    suspend fun water(assistantId: String, actor: String): WorldPlantEntity? {
        val now = System.currentTimeMillis()
        val target = fetchPlantsWithDecay(assistantId)
            .maxByOrNull { it.thirst }?.takeIf { it.thirst > 0 } ?: return null
        return applyWater(assistantId, target, actor, now)
    }

    /** 详情卡浇指定盆 */
    suspend fun waterPlant(assistantId: String, plantId: Long, actor: String): WorldPlantEntity? {
        val now = System.currentTimeMillis()
        val target = plantDao.getById(assistantId, plantId) ?: return null
        val decayed = fetchPlantsWithDecay(assistantId).firstOrNull { it.id == plantId } ?: target
        if (decayed.thirst <= 0) return null
        return applyWater(assistantId, decayed, actor, now)
    }

    private suspend fun applyWater(assistantId: String, target: WorldPlantEntity, actor: String, now: Long): WorldPlantEntity {
        val watered = target.copy(
            thirst = 0,
            growth = target.growth + WorldConstants.GROWTH_PER_CARE,
            waters = target.waters + 1,
            hasWeed = false, // 浇水顺手冲掉草(轻量照顾)
            lastCareAt = now,
        )
        plantDao.update(watered)
        val name = WorldConstants.FLOWERS[watered.species] ?: ""
        logGarden(assistantId, actor, "water", watered.id, "给${watered.slot + 1}号盆的$name 浇了水", now)
        return watered
    }

    /** 除虫除草:一次性清掉所有盆的虫和草(页面对应「除虫除草」单按钮) */
    suspend fun sweep(assistantId: String, actor: String): Boolean {
        val now = System.currentTimeMillis()
        val dirty = fetchPlantsWithDecay(assistantId).filter { it.hasPest || it.hasWeed }
        if (dirty.isEmpty()) return false
        dirty.forEach { p ->
            plantDao.update(
                p.copy(hasPest = false, hasWeed = false, growth = p.growth + WorldConstants.GROWTH_PER_CARE, lastCareAt = now)
            )
        }
        logGarden(assistantId, actor, "weeding", dirty.firstOrNull()?.id, "把${dirty.size}号盆的虫虫和杂草请走了", now)
        return true
    }

    /** 换新种:拔掉旧花重播种(同盆换新) */
    suspend fun replant(assistantId: String, plantId: Long, newSpecies: String, actor: String): Boolean {
        val plant = plantDao.getById(assistantId, plantId) ?: return false
        if (!WorldConstants.FLOWERS.containsKey(newSpecies)) return false
        val now = System.currentTimeMillis()
        plantDao.delete(plant)
        plantDao.insert(
            WorldPlantEntity(
                assistantId = assistantId,
                slot = plant.slot,
                species = newSpecies,
                lastCareAt = now,
                plantedAt = now,
            )
        )
        logGarden(assistantId, actor, "replant", plant.id, "${plant.slot + 1}号盆换上了${WorldConstants.FLOWERS[newSpecies]}", now)
        return true
    }

    fun observeGardenLog(assistantId: String): Flow<List<GardenLogEntity>> =
        gardenLogDao.flowRecent(assistantId, WorldConstants.DIARY_LIMIT)

    suspend fun getGardenDiary(
        assistantId: String,
        limit: Int = WorldConstants.DIARY_LIMIT,
        offset: Int = 0,
    ): List<GardenLogEntity> = gardenLogDao.getRecent(
        assistantId,
        limit.coerceIn(1, WorldConstants.DIARY_LIMIT),
        offset.coerceAtLeast(0),
    )

    /** 小园日记带去重落库(同 actor+action 连续点只记一条,动作本身照常生效) */
    private suspend fun logGarden(assistantId: String, actor: String, action: String, plantId: Long?, detail: String, now: Long) {
        val latest = gardenLogDao.getLatest(assistantId)
        if (latest != null && latest.actor == actor && latest.action == action &&
            now - latest.createdAt < WorldConstants.DEDUPE_WINDOW_MS
        ) {
            return
        }
        gardenLogDao.insert(
            GardenLogEntity(
                assistantId = assistantId,
                actor = actor,
                action = action,
                plantId = plantId,
                detail = detail,
                createdAt = now,
            )
        )
    }

    // ================= 宠物 =================

    fun observePets(assistantId: String): Flow<List<WorldPetEntity>> =
        petDao.flowByAssistant(assistantId)

    suspend fun getPetsSnapshot(assistantId: String): List<WorldPetEntity> =
        petDao.getByAssistant(assistantId)

    fun observePetEvents(assistantId: String): Flow<List<WorldPetEventEntity>> =
        petEventDao.flowRecent(assistantId, WorldConstants.DIARY_LIMIT)

    suspend fun getPetDiary(
        assistantId: String,
        limit: Int = WorldConstants.DIARY_LIMIT,
        offset: Int = 0,
    ): List<WorldPetEventEntity> = petEventDao.getRecent(
        assistantId,
        limit.coerceIn(1, WorldConstants.DIARY_LIMIT),
        offset.coerceAtLeast(0),
    )

    suspend fun getPetEventsSnapshot(assistantId: String, petId: Long, limit: Int = 30): List<WorldPetEventEntity> =
        petEventDao.getRecentForPet(assistantId, petId, limit.coerceIn(1, 100))

    sealed class PetOpResult {
        data class Ok(val pet: WorldPetEntity) : PetOpResult()
        data class Failed(val reason: String) : PetOpResult()
    }

    private suspend fun petCount(assistantId: String): Int = petDao.count(assistantId)

    private fun newPet(assistantId: String, species: String, now: Long, isGolden: Boolean = false): WorldPetEntity =
        WorldPetEntity(
            assistantId = assistantId,
            species = species,
            name = WorldConstants.PET_NAMES[species] ?: species,
            gender = if (Random.nextBoolean()) "girl" else "boy",
            isGolden = isGolden,
            hatchSignals = 0,
            bornAt = if (species == "egg") null else now,
            lastFedAt = now,
            lastPlayAt = now,
            createdAt = now,
        )

    /** 领养宠物/蛋(上限 6 含蛋);蛋 12% 为稀有金蛋(需要 9 信号) */
    suspend fun adoptPet(assistantId: String, species: String): PetOpResult {
        if (species != "egg" && !WorldConstants.DIRECT_PET_POOL.contains(species)) return PetOpResult.Failed("unknown_species")
        if (petCount(assistantId) >= WorldConstants.MAX_PETS) return PetOpResult.Failed("full")
        val now = System.currentTimeMillis()
        val golden = species == "egg" && Random.nextFloat() < WorldConstants.RARE_CHANCE
        val pet = newPet(assistantId, species, now, isGolden = golden)
        val newId = petDao.insert(pet)
        val saved = pet.copy(id = newId)
        logPet(assistantId, saved.id, "user", "adopt", if (golden) "领养了一颗稀有金蛋!" else "领养了新住户", now)
        return PetOpResult.Ok(saved)
    }

    /** 档案卡 ✎ 改名 */
    suspend fun renamePet(assistantId: String, petId: Long, name: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val trimmed = name.trim().take(12)
        if (trimmed.isEmpty()) return PetOpResult.Failed("empty_name")
        val now = System.currentTimeMillis()
        val updated = pet.copy(name = trimmed)
        petDao.update(updated)
        logPet(assistantId, petId, "user", "rename", "给${pet.name} 起了新名字:${trimmed}", now)
        return PetOpResult.Ok(updated)
    }

    /** 成年宠物与孵化蛋统一走服务端冷却判定,成功前不扣粮/涨成长/写日记。 */
    private suspend fun careCooldownFailed(
        assistantId: String,
        petId: Long,
        actor: String,
        action: String,
        now: Long,
    ): Boolean {
        val last = if (actor == "assistant") {
            petEventDao.getLatestForPetActorActions(
                assistantId,
                petId,
                actor,
                listOf("feed", "play", "groom"),
            )
        } else {
            petEventDao.getLatestForPetActorAction(assistantId, petId, actor, action)
        } ?: return false
        val cooldown = if (actor == "assistant") WorldConstants.COOLDOWN_TA_MS else WorldConstants.COOLDOWN_CARE_MS
        return now - last.createdAt < cooldown
    }

    /** 喂食:消耗粮仓 1 份;蛋=攒信号;成体=涨成长+性格。 */
    suspend fun feedPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor, WorldConstants.HATCH_ACT_FEED, WorldConstants.SIGNAL_FEED)
        if (careCooldownFailed(assistantId, petId, actor, "feed", now)) return PetOpResult.Failed("cooldown")
        if (currentGranary(assistantId) <= 0) return PetOpResult.Failed("no_granary")
        consumeGranary(assistantId)
        val gain = growthGain(assistantId, petId, "feed", WorldConstants.GROWTH_PER_FEED)
        val updated = applyCare(pet, now, gain, "affection", refreshFed = true)
        petDao.update(updated)
        val settled = settleStage(assistantId, updated, now)
        logPet(assistantId, petId, actor, "feed", "给${pet.name} 添了饭(成长 +$gain)", now)
        return PetOpResult.Ok(settled)
    }

    suspend fun playPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor, WorldConstants.HATCH_ACT_CARE, WorldConstants.SIGNAL_CARE)
        if (careCooldownFailed(assistantId, petId, actor, "play", now)) return PetOpResult.Failed("cooldown")
        val gain = growthGain(assistantId, petId, "play", WorldConstants.GROWTH_PER_PLAY)
        val updated = applyCare(pet, now, gain, "exploration")
        petDao.update(updated)
        val settled = settleStage(assistantId, updated, now)
        logPet(assistantId, petId, actor, "play", "陪${pet.name} 玩了一会儿(成长 +$gain)", now)
        return PetOpResult.Ok(settled)
    }

    suspend fun groomPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor, WorldConstants.HATCH_ACT_CARE, WorldConstants.SIGNAL_CARE)
        if (careCooldownFailed(assistantId, petId, actor, "groom", now)) return PetOpResult.Failed("cooldown")
        val gain = growthGain(assistantId, petId, "groom", WorldConstants.GROWTH_PER_GROOM)
        val updated = applyCare(pet, now, gain, "affection") // 梳毛算亲昵互动
        petDao.update(updated)
        val settled = settleStage(assistantId, updated, now)
        logPet(assistantId, petId, actor, "groom", "给${pet.name} 梳了梳毛(成长 +$gain)", now)
        return PetOpResult.Ok(settled)
    }

    /**
     * 「让 TA 照顾」:本地模拟 TA 随机做一件照顾(添饭[需粮]/陪玩/梳毛),记 TA 日记。
     * 二阶段升级为真实唤醒轮注入;一期数值与日记先跑通。
     */
    suspend fun taCare(assistantId: String, petId: Long): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, "assistant", WorldConstants.HATCH_ACT_TA, WorldConstants.SIGNAL_TA)
        val roll = Random.nextInt(3)
        return when (roll) {
            0 -> if (currentGranary(assistantId) > 0) feedPet(assistantId, petId, "assistant") else playPet(assistantId, petId, "assistant")
            1 -> playPet(assistantId, petId, "assistant")
            else -> groomPet(assistantId, petId, "assistant")
        }
    }

    /**
     * 攒孵化信号(反连点):按动作类型小额贡献 + 同类冷却,满格且跨本地自然日才可破壳。
     * hatchSignals 单位×10(满 100 页面显示 10.0),不动 DB 字段类型、无迁移。
     * action: hatch_feed(喂食/暖蛋) / hatch_care(摇蛋/盖毯) / hatch_ta(TA照顾) / hatch_chat(聊天关系信号)
     */
    suspend fun bumpEggSignal(
        assistantId: String,
        petId: Long,
        actor: String,
        action: String = WorldConstants.HATCH_ACT_CARE,
        amount: Int = WorldConstants.SIGNAL_CARE,
    ): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species != "egg") return PetOpResult.Failed("not_egg")
        val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
        if (pet.hatchSignals >= need) return PetOpResult.Ok(pet) // 已攒满等破壳,不重复加
        val now = System.currentTimeMillis()
        // 同类冷却从同一只蛋的事件表最近记录判断(不新增字段):聊天信号不卡
        if (action != WorldConstants.HATCH_ACT_CHAT) {
            val cooldown = when (action) {
                WorldConstants.HATCH_ACT_FEED -> WorldConstants.COOLDOWN_FEED_MS
                WorldConstants.HATCH_ACT_TA -> WorldConstants.COOLDOWN_TA_MS
                else -> WorldConstants.COOLDOWN_CARE_MS
            }
            val last = petEventDao.getLatestForPetAction(assistantId, petId, action)?.createdAt
            if (last != null && now - last < cooldown) return PetOpResult.Failed("cooldown")
        }
        val updated = pet.copy(hatchSignals = (pet.hatchSignals + amount).coerceAtMost(need))
        petDao.update(updated)
        val showNeed = need / 10f
        val showNow = updated.hatchSignals / 10f
        logPet(assistantId, petId, actor, action, "对着蛋使了点暖(信号 $showNow/$showNeed)", now)
        return PetOpResult.Ok(updated)
    }

    /** 破壳:满信号 + 至少跨一个本地自然日;随机幻想灵兽(月光/森林/星尘/潮汐),性别随机 */
    suspend fun hatchEgg(assistantId: String, petId: Long): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species != "egg") return PetOpResult.Failed("not_egg")
        val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
        if (pet.hatchSignals < need) return PetOpResult.Failed("not_ready")
        val now = System.currentTimeMillis()
        if (!eggCrossedDays(pet.createdAt, now)) return PetOpResult.Failed("not_cross_day")
        // 关系信号孵化:五维主导维度定物种(全空走四兽随机兜底);exploration 保持四兽随机+探索由来
        val signal = relationshipSignals.compute(assistantId)
        val dim = signal.dominant()
        val species = if (dim != null && dim != "exploration" && WorldConstants.HATCH_DIM_SPECIES.containsKey(dim)) {
            WorldConstants.HATCH_DIM_SPECIES[dim]!!
        } else {
            WorldConstants.FANTASY_HATCH_POOL[Random.nextInt(WorldConstants.FANTASY_HATCH_POOL.size)]
        }
        val origin = dim?.let { WorldConstants.HATCH_DIM_ORIGIN[it] }
        // 不预设名字:破壳后给占位名,用户点档案卡名字旁的 ✎ 自己取
        val baseName = "小家伙"
        val updated = pet.copy(
            species = species,
            name = baseName,
            gender = if (Random.nextBoolean()) "girl" else "boy",
            bornAt = now,
            lastFedAt = now,
            lastPlayAt = now,
            origin = origin,
            personality = JsonInstant.encodeToString(signal.toPersonality()),
        )
        petDao.update(updated)
        val kindName = WorldConstants.PET_KINDS[species] ?: species
        logPet(
            assistantId, petId, "user", "hatch",
            (if (pet.isGolden) "金蛋破壳了!稀有的「$kindName」睁开了眼睛" else "蛋破壳了!「$kindName」睁开了眼睛") +
                (origin?.let { "——$it" } ?: ""),
            now,
        )
        return PetOpResult.Ok(updated)
    }

    // ================= 成长体系(批次B) =================

    /** 有效成长 = 照顾收益累计 + 陪伴天数(每陪伴 1 天 +1,纯派生;老宠物迁移后立即有成长,无需回填) */
    fun effGrowth(pet: WorldPetEntity, now: Long): Int =
        WorldConstants.petEffGrowth(pet.growth, pet.bornAt, pet.createdAt, now)

    /** 重复递减:同一只宠物最近连续同类动作第 n 次,收益 = max(1, base/n);换动作重置。 */
    private suspend fun growthGain(assistantId: String, petId: Long, action: String, base: Int): Int {
        var n = 0
        for (e in petEventDao.getRecentForPet(assistantId, petId, 30)) {
            if (e.action == action) n++ else break
        }
        return max(1, base / n.coerceAtLeast(1))
    }

    /** 照顾落库:刷新时间戳 + 涨成长 + 性格小幅变化(喂食/梳毛→亲近,玩耍→探索) */
    private fun applyCare(pet: WorldPetEntity, now: Long, gain: Int, dim: String, refreshFed: Boolean = false): WorldPetEntity {
        val personality = bumpPersonality(pet, dim)
        return pet.copy(
            lastFedAt = if (refreshFed) now else pet.lastFedAt,
            lastPlayAt = if (refreshFed) pet.lastPlayAt else now, // 喂食不动陪玩时间戳
            growth = pet.growth + gain,
            personality = personality,
        )
    }

    /** 性格5维 JSON 小步更新,clamp 0-100 */
    private fun bumpPersonality(pet: WorldPetEntity, dim: String, amount: Int = 1): String {
        val cur = pet.personality?.let {
            runCatching { JsonInstant.decodeFromString<PetPersonality>(it) }.getOrNull()
        } ?: PetPersonality()
        val next = when (dim) {
            "affection" -> cur.copy(affection = (cur.affection + amount).coerceIn(0, 100))
            "reading" -> cur.copy(reading = (cur.reading + amount).coerceIn(0, 100))
            "watching" -> cur.copy(watching = (cur.watching + amount).coerceIn(0, 100))
            "listening" -> cur.copy(listening = (cur.listening + amount).coerceIn(0, 100))
            else -> cur.copy(exploration = (cur.exploration + amount).coerceIn(0, 100))
        }
        return JsonInstant.encodeToString(next)
    }

    /** 惰性结算阶段:有效成长与陪伴天数达双门槛则进阶,写里程碑日记;返回最新实体 */
    private suspend fun settleStage(assistantId: String, pet: WorldPetEntity, now: Long): WorldPetEntity {
        if (pet.species == "egg") return pet
        val days = WorldConstants.petDays(pet.bornAt, pet.createdAt, now)
        val target = WorldConstants.petStageOf(effGrowth(pet, now), days)
        if (target <= pet.stage) return pet
        val updated = pet.copy(stage = target, stageAt = now)
        petDao.update(updated)
        logPet(assistantId, pet.id, "assistant", WorldConstants.PET_ACTION_STAGE_UP,
            "${pet.name} 进入了${WorldConstants.petStageName(target)} ✦", now)
        return updated
    }

    private suspend fun logPet(assistantId: String, petId: Long?, actor: String, action: String, detail: String, now: Long) {
        val latest = petEventDao.getLatest(assistantId)
        if (latest != null && latest.petId == petId && latest.actor == actor && latest.action == action &&
            now - latest.createdAt < WorldConstants.DEDUPE_WINDOW_MS
        ) {
            return
        }
        petEventDao.insert(
            WorldPetEventEntity(
                assistantId = assistantId,
                petId = petId,
                actor = actor,
                action = action,
                detail = detail,
                createdAt = now,
            )
        )
    }

    // ================= 粮仓/全局计数 =================

    fun observeGranary(assistantId: String): Flow<Int> =
        stateDao.observe(assistantId).map { it?.granary ?: 0 }

    suspend fun currentGranary(assistantId: String): Int = stateDao.get(assistantId)?.granary ?: 0

    /**
     * 每聊一轮:粮仓 +1(ChatService.handleMessageComplete 钩子);无行时建行。
     * 同一钩子顺带给在孵的蛋补聊天关系信号 —— HATCH_ACT_CHAT/SIGNAL_CHAT 之前只有常量没有调用者,
     * 聊天其实一点孵化信号都不加,这里接上(不卡冷却、满格不加)。
     */
    suspend fun incrementGranary(assistantId: String) {
        val now = System.currentTimeMillis()
        if (stateDao.incrementGranary(assistantId, now) == 0) {
            stateDao.upsert(WorldStateEntity(subject = assistantId, granary = WorldConstants.GRANARY_PER_ROUND, updatedAt = now))
        }
        bumpChatHatchSignal(assistantId)
    }

    /** 聊天关系信号:该助手在孵的蛋 +SIGNAL_CHAT;不写事件流水,免得每轮刷满宠物日记 */
    private suspend fun bumpChatHatchSignal(assistantId: String) {
        for (pet in petDao.getByAssistant(assistantId)) {
            if (pet.species != "egg") continue
            val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
            if (pet.hatchSignals >= need) continue
            petDao.update(pet.copy(hatchSignals = (pet.hatchSignals + WorldConstants.SIGNAL_CHAT).coerceAtMost(need)))
        }
    }

    suspend fun getState(assistantId: String): WorldStateEntity? = stateDao.get(assistantId)

    /** 封面壁纸/花线款式持久化(部分更新,粮仓计数不动) */
    suspend fun saveWall(assistantId: String, wallKey: String?, decoKey: String?) {
        val existing = stateDao.get(assistantId)
        stateDao.upsert(
            WorldStateEntity(
                subject = assistantId,
                granary = existing?.granary ?: 0,
                wallKey = wallKey ?: existing?.wallKey.orEmpty(),
                decoKey = decoKey ?: existing?.decoKey.orEmpty(),
                lastNotifiedAt = existing?.lastNotifiedAt ?: 0L,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun consumeGranary(assistantId: String) {
        val state = stateDao.get(assistantId) ?: return
        if (state.granary > 0) {
            stateDao.upsert(state.copy(granary = state.granary - 1, updatedAt = System.currentTimeMillis()))
        }
    }

    // ================= 用户世界动作 → 下一轮告知助手 =================

    /** 收集位点之后的用户动作(升序),返回 (摘要行列表, 最新动作时间);无新动作返回 (空, 位点) */
    suspend fun collectUserActionsSince(
        assistantId: String,
        limit: Int = 12,
    ): Pair<List<String>, Long> {
        val since = stateDao.get(assistantId)?.lastNotifiedAt ?: 0L
        val lines = mutableListOf<String>()
        var newest = since
        thoughtDao.getUserSince(assistantId, since).forEach {
            lines.add("写了一则碎碎念:「${it.content}」")
            newest = maxOf(newest, it.createdAt)
        }
        lightDao.getUserSince(assistantId, since).forEach {
            lines.add("为你留了一盏灯:「${it.content}」")
            newest = maxOf(newest, it.litAt)
        }
        gardenLogDao.getUserSince(assistantId, since).forEach {
            lines.add("在花园里${it.detail}")
            newest = maxOf(newest, it.createdAt)
        }
        petEventDao.getUserSince(assistantId, since).forEach {
            lines.add("照顾宠物:${it.detail}")
            newest = maxOf(newest, it.createdAt)
        }
        if (lines.size > limit) {
            return Pair(lines.take(limit) + listOf("…等共 ${lines.size} 件事"), newest)
        }
        return Pair(lines, newest)
    }

    /** 注入完成后推进位点 */
    suspend fun markUserActionsNotified(assistantId: String, at: Long) {
        stateDao.setLastNotifiedAt(assistantId, at)
    }
}
