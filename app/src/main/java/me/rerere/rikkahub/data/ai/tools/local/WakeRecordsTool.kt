package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.WakeRegistry
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * query_wake_records：助手可查询唤醒设置记录
 * （正在生效 PENDING / 已经触发 FIRED / 已取消 CANCELLED）。
 */
internal fun buildWakeRecordsTool(
    wakeRegistry: WakeRegistry,
    conversationId: kotlin.uuid.Uuid,
): Tool = Tool(
    name = "query_wake_records",
    description = "Query your wake/scheduled-task records: which are pending, fired or cancelled. " +
        "Use it when the user asks about their scheduled reminders or wake settings.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("status", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional filter: all (default) / pending / fired / cancelled")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 query_wake_records 查看唤醒任务记录（进行中/已触发/已取消）。" +
            "用户询问定时提醒或唤醒设置时主动调用。"
    },
    execute = { args ->
        val statusFilter = (args as? JsonObject)?.get("status")
            ?.let { part -> (part as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            ?.lowercase()
            ?.takeIf { it in listOf("pending", "fired", "cancelled") }
        val records = wakeRegistry.list(conversationId = conversationId.toString())
            .filter { statusFilter == null || it.status == statusFilter.uppercase() }
        val payload = buildJsonObject {
            put("total", records.size)
            put("records", kotlinx.serialization.json.buildJsonArray {
                records.forEach { r ->
                    add(buildJsonObject {
                        put("note", r.note)
                        put("type", r.type)
                        put("status", r.status)
                        put("fire_at", LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(r.fireAt),
                            ZoneId.systemDefault()).toString())
                        put("created_at", LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(r.createdAt),
                            ZoneId.systemDefault()).toString())
                    })
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
