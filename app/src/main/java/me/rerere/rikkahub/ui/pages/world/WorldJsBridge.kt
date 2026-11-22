package me.rerere.rikkahub.ui.pages.world

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.data.world.CoverState
import me.rerere.rikkahub.data.world.DrawnState
import me.rerere.rikkahub.data.world.GardenState
import me.rerere.rikkahub.data.world.HomeState
import me.rerere.rikkahub.data.world.LightsState
import me.rerere.rikkahub.data.world.PetState
import me.rerere.rikkahub.data.world.ThoughtsState
import me.rerere.rikkahub.data.world.WorldConstants
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.data.world.toPageLogs
import me.rerere.rikkahub.data.world.toPagePlants
import me.rerere.rikkahub.data.world.toPagePet
import me.rerere.rikkahub.data.world.toPageThought
import me.rerere.rikkahub.data.world.toPageLights
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 「你的世界」JS 桥:页面通过 window.AndroidWorld 调用(WebViewState.interfaces 注入)。
 * 每个 @JavascriptInterface 方法都在 WebView 的 JavaBridge 线程跑,runBlocking 等仓库层落库后
 * 返回该页**最新状态 JSON**(页面拿到直接整页重渲,单一往返、无需反向 evaluate 推送)。
 * 错误统一折成 {"error": "..."} 字符串返回,不让桥线程崩。
 */
class WorldJsBridge(
    private val assistantId: String,
    private val assistantName: String,
    private val repository: WorldRepository,
) {
    /** 原生壳注入:页面跳转走原生 loadUrl(主线程),保证顶栏标题与返回栈跟随 */
    var onNav: ((String) -> Unit)? = null

    private fun err(e: Throwable): String =
        JsonInstant.encodeToString(mapOf("error" to (e.message ?: "error")))

    // ================= 状态快照 =================

    private suspend fun buildState(page: String, fail: String? = null): String {
        val now = System.currentTimeMillis()
        return when (page) {
            "cover" -> {
                val st = repository.getState(assistantId)
                JsonInstant.encodeToString(
                    CoverState(
                        wallKey = st?.wallKey.orEmpty(),
                        decoKey = st?.decoKey.orEmpty(),
                        name = assistantName,
                        avatarChar = assistantName.firstOrNull()?.toString() ?: "TA",
                    )
                )
            }
            "home" -> JsonInstant.encodeToString(
                HomeState(
                    lights = repository.litLightCountFromTa(assistantId),
                    mailbox = repository.mailboxUndrawnCount(assistantId),
                    name = assistantName,
                )
            )
            "thoughts" -> JsonInstant.encodeToString(
                ThoughtsState(
                    thoughts = repository.observeThoughts(assistantId).first().map { toPageThought(it) }
                )
            )
            "lights" -> JsonInstant.encodeToString(
                toPageLights(repository.observeLights(assistantId).first(), assistantName)
            )
            "garden" -> JsonInstant.encodeToString(
                GardenState(
                    plants = toPagePlants(repository.fetchPlantsWithDecay(assistantId)),
                    logs = toPageLogs(repository.observeGardenLog(assistantId).first()),
                )
            )
            "pet" -> {
                val pets = repository.observePets(assistantId).first()
                val events = repository.observePetEvents(assistantId).first()
                JsonInstant.encodeToString(
                    PetState(
                        pets = pets.map { toPagePet(it, events, now) },
                        pantry = repository.currentGranary(assistantId),
                        full = pets.size >= WorldConstants.MAX_PETS,
                        fail = fail,
                    )
                )
            }
            else -> "{}"
        }
    }

    @JavascriptInterface
    fun getState(page: String): String = runBlocking {
        runCatching { buildState(page) }.getOrElse { err(it) }
    }

    // ================= 导航 =================

    @JavascriptInterface
    fun nav(page: String) {
        onNav?.invoke(page)
    }

    // ================= 碎碎念 =================

    @JavascriptInterface
    fun addThought(mood: String, text: String): String = runBlocking {
        runCatching {
            if (text.isNotBlank()) repository.addThought(assistantId, "user", text.trim(), mood)
            buildState("thoughts")
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun drawMailbox(): String = runBlocking {
        runCatching {
            JsonInstant.encodeToString(DrawnState(repository.drawFromMailbox(assistantId)?.let { toPageThought(it) }))
        }.getOrElse { err(it) }
    }

    // ================= 留灯 =================

    private fun lightsOp(action: suspend () -> Unit): String = runBlocking {
        runCatching {
            action()
            buildState("lights")
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun addLight(text: String): String = lightsOp {
        if (text.isNotBlank()) repository.addLight(assistantId, "user", text.trim())
    }

    @JavascriptInterface
    fun extinguish(id: Long): String = lightsOp {
        repository.extinguishLight(assistantId, id)
    }

    // ================= 花园 =================

    private fun gardenOp(action: suspend () -> Unit): String = runBlocking {
        runCatching {
            action()
            buildState("garden")
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun sow(slot: Int, sp: String): String = gardenOp { repository.sow(assistantId, slot, sp, "user") }

    @JavascriptInterface
    fun water(): String = gardenOp { repository.water(assistantId, "user") }

    @JavascriptInterface
    fun waterOne(plantId: Long): String = gardenOp { repository.waterPlant(assistantId, plantId, "user") }

    @JavascriptInterface
    fun sweep(): String = gardenOp { repository.sweep(assistantId, "user") }

    @JavascriptInterface
    fun replant(plantId: Long, sp: String): String = gardenOp { repository.replant(assistantId, plantId, sp, "user") }

    // ================= 宠物 =================

    /** fail=操作失败原因(no_granary/full/not_ready...),null=成功;页面据此出提示 */
    private fun petOp(action: suspend () -> String?): String = runBlocking {
        runCatching {
            val fail = action()
            buildState("pet", fail)
        }.getOrElse { err(it) }
    }

    private fun failReason(r: WorldRepository.PetOpResult): String? =
        (r as? WorldRepository.PetOpResult.Failed)?.reason

    @JavascriptInterface
    fun adopt(sp: String): String = petOp { failReason(repository.adoptPet(assistantId, sp)) }

    @JavascriptInterface
    fun rename(petId: Long, name: String): String = petOp { failReason(repository.renamePet(assistantId, petId, name)) }

    @JavascriptInterface
    fun feed(petId: Long): String = petOp { failReason(repository.feedPet(assistantId, petId, "user")) }

    @JavascriptInterface
    fun play(petId: Long): String = petOp { failReason(repository.playPet(assistantId, petId, "user")) }

    @JavascriptInterface
    fun groom(petId: Long): String = petOp { failReason(repository.groomPet(assistantId, petId, "user")) }

    @JavascriptInterface
    fun taCare(petId: Long): String = petOp { failReason(repository.taCare(assistantId, petId)) }

    @JavascriptInterface
    fun eggSignal(petId: Long): String = petOp { failReason(repository.bumpEggSignal(assistantId, petId, "user")) }

    @JavascriptInterface
    fun hatch(petId: Long): String = petOp { failReason(repository.hatchEgg(assistantId, petId)) }

    // ================= 封面壁纸/花线持久化 =================

    /** 各页通用:取当前壁纸/花线选择(壁纸取色兜底是全页拍板口径) */
    @JavascriptInterface
    fun getWall(): String = runBlocking {
        runCatching {
            val st = repository.getState(assistantId)
            JsonInstant.encodeToString(
                CoverState(
                    wallKey = st?.wallKey.orEmpty(),
                    decoKey = st?.decoKey.orEmpty(),
                    name = assistantName,
                    avatarChar = assistantName.firstOrNull()?.toString() ?: "TA",
                )
            )
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun saveWall(wallKey: String, decoKey: String): String = runBlocking {
        runCatching {
            repository.saveWall(assistantId, wallKey.takeIf { it.isNotEmpty() }, decoKey.takeIf { it.isNotEmpty() })
            buildState("cover")
        }.getOrElse { err(it) }
    }
}
