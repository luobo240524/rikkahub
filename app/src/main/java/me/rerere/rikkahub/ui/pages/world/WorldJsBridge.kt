package me.rerere.rikkahub.ui.pages.world

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.db.entity.isDue
import me.rerere.rikkahub.data.music.MusicPlayerController
import me.rerere.rikkahub.data.music.NeteaseClient
import me.rerere.rikkahub.data.world.CoverState
import me.rerere.rikkahub.data.world.DrawnState
import me.rerere.rikkahub.data.world.GardenState
import me.rerere.rikkahub.data.world.HomeState
import me.rerere.rikkahub.data.world.EnvelopeState
import me.rerere.rikkahub.data.world.LightsState
import me.rerere.rikkahub.data.world.PostOfficeState
import me.rerere.rikkahub.data.world.MusicPageState
import me.rerere.rikkahub.data.world.PageSong
import me.rerere.rikkahub.data.world.PetState
import me.rerere.rikkahub.data.world.ThoughtsState
import me.rerere.rikkahub.data.world.WorldConstants
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.data.world.toPageLogs
import me.rerere.rikkahub.data.world.toPagePlants
import me.rerere.rikkahub.data.world.toPageLetter
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
    private val context: Context,
    private val assistantId: String,
    private val assistantName: String,
    private val userAlias: String, // 用户昵称(页面署名用,空则各页退默认)
    private val avatar: Avatar,
    private val repository: WorldRepository,
    private val music: MusicPlayerController,
    initialLetterId: Long = 0L,
) {
    /** 通知深链目标信;池化复用桥时由页面每次进页更新(不能重建桥,缓存页绑着旧实例) */
    private var targetLetterId: Long = initialLetterId

    fun updateInitialLetterId(id: Long) {
        targetLetterId = id
    }

    /** 原生壳注入:页面跳转走原生 loadUrl(主线程) */
    var onNav: ((String) -> Unit)? = null

    /** 原生壳注入:页面内「←」回退(优先 WebView 历史,没有历史才退出路由;主线程) */
    var onBack: (() -> Unit)? = null

    /** 原生壳注入:用户在世界页的交互动作 → 后台提醒助手(与社交通知同机制) */
    var onUserAction: ((String) -> Unit)? = null

    private fun err(e: Throwable): String =
        JsonInstant.encodeToString(mapOf("error" to (e.message ?: "error")))

    /** 用户动作 → 通知回调(JavaBridge 线程,回调方自行切线程) */
    private fun notifyUser(summary: String) {
        runCatching { onUserAction?.invoke(summary) }
    }

    /**
     * 助手图片头像地址 → 页面可加载的地址。
     * http(s)/data: 直接透传;content:// 与本地文件路径(coil 能加载,WebView 跨源加载不了)
     * 读字节转 base64 data URL,封面圆窗才能铺满图片而不是退回首字。
     */
    private fun resolveAvatarUrl(raw: String?): String? {
        val url = raw ?: return null
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) return url
        return runCatching {
            val bytes: ByteArray? = if (url.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
            } else {
                val path = url.removePrefix("file://")
                File(path).takeIf { it.isFile }?.readBytes()
            }
            val data = bytes ?: return null
            val mime = when {
                data.size >= 3 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() -> "image/png"
                data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "image/jpeg"
                data.size >= 4 && data[0] == 0x52.toByte() && data[1] == 0x49.toByte() && data[2] == 0x46.toByte() && data[3] == 0x46.toByte() -> "image/webp"
                data.size >= 3 && data[0] == 0x47.toByte() && data[1] == 0x49.toByte() && data[2] == 0x46.toByte() -> "image/gif"
                else -> "image/jpeg"
            }
            "data:$mime;base64," + Base64.encodeToString(data, Base64.NO_WRAP)
        }.getOrNull()
    }

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
                        avatarEmoji = (avatar as? Avatar.Emoji)?.content,
                        avatarUrl = resolveAvatarUrl((avatar as? Avatar.Image)?.url),
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
            "envelope" -> {
                val letters = repository.getLettersSnapshot(assistantId)
                EnvelopeState(
                    thoughts = repository.getThoughtsSnapshot(assistantId).size,
                    letters = letters.count { isDue(it.scheduledAt, now) },
                    pendingLetters = letters.count { !isDue(it.scheduledAt, now) },
                ).let { JsonInstant.encodeToString(it) }
            }
            "postoffice" -> JsonInstant.encodeToString(
                PostOfficeState(
                    letters = repository.getLettersSnapshot(assistantId).map { toPageLetter(it, assistantName, userAlias, now) },
                    name = assistantName,
                    userAlias = userAlias,
                )
            )
            "lights" -> JsonInstant.encodeToString(
                toPageLights(repository.observeLights(assistantId).first(), assistantName, userAlias)
            )
            "garden" -> JsonInstant.encodeToString(
                GardenState(
                    plants = toPagePlants(repository.fetchPlantsWithDecay(assistantId)),
                    logs = toPageLogs(repository.observeGardenLog(assistantId).first()),
                    name = assistantName,
                    userAlias = userAlias,
                )
            )
            "pet" -> {
                val pets = repository.observePets(assistantId).first()
                val eventsByPet = pets.associate { pet ->
                    pet.id to repository.getPetEventsSnapshot(assistantId, pet.id)
                }
                JsonInstant.encodeToString(
                    PetState(
                        pets = pets.map { pet -> toPagePet(pet, eventsByPet[pet.id].orEmpty(), now) },
                        pantry = repository.currentGranary(assistantId),
                        full = pets.size >= WorldConstants.MAX_PETS,
                        fail = fail,
                        name = assistantName,
                        userAlias = userAlias,
                    )
                )
            }
            else -> "{}"
        }
    }

    @JavascriptInterface
    fun getInitialLetterId(): Long = targetLetterId

    @JavascriptInterface
    fun getState(page: String): String = runBlocking {
        runCatching { buildState(page) }.getOrElse { err(it) }
    }

    // ================= 导航 =================

    @JavascriptInterface
    fun nav(page: String) {
        onNav?.invoke(page)
    }

    @JavascriptInterface
    fun back() {
        onBack?.invoke()
    }

    // ================= 碎碎念 =================

    @JavascriptInterface
    fun addThought(mood: String, text: String): String = runBlocking {
        runCatching {
            if (text.isNotBlank()) {
                repository.addThought(assistantId, "user", text.trim(), mood)
                notifyUser("写了一则碎碎念「${text.trim()}」")
            }
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
        if (text.isNotBlank()) {
            repository.addLight(assistantId, "user", text.trim())
            notifyUser("为${assistantName}留了一盏灯「${text.trim()}」")
        }
    }

    @JavascriptInterface
    fun extinguish(id: Long): String = lightsOp {
        repository.extinguishLight(assistantId, id)
    }

    // ================= 信件 =================

    private fun lettersOp(action: suspend () -> Unit): String = runBlocking {
        runCatching {
            action()
            buildState("postoffice")
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun addLetter(text: String, scheduledAt: Long): String = lettersOp {
        repository.addLetter(assistantId, "user", text, scheduledAt.takeIf { it > 0L })?.let {
            notifyUser("写了一封预约信")
            me.rerere.rikkahub.service.WorldLetterDeliveryWorker.schedule(context.applicationContext, it.id, it.scheduledAt)
        }
    }

    @JavascriptInterface
    fun openLetter(id: Long): String = lettersOp {
        repository.openLetter(assistantId, id)
    }

    // ================= 花园 =================

    private fun gardenOp(action: suspend () -> Unit): String = runBlocking {
        runCatching {
            action()
            buildState("garden")
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun sow(slot: Int, sp: String): String = gardenOp {
        if (repository.sow(assistantId, slot, sp, "user")) notifyUser("在花园里种下了${WorldConstants.FLOWERS[sp] ?: sp}")
    }

    @JavascriptInterface
    fun water(): String = gardenOp {
        if (repository.water(assistantId, "user") != null) notifyUser("给花园的花浇了水")
    }

    @JavascriptInterface
    fun waterOne(plantId: Long): String = gardenOp {
        if (repository.waterPlant(assistantId, plantId, "user") != null) notifyUser("给花园的花浇了水")
    }

    @JavascriptInterface
    fun sweep(): String = gardenOp {
        if (repository.sweep(assistantId, "user")) notifyUser("在花园里除了虫除了草")
    }

    @JavascriptInterface
    fun replant(plantId: Long, sp: String): String = gardenOp {
        if (repository.replant(assistantId, plantId, sp, "user")) notifyUser("给一盆花换了新种(${WorldConstants.FLOWERS[sp] ?: sp})")
    }

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
    fun adopt(sp: String): String = petOp {
        val r = failReason(repository.adoptPet(assistantId, sp))
        if (r == null) notifyUser("领养了一只${WorldConstants.PET_KINDS[sp] ?: sp}")
        r
    }

    @JavascriptInterface
    fun rename(petId: Long, name: String): String = petOp {
        val r = failReason(repository.renamePet(assistantId, petId, name))
        if (r == null) notifyUser("给宠物改名叫「$name」")
        r
    }

    @JavascriptInterface
    fun feed(petId: Long): String = petOp {
        val r = failReason(repository.feedPet(assistantId, petId, "user"))
        if (r == null) notifyUser("给宠物喂了食")
        r
    }

    @JavascriptInterface
    fun play(petId: Long): String = petOp {
        val r = failReason(repository.playPet(assistantId, petId, "user"))
        if (r == null) notifyUser("陪宠物玩了一会儿")
        r
    }

    @JavascriptInterface
    fun groom(petId: Long): String = petOp {
        val r = failReason(repository.groomPet(assistantId, petId, "user"))
        if (r == null) notifyUser("给宠物梳了毛")
        r
    }

    @JavascriptInterface
    fun taCare(petId: Long): String = petOp { failReason(repository.taCare(assistantId, petId)) }

    @JavascriptInterface
    fun eggSignal(petId: Long): String = petOp {
        // 档案卡「暖一暖」按暖蛋(feed)计,与 actFeed 冷却同类
        val r = failReason(repository.bumpEggSignal(assistantId, petId, "user", WorldConstants.HATCH_ACT_FEED, WorldConstants.SIGNAL_FEED))
        if (r == null) notifyUser("给宠物蛋攒了孵化信号")
        r
    }

    @JavascriptInterface
    fun hatch(petId: Long): String = petOp {
        val r = failReason(repository.hatchEgg(assistantId, petId))
        if (r == null) notifyUser("帮宠物蛋破了壳")
        r
    }

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

    // ================= 音乐(复用一起听的网易云接口) =================

    /** 最近一次搜索结果:点播时按 id 找回歌曲 */
    private var lastSearch: List<NeteaseClient.Song> = emptyList()

    private fun musicStateJson(): MusicPageState {
        val st = music.state.value
        return MusicPageState(
            phase = st.phase.name,
            name = st.song?.name.orEmpty(),
            artist = st.song?.artist.orEmpty(),
            position = st.positionMs,
            duration = st.durationMs,
            songs = lastSearch.map {
                PageSong(id = it.id, name = it.name, artist = it.artist, duration = it.durationMs, fee = it.fee)
            },
        )
    }

    @JavascriptInterface
    fun musicSearch(keyword: String): String = runBlocking {
        runCatching {
            if (keyword.isBlank()) error("EMPTY_KEYWORD")
            val songs = music.search(keyword.trim(), 20)
            lastSearch = songs
            JsonInstant.encodeToString(musicStateJson())
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun musicPlay(id: Long): String = runBlocking {
        runCatching {
            val song = lastSearch.firstOrNull { it.id == id } ?: error("SONG_NOT_FOUND")
            music.playSong(song).getOrThrow()
            JsonInstant.encodeToString(musicStateJson())
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun musicToggle(): String = runBlocking {
        runCatching {
            music.togglePlayAwait()
            JsonInstant.encodeToString(musicStateJson())
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun musicNext(): String = runBlocking {
        runCatching {
            music.nextAwait()
            JsonInstant.encodeToString(musicStateJson())
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun musicPrev(): String = runBlocking {
        runCatching {
            music.prevAwait()
            JsonInstant.encodeToString(musicStateJson())
        }.getOrElse { err(it) }
    }

    @JavascriptInterface
    fun musicState(): String = runBlocking {
        runCatching { JsonInstant.encodeToString(musicStateJson()) }.getOrElse { err(it) }
    }
}
