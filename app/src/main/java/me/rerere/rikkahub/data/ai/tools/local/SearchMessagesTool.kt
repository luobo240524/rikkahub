package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import kotlin.uuid.Uuid

/**
 * search_chat_history：助手在对话中检索当前助手所有会话（含分支）的历史消息文本，
 * 用于回忆早前对话的细节以完成任务。基于官方 FTS 引擎（jieba 分词 + 相关性排序）。
 */
internal fun buildSearchMessagesTool(
    messageFtsManager: MessageFtsManager,
    assistantId: Uuid,
): Tool = Tool(
    name = "search_chat_history",
    description = "Search the user's past conversations with you (this assistant only) by keyword. " +
        "Use it when the user refers to something discussed earlier or you need to recall previous details. " +
        "Returns the most relevant message snippets with their conversation titles.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Search keyword or short phrase (Chinese or English)")
                })
            },
            required = listOf("query")
        )
    },
    systemPrompt = { _, _ ->
        "你可以使用 search_chat_history 工具检索与用户的全部历史对话（仅限本助手）。" +
            "当用户提到之前聊过的内容、或你需要回忆早期细节来完成任务时，主动使用该工具。" +
            "查询词用简短的关键词或短语。检索结果只有摘要；" +
            "需要完整信息时，用结果里的 conversation_id 调用 read_chat_history_detail 读取原文。"
    },
    execute = { args ->
        val query: String? = (args as? JsonObject)?.get("query")
            ?.let { part -> (part as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        val payload = if (query.isNullOrBlank()) {
            buildJsonObject { put("error", "MISSING_QUERY") }
        } else {
            val results = runCatching {
                messageFtsManager.search(query, assistantId = assistantId.toString())
            }.getOrElse { emptyList() }
            buildJsonObject {
                put("total", results.size)
                putJsonArrayBody(results)
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayBody(
    results: List<me.rerere.rikkahub.data.db.fts.MessageSearchResult>,
) {
    put("results", buildJsonArray {
        results.take(10).forEach { r ->
            add(buildJsonObject {
                put("conversation_id", r.conversationId)
                put("conversation_title", r.title)
                put("snippet", r.snippet)
                put("time", java.time.LocalDateTime.ofInstant(r.updateAt, java.time.ZoneId.systemDefault()).toString())
            })
        }
    })
}
