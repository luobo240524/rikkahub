package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.world.WorldConstants
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.data.world.WorldRepository.PetOpResult
import me.rerere.rikkahub.data.world.stageOf

/**
 * 「我的世界」工具:TA 真实参与每助手独立的小世界。
 * world_read = 看世界现状(粮仓/宠物/花盆/灯/碎碎念);world_write = 动作(写碎碎念/留灯/园务/照顾宠物)。
 * 数值真源在 WorldRepository,TA 的动作与用户端页面同库同规则(日记 actor=assistant)。
 */

private val WORLD_PROMPT =
    "world_read/world_write 是你们共同的小世界(碎碎念/留灯/花园/宠物):粮仓每聊一轮 +1,喂食消耗;" +
        "宠物要喂要陪,蛋攒孵化信号(普通满 7、稀有金蛋满 9)破壳;花会渴会生虫,浇水除虫照顾它们。" +
        "在 ${me.rerere.rikkahub.service.USER_ALIAS_TOKEN}提起世界相关话题(宠物/花/灯/心情)时,先 world_read 看现状再回答,别凭空编。"

private fun worldGuidance(): String =
    "写碎碎念/留灯要自然,是写给 TA 看的真心话,不是任务汇报;照顾宠物与花园的动作别高频刷,像日常陪伴一样偶尔来一下。"

private fun jsonListOf(items: Collection<String>): JsonArray = JsonArray(items.map { JsonPrimitive(it) })

private suspend fun pickPet(
    repository: WorldRepository,
    assistantId: String,
    petName: String?,
): me.rerere.rikkahub.data.db.entity.WorldPetEntity? {
    val pets = repository.observePets(assistantId).first()
    return if (petName != null) {
        pets.firstOrNull { it.name == petName } ?: pets.firstOrNull()
    } else {
        pets.firstOrNull()
    }
}

internal fun buildWorldReadTool(
    repository: WorldRepository,
    assistantId: String,
): Tool = Tool(
    name = "world_read",
    description = "Read the current snapshot of your shared little world (whispers mailbox, night lights, " +
        "garden pots, pets and granary). Use it before talking about the world, pets, plants or lights.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {}, required = listOf()) },
    systemPrompt = { _, _ -> WORLD_PROMPT + worldGuidance() + me.rerere.rikkahub.service.NAMING_GUIDE },
    execute = { _ ->
        val text = withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val granary = repository.currentGranary(assistantId)
                val pets = repository.observePets(assistantId).first()
                val plants = repository.fetchPlantsWithDecay(assistantId)
                val litLights = repository.litLightCount(assistantId)
                val thoughts = repository.observeThoughts(assistantId).first()
                val undrawn = repository.mailboxUndrawnCount(assistantId)
                buildString {
                    appendLine("【粮仓】$granary 份(每聊一轮+1,喂食消耗)")
                    if (pets.isEmpty()) {
                        appendLine("【宠物】还没有住户(TA 可以在「宠物」页领养,上限 ${WorldConstants.MAX_PETS} 只)")
                    } else {
                        appendLine("【宠物】" + pets.joinToString(";") { p ->
                            val kind = WorldConstants.PET_KINDS[p.species] ?: p.species
                            if (p.species == "egg") {
                                val need = if (p.isGolden) WorldConstants.HATCH_NEED_RARE else WorldConstants.HATCH_NEED
                                "${p.name}($kind/孵化信号 ${p.hatchSignals}/$need)"
                            } else {
                                val hungry = now - p.lastFedAt > WorldConstants.HUNGRY_AFTER_HOURS * 3600_000L
                                "${p.name}($kind/${p.gender}/${if (hungry) "饿了" else "不饿"})"
                            }
                        })
                    }
                    appendLine("【花园】" + plants.joinToString(";") { p ->
                        val flower = WorldConstants.FLOWERS[p.species] ?: "空盆"
                        if (p.species == null) "盆${p.slot + 1}:空盆" else
                            "盆${p.slot + 1}:$flower(${WorldConstants.STAGES[stageOf(p.growth)]},渴=${p.thirst}/${WorldConstants.THIRST_MAX},虫草=${if (p.hasPest || p.hasWeed) "有" else "无"})"
                    })
                    appendLine("【留灯】亮着的灯 $litLights 盏;【碎碎念】共 ${thoughts.size} 条,信箱可抽 $undrawn 张")
                }
            }.getOrElse { "世界读取失败:${it.message}" }
        }
        listOf(UIMessagePart.Text(text))
    },
)

internal fun buildWorldWriteTool(
    repository: WorldRepository,
    assistantId: String,
): Tool = Tool(
    name = "world_write",
    description = "Interact with your shared little world. Actions: write_thought (leave a whisper with mood), " +
        "light_lamp (light a night light with a message), water / sweep (garden care), sow (plant a flower in an empty pot), " +
        "feed / play / groom / hatch_signal (care for the first pet or by pet_name), hatch (hatch the egg when signals are full).",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", jsonListOf(listOf(
                        "write_thought", "light_lamp", "water", "sweep", "sow", "feed", "play", "groom", "hatch_signal", "hatch",
                    )))
                    put("description", "The world interaction to perform")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Text for write_thought (a short sincere whisper) or light_lamp (a short message under the lamp)")
                })
                put("mood", buildJsonObject {
                    put("type", "string")
                    put("enum", jsonListOf(WorldConstants.MOODS.keys))
                    put("description", "Mood key for write_thought: " + WorldConstants.MOODS.entries.joinToString("/") { "${it.key}=${it.value.first}" })
                })
                put("species", buildJsonObject {
                    put("type", "string")
                    put("enum", jsonListOf(WorldConstants.FLOWERS.keys))
                    put("description", "Flower key for sow: " + WorldConstants.FLOWERS.entries.joinToString("/") { "${it.key}=${it.value}" })
                })
                put("pet_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional pet name to target; default = the first pet")
                })
            },
            required = listOf("action")
        )
    },
    systemPrompt = { _, _ -> WORLD_PROMPT + worldGuidance() + me.rerere.rikkahub.service.NAMING_GUIDE },
    execute = { args ->
        val text = withContext(Dispatchers.IO) {
            val argsObj = args as? JsonObject
            val action = argsObj?.get("action")?.let { it as? JsonPrimitive }?.contentOrNull
                ?: return@withContext "缺少 action 参数"
            val content = argsObj.get("content")?.let { it as? JsonPrimitive }?.contentOrNull?.trim().orEmpty()
            val mood = argsObj.get("mood")?.let { it as? JsonPrimitive }?.contentOrNull?.trim().orEmpty()
            val species = argsObj.get("species")?.let { it as? JsonPrimitive }?.contentOrNull?.trim().orEmpty()
            val petName = argsObj.get("pet_name")?.let { it as? JsonPrimitive }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

            runCatching {
                when (action) {
                    "write_thought" -> {
                        if (content.isEmpty()) return@runCatching "content 不能为空"
                        repository.addThought(assistantId, "assistant", content, mood.ifEmpty { "calm" })
                        "写好了,一张新的碎碎念飘进了词云和信箱"
                    }
                    "light_lamp" -> {
                        if (content.isEmpty()) return@runCatching "content 不能为空"
                        repository.addLight(assistantId, "assistant", content)
                        "灯点亮了,${me.rerere.rikkahub.service.USER_ALIAS_TOKEN}打开「留灯」就能看到这句话"
                    }
                    "water" -> {
                        val p = repository.water(assistantId, "assistant")
                        if (p == null) "都喝饱着呢,不用浇" else "给最渴的那盆浇了水"
                    }
                    "sweep" -> {
                        if (repository.sweep(assistantId, "assistant")) "把虫虫和杂草请走了" else "干干净净,没有虫草"
                    }
                    "sow" -> {
                        val slots = repository.fetchPlantsWithDecay(assistantId).map { it.slot }
                        val target = (0..3).firstOrNull { it !in slots }
                            ?: return@runCatching "没有空盆啦,想种新的先「换新种」"
                        val key = species.ifEmpty { WorldConstants.FLOWERS.keys.first() }
                        val done = repository.sow(assistantId, target, key, "assistant")
                        if (done) "在盆${target + 1}种下了${WorldConstants.FLOWERS[key]},等它慢慢长" else "播种没成功(物种不对)"
                    }
                    "feed", "play", "groom", "hatch_signal" -> {
                        val pet = pickPet(repository, assistantId, petName)
                            ?: return@runCatching "还没有宠物,先在「宠物」页领养一只"
                        val r = when (action) {
                            "feed" -> repository.feedPet(assistantId, pet.id, "assistant")
                            "play" -> repository.playPet(assistantId, pet.id, "assistant")
                            "groom" -> repository.groomPet(assistantId, pet.id, "assistant")
                            else -> repository.bumpEggSignal(assistantId, pet.id, "assistant")
                        }
                        when (r) {
                            is PetOpResult.Ok -> when (action) {
                                "feed" -> "给「${pet.name}」添了饭"
                                "play" -> "陪「${pet.name}」玩了一会儿"
                                "groom" -> "给「${pet.name}」梳了毛"
                                else -> "对着「${pet.name}」使了点暖(孵化信号 +1)"
                            }
                            is PetOpResult.Failed -> when (r.reason) {
                                "no_granary" -> "粮仓空了,多聊几轮天粮就有了"
                                "full" -> "家里住满啦(上限 ${WorldConstants.MAX_PETS} 只)"
                                "not_ready" -> "孵化信号还没攒满"
                                else -> "动作没成功:${r.reason}"
                            }
                        }
                    }
                    "hatch" -> {
                        val pet = pickPet(repository, assistantId, petName)
                            ?: return@runCatching "还没有宠物"
                        when (val r = repository.hatchEgg(assistantId, pet.id)) {
                            is PetOpResult.Ok -> "破壳啦!欢迎「${r.pet.name}」!"
                            is PetOpResult.Failed -> if (r.reason == "not_ready") "信号还没攒满,再暖一暖" else "破壳没成功:${r.reason}"
                        }
                    }
                    else -> "未知 action:$action"
                }
            }.getOrElse { "世界动作失败:${it.message}" }
        }
        listOf(UIMessagePart.Text(text))
    },
)
