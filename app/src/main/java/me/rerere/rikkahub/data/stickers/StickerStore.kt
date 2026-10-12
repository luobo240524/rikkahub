package me.rerere.rikkahub.data.stickers

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable
data class Sticker(
    val id: String,
    val fileName: String,
    val description: String = "",
    val pinned: Boolean = false, // 置顶：助手注入时优先
    val useCount: Long = 0, // 使用次数（用户点击+助手发送）
    val sourceUrl: String? = null, // 直链导入的来源 URL（用于导入去重）
)

/**
 * 贴纸库：管理用户导入的表情包（相册导入 / 图片直链下载）。
 * 图片统一复制到应用私有目录 stickers/ 下，索引起保存在 stickers/index.json。
 * 助手通过 send_sticker 工具从这里选图发送。
 */
class StickerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val dir: File get() = File(context.filesDir, "stickers").apply { mkdirs() }
    private val indexFile: File get() = File(dir, "index.json")

    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickers: StateFlow<List<Sticker>> = _stickers.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val loaded = runCatching {
            if (indexFile.exists()) {
                json.decodeFromString<List<Sticker>>(indexFile.readText())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }
        // 校验：剔除图片文件已丢失的贴纸（避免网格渲染空白累积），并记录诊断日志
        val valid = loaded.filter { File(dir, it.fileName).exists() }
        val missing = loaded.size - valid.size
        if (missing > 0) {
            runCatching {
                me.rerere.common.android.Logging.log(
                    "StickerStore",
                    "reload: $missing sticker image file(s) missing, removed from index. " +
                        loaded.filter { !File(dir, it.fileName).exists() }
                            .joinToString(";") { it.fileName }
                )
            }
            saveIndex(valid)
        }
        _stickers.value = valid
    }

    fun list(): List<Sticker> = _stickers.value

    private fun saveIndex(list: List<Sticker>) {
        indexFile.writeText(json.encodeToString(list))
        _stickers.value = list
    }

    /** 从相册 URI 导入贴纸 */
    suspend fun addFromUri(uri: Uri, description: String): Sticker? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = when (context.contentResolver.getType(uri)) {
                "image/gif" -> "gif"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val fileName = "${UUID.randomUUID()}.$ext"
            val target = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            val sticker = Sticker(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                description = description.trim(),
            )
            saveIndex(_stickers.value + sticker)
            sticker
        }.getOrNull()
    }

    /** 从图片直链下载导入贴纸 */
    suspend fun addFromUrl(url: String, description: String): Sticker? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@runCatching null
            }
            val contentType = conn.contentType ?: "image/jpeg"
            val ext = when {
                contentType.contains("gif") -> "gif"
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val fileName = "${UUID.randomUUID()}.$ext"
            val target = File(dir, fileName)
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            val sticker = Sticker(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                description = description.trim(),
                sourceUrl = url,
            )
            saveIndex(_stickers.value + sticker)
            sticker
        }.getOrNull()
    }

    fun remove(id: String) {
        val sticker = _stickers.value.firstOrNull { it.id == id } ?: return
        File(dir, sticker.fileName).delete()
        saveIndex(_stickers.value.filterNot { it.id == id })
    }

    /** 批量删除 */
    fun removeAll(ids: Collection<String>) {
        val idSet = ids.toSet()
        val removed = _stickers.value.filter { it.id in idSet }
        removed.forEach { File(dir, it.fileName).delete() }
        saveIndex(_stickers.value.filterNot { it.id in idSet })
    }

    /** 按文件名反查贴纸（用于折叠历史中的贴纸工具调用） */
    fun getByFileName(fileName: String): Sticker? = list().firstOrNull { it.fileName == fileName }

    /** 按来源直链查重 */
    fun getBySourceUrl(url: String): Sticker? = list().firstOrNull { it.sourceUrl == url }

    fun get(id: String): Sticker? = list().firstOrNull { it.id == id }

    /** 使用次数 +1（用户点击或助手发送时） */
    fun incrementUse(id: String) {
        val cur = _stickers.value
        val target = cur.firstOrNull { it.id == id } ?: return
        saveIndex(cur.map { if (it.id == id) it.copy(useCount = it.useCount + 1) else it })
    }

    /** 设置/取消置顶 */
    fun setPinned(id: String, pinned: Boolean) {
        val cur = _stickers.value
        val target = cur.firstOrNull { it.id == id } ?: return
        saveIndex(cur.map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    /**
     * 工具注入用的排序列表：置顶优先 → 导入顺序。
     * 注意：绝不能按使用频率排序——每次点击都会改变系统提示里的贴纸顺序，
     * 导致 DeepSeek 等前缀缓存完全失效（顺序必须稳定）。
     */
    fun sortedForTool(): List<Sticker> = _stickers.value.sortedByDescending { it.pinned }

    /** 助手可见的贴纸（短编号 s1..sN）：limit<=0 表示全部 */
    fun visibleForTool(limit: Int): List<Pair<String, Sticker>> {
        val sorted = sortedForTool()
        val capped = if (limit > 0) sorted.take(limit) else sorted
        return capped.mapIndexed { i, s -> "s${i + 1}" to s }
    }

    /** 按短编号解析贴纸（s12 = 可见列表第 12 张） */
    fun resolveShortId(shortId: String, limit: Int): Sticker? {
        val idx = shortId.removePrefix("s").toIntOrNull() ?: return null
        if (idx < 1) return null
        val visible = visibleForTool(limit)
        return visible.getOrNull(idx - 1)?.second
    }

    /** 贴纸图片的本地 file:// URI，用于消息渲染与发送 */
    fun fileUrl(sticker: Sticker): String {
        val target = File(dir, sticker.fileName)
        return Uri.fromFile(target).toString()
    }
}
