package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.uuid.Uuid

/**
 * read_chat_history_detail：配合 search_chat_history 使用。
 * 检索结果只有摘要；用本工具按会话 ID（可选关键词过滤）读取完整消息内容，
 * 让模型能定位并引用历史对话的完整信息。
 */
internal fun buildSearchMessageDetailTool(
    conversationRepository: ConversationRepository,
    assistantId: Uuid,
): Tool = Tool(
    name = "read_chat_history_detail",
    description = "Read the FULL content of messages in a specific conversation (found via " +
        "search_chat_history). Optionally filter by a keyword. Use it when a search snippet " +
        "is not enough and you need the complete information.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("conversation_id", buildJsonObject {
                    put("type", "string")
                    put("description", "The conversation_id from search_chat_history results")
                })
                put("keyword", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional: only return messages containing this keyword")
                })
            },
            required = listOf("conversation_id")
        )
    },
    systemPrompt = { _, _ ->
        "配合 search_chat_history 使用：检索结果只有摘要，需要完整信息时，" +
            "用结果里的 conversation_id 调用 read_chat_history_detail 读取原文。"
    },
    execute = { args ->
        val obj = args as? JsonObject
        val conversationIdStr: String? = obj?.get("conversation_id")
            ?.let { part -> (part as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        val keyword: String? = obj?.get("keyword")
            ?.let { part -> (part as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotBlank() }
        val payload = if (conversationIdStr.isNullOrBlank()) {
            buildJsonObject { put("error", "MISSING_CONVERSATION_ID") }
        } else {
            runCatching {
                val conversationId = Uuid.parse(conversationIdStr)
                val conversation = conversationRepository.getConversationById(conversationId)
                if (conversation == null) {
                    buildJsonObject { put("error", "CONVERSATION_NOT_FOUND") }
                } else {
                    val messages = conversation.currentMessages.asSequence()
                        .map { msg ->
                            val text = msg.parts.filterIsInstance<UIMessagePart.Text>()
                                .joinToString("\n") { it.text }.trim()
                            Triple(msg.role.name, text, msg.createdAt)
                        }
                        .filter { it.second.isNotBlank() }
                        .filter { keyword == null || it.second.contains(keyword) }
                        .toList()
                    val body = StringBuilder()
                    var count = 0
                    for ((role, text, time) in messages) {
                        val block = "[$role ${java.time.LocalDateTime.ofInstant(time, java.time.ZoneId.systemDefault())}]\n$text\n"
                        if (body.length + block.length > 6000) {
                            body.append("...(内容过长已截断)\n")
                            break
                        }
                        body.append(block)
                        count++
                    }
                    buildJsonObject {
                        put("conversation_title", conversation.title)
                        put("matched_total", messages.size)
                        put("returned", count)
                        put("messages", body.toString())
                    }
                }
            }.getOrElse {
                buildJsonObject { put("error", "READ_FAILED"); put("message", it.message ?: "") }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
