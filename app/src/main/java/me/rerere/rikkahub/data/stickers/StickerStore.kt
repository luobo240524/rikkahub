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
        _stickers.value = runCatching {
            if (indexFile.exists()) {
                json.decodeFromString<List<Sticker>>(indexFile.readText())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }
    }

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
            saveIndex(list + sticker)
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
            )
            saveIndex(list + sticker)
            sticker
        }.getOrNull()
    }

    fun remove(id: String) {
        val sticker = list().firstOrNull { it.id == id } ?: return
        File(dir, sticker.fileName).delete()
        saveIndex(list.filterNot { it.id == id })
    }

    fun get(id: String): Sticker? = list().firstOrNull { it.id == id }

    /** 贴纸图片的本地 file:// URI，用于消息渲染与发送 */
    fun fileUrl(sticker: Sticker): String {
        val target = File(dir, sticker.fileName)
        return Uri.fromFile(target).toString()
    }
}
