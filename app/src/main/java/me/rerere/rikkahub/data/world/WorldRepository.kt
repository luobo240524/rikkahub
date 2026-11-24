package me.rerere.rikkahub.data.world

import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.GardenLogDAO
import me.rerere.rikkahub.data.db.dao.WorldLightDAO
import me.rerere.rikkahub.data.db.dao.WorldPetDAO
import me.rerere.rikkahub.data.db.dao.WorldPetEventDAO
import me.rerere.rikkahub.data.db.dao.WorldPlantDAO
import me.rerere.rikkahub.data.db.dao.WorldStateDAO
import me.rerere.rikkahub.data.db.dao.WorldThoughtDAO
import me.rerere.rikkahub.data.db.entity.GardenLogEntity
import me.rerere.rikkahub.data.db.entity.WorldLightEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEntity
import me.rerere.rikkahub.data.db.entity.WorldPetEventEntity
import me.rerere.rikkahub.data.db.entity.WorldPlantEntity
import me.rerere.rikkahub.data.db.entity.WorldStateEntity
import me.rerere.rikkahub.data.db.entity.WorldThoughtEntity

/** 「你的世界」仓库层:所有玩法落库逻辑(按 assistantId 隔离),页面/桥与钩子只经此写库 */
class WorldRepository(
    private val thoughtDao: WorldThoughtDAO,
    private val lightDao: WorldLightDAO,
    private val plantDao: WorldPlantDAO,
    private val petDao: WorldPetDAO,
    private val petEventDao: WorldPetEventDAO,
    private val gardenLogDao: GardenLogDAO,
    private val stateDao: WorldStateDAO,
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

    /** 喂食:消耗粮仓 1 份;蛋=攒信号;没粮返回 failed(页面引导去聊天攒粮) */
    suspend fun feedPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor)
        if (currentGranary(assistantId) <= 0) return PetOpResult.Failed("no_granary")
        consumeGranary(assistantId)
        val updated = pet.copy(lastFedAt = now)
        petDao.update(updated)
        logPet(assistantId, petId, actor, "feed", "给${pet.name} 添了饭", now)
        return PetOpResult.Ok(updated)
    }

    suspend fun playPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor)
        val updated = pet.copy(lastPlayAt = now)
        petDao.update(updated)
        logPet(assistantId, petId, actor, "play", "陪${pet.name} 玩了一会儿", now)
        return PetOpResult.Ok(updated)
    }

    suspend fun groomPet(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        val now = System.currentTimeMillis()
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, actor)
        val updated = pet.copy(lastPlayAt = now) // 梳毛算亲昵互动,刷新安抚时间
        petDao.update(updated)
        logPet(assistantId, petId, actor, "groom", "给${pet.name} 梳了梳毛", now)
        return PetOpResult.Ok(updated)
    }

    /**
     * 「让 TA 照顾」:本地模拟 TA 随机做一件照顾(添饭[需粮]/陪玩/梳毛),记 TA 日记。
     * 二阶段升级为真实唤醒轮注入;一期数值与日记先跑通。
     */
    suspend fun taCare(assistantId: String, petId: Long): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species == "egg") return bumpEggSignal(assistantId, petId, "assistant")
        val roll = Random.nextInt(3)
        return when (roll) {
            0 -> if (currentGranary(assistantId) > 0) feedPet(assistantId, petId, "assistant") else playPet(assistantId, petId, "assistant")
            1 -> playPet(assistantId, petId, "assistant")
            else -> groomPet(assistantId, petId, "assistant")
        }
    }

    /** 孵蛋互动(暖蛋/摇蛋/盖毯都计一次信号):0/7 或 0/9,满后页面开破壳 */
    suspend fun bumpEggSignal(assistantId: String, petId: Long, actor: String): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species != "egg") return PetOpResult.Failed("not_egg")
        val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
        if (pet.hatchSignals >= need) return PetOpResult.Ok(pet) // 已攒满等破壳,不重复加
        val now = System.currentTimeMillis()
        val updated = pet.copy(hatchSignals = pet.hatchSignals + 1)
        petDao.update(updated)
        logPet(assistantId, petId, actor, "hatch_signal", "对着蛋使了点暖(信号 ${updated.hatchSignals}/$need)", now)
        return PetOpResult.Ok(updated)
    }

    /** 破壳:随机幻想灵兽(月光/森林/星尘/潮汐);金蛋出「黄金+名」+金色调(页面渲染),性别随机 */
    suspend fun hatchEgg(assistantId: String, petId: Long): PetOpResult {
        val pet = petDao.getById(assistantId, petId) ?: return PetOpResult.Failed("not_found")
        if (pet.species != "egg") return PetOpResult.Failed("not_egg")
        val need = if (pet.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
        if (pet.hatchSignals < need) return PetOpResult.Failed("not_ready")
        val now = System.currentTimeMillis()
        val species = WorldConstants.FANTASY_HATCH_POOL[Random.nextInt(WorldConstants.FANTASY_HATCH_POOL.size)]
        val baseName = WorldConstants.PET_NAMES[species] ?: species
        val updated = pet.copy(
            species = species,
            name = if (pet.isGolden) "黄金$baseName" else baseName,
            gender = if (Random.nextBoolean()) "girl" else "boy",
            bornAt = now,
            lastFedAt = now,
            lastPlayAt = now,
        )
        petDao.update(updated)
        logPet(
            assistantId, petId, "user", "hatch",
            if (pet.isGolden) "金蛋破壳了!${updated.name} 睁开了眼睛" else "蛋破壳了!${updated.name} 睁开了眼睛",
            now,
        )
        return PetOpResult.Ok(updated)
    }

    private suspend fun logPet(assistantId: String, petId: Long?, actor: String, action: String, detail: String, now: Long) {
        val latest = petEventDao.getLatest(assistantId)
        if (latest != null && latest.actor == actor && latest.action == action &&
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

    /** 每聊一轮 +1(ChatService.handleMessageComplete 钩子);无行时建行 */
    suspend fun incrementGranary(assistantId: String) {
        val now = System.currentTimeMillis()
        if (stateDao.incrementGranary(assistantId, now) == 0) {
            stateDao.upsert(WorldStateEntity(subject = assistantId, granary = WorldConstants.GRANARY_PER_ROUND, updatedAt = now))
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
