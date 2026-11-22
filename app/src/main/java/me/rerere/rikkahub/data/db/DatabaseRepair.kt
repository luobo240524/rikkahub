package me.rerere.rikkahub.data.db

import android.content.Context
import android.util.Log
import me.rerere.ai.core.MessageRole
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
    private const val FLAG_TOOL_REASONING_DONE = "tool_call_reasoning_v1_done"

    /** 超过 200KB 的 message_node 行视为疑似污染（正常文本节点远小于此） */
    private const val GIANT_ROW_CHARS = 200L * 1024L

    /** 超过 100KB 的图片 url 视为内嵌 base64（裸 base64 与 data: URI 都算） */
    private const val GIANT_URL_CHARS = 100_000

    /** @return Pair(扫描行数, 清理行数) */
    fun repairGiantBase64(context: Context, database: AppDatabase, force: Boolean = false): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean(FLAG_DONE, false)) return Pair(0, 0)
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
        Log.i(TAG, "repairGiantBase64: scanned=$scanned cleaned=$cleaned force=$force")
        // 无残留才算完成；清理过则下轮启动再扫一次确认收敛；force 模式不置位
        if (!force) prefs.edit().putBoolean(FLAG_DONE, cleaned == 0).apply()
        return Pair(scanned, cleaned)
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

    /**
     * 工具调用消息的思考链污染修复（r329，一次性）。
     *
     * 背景：r315~326 的「思考链静默化」把助手历史思考里含机制关键词的句子整句删除并
     * 持久化落库；而 ChatCompletions 对带已执行工具调用的历史助手消息**无条件回传**
     * reasoning_content（智谱 thinking 校验要求，不受「历史思考回传」开关门控，
     * 见 ChatCompletionsAPI.addAssistantMessages）。碎片/被掏空的思考每轮回灌给模型，
     * 模型强上下文模仿到干脆不写思考。r328 已删除 scrubber 停止继续破坏，但落库的
     * 碎片还在，回灌通道仍然开着。
     *
     * 修复策略：把所有「带已执行 Tool part 的助手消息」的 Reasoning part 整体清掉
     * （与回传行为精确对齐：只有 isExecuted 的 Tool 才构成工具组、才触发无条件回传）。
     * 纯文本消息的思考一字不动（其回传本就受开关门控）。清掉后这类消息回传
     * reasoning_content=""（非 DeepSeek）或不带该字段（DeepSeek），都是合法形态。
     *
     * 一次性：扫描成功跑完一轮就置位（污染源已在 r328 移除，新数据不会再被污染）；
     * 中途失败不置位，下次启动重试。
     *
     * @return Pair(扫描的节点行数, 清理的节点行数)
     */
    fun repairToolCallReasoning(context: Context, database: AppDatabase): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG_TOOL_REASONING_DONE, false)) return Pair(0, 0)
        val db = database.openHelper.writableDatabase
        var scanned = 0
        var cleaned = 0
        // SQL 预筛:只可能污染含 Tool part 的行(其多态判别符精确为 "tool";
        // tool_call/tool_result/server_tool 等其他类型带引号后形如 "tool_call",不匹配 '%"tool"%'),
        // 纯文本节点不解码
        db.query(
            "SELECT id, messages FROM message_node WHERE messages LIKE '%\"tool\"%'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                scanned++
                val id = cursor.getString(0)
                val json = cursor.getString(1)
                runCatching {
                    val messages = JsonInstant.decodeFromString<List<UIMessage>>(json)
                    var changed = false
                    val cleanedMessages = messages.map { msg ->
                        val shouldStrip = msg.role == MessageRole.ASSISTANT &&
                            msg.parts.any { it is UIMessagePart.Tool && it.isExecuted } &&
                            msg.parts.any { it is UIMessagePart.Reasoning }
                        if (!shouldStrip) return@map msg
                        changed = true
                        msg.copy(parts = msg.parts.filterNot { it is UIMessagePart.Reasoning })
                    }
                    if (changed) {
                        db.execSQL(
                            "UPDATE message_node SET messages = ? WHERE id = ?",
                            arrayOf(JsonInstant.encodeToString(cleanedMessages), id)
                        )
                        cleaned++
                    }
                }.onFailure { e ->
                    // JSON 解析失败:与本修复无关的数据损坏,不动它,只记日志
                    Log.e(TAG, "repairToolCallReasoning: decode failed for node $id, skip", e)
                }
            }
        }
        Log.i(TAG, "repairToolCallReasoning: scanned=$scanned cleaned=$cleaned")
        prefs.edit().putBoolean(FLAG_TOOL_REASONING_DONE, true).apply()
        return Pair(scanned, cleaned)
    }
}
