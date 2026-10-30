package me.rerere.rikkahub.data.db

import android.content.Context
import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 启动数据库自修复。
 *
 * 背景：聊天内流式生图（gpt-image-2 等）历史上会把整张图的 base64 直接拼进
 * message_node.messages 的 Image part（裸 base64，无 data: 前缀），绕过了落库
 * 清理（sanitize 只认 data: 前缀），导致数 MB 的巨型行被持久化。之后每次打开
 * 该对话都会在渲染/复制该字符串时把主线程拖死（ANR），且重装不清数据无法自愈。
 *
 * 修复策略：启动最早期（UI 加载前）扫描超长 message_node 行，把其中超长的
 * 内嵌图片（含裸 base64 与 data: URI）替换为占位文本。扫描无残留后置位标记，
 * 不再重复扫描；若清理过则下次启动再跑一轮以确认收敛。
 */
object DatabaseRepair {
    private const val TAG = "DatabaseRepair"
    private const val PREFS = "database_repair"
    private const val FLAG_DONE = "giant_base64_v1_done"

    /** 超过 200KB 的 message_node 行视为疑似污染（正常文本节点远小于此） */
    private const val GIANT_ROW_CHARS = 200L * 1024L

    /** 超过 100KB 的图片 url 视为内嵌 base64（裸 base64 与 data: URI 都算） */
    private const val GIANT_URL_CHARS = 100_000

    fun repairGiantBase64(context: Context, database: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG_DONE, false)) return
        val db = database.openHelper.writableDatabase
        var cleaned = 0
        var scanned = 0
        db.query(
            "SELECT id, messages FROM message_node WHERE length(messages) > ?",
            arrayOf(GIANT_ROW_CHARS.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                scanned++
                val id = cursor.getString(0)
                val json = cursor.getString(1)
                runCatching {
                    val messages = JsonInstant.decodeFromString<List<UIMessage>>(json)
                    var changed = false
                    val cleanedMessages = messages.map { msg -> stripGiantImages(msg) { changed = true } }
                    if (changed) {
                        db.execSQL(
                            "UPDATE message_node SET messages = ? WHERE id = ?",
                            arrayOf(JsonInstant.encodeToString(cleanedMessages), id)
                        )
                        cleaned++
                    }
                }.onFailure { e ->
                    // JSON 解析失败（数据损坏）：该节点无法正常渲染，重置为占位消息兜底，
                    // 避免加载该对话时反复异常/卡死
                    Log.e(TAG, "repair: decode failed for node $id, reset node", e)
                    val placeholder = UIMessage.assistant("[此节点数据损坏，已由启动修复重置]")
                    db.execSQL(
                        "UPDATE message_node SET messages = ? WHERE id = ?",
                        arrayOf(JsonInstant.encodeToString(listOf(placeholder)), id)
                    )
                    cleaned++
                }
            }
        }
        Log.i(TAG, "repairGiantBase64: scanned=$scanned cleaned=$cleaned")
        // 无残留才算完成；清理过则下轮启动再扫一次确认收敛
        prefs.edit().putBoolean(FLAG_DONE, cleaned == 0).apply()
    }

    private fun stripGiantImages(message: UIMessage, onChanged: () -> Unit): UIMessage {
        return message.copy(parts = message.parts.map { part -> stripGiantPart(part, onChanged) })
    }

    private fun stripGiantPart(part: UIMessagePart, onChanged: () -> Unit): UIMessagePart {
        return when (part) {
            is UIMessagePart.Image -> {
                if (part.url.length > GIANT_URL_CHARS) {
                    onChanged()
                    UIMessagePart.Text("[内嵌图片过大，已由启动修复清理]")
                } else {
                    part
                }
            }
            is UIMessagePart.Tool -> {
                var changed = false
                val newOutput = part.output.map { o -> stripGiantPart(o) { changed = true } }
                if (changed) {
                    onChanged()
                    part.copy(output = newOutput)
                } else {
                    part
                }
            }
            else -> part
        }
    }
}
