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
 * query_wake_records：助手可查询唤醒设置记录。
 * token 优化：默认只返回进行中任务(PENDING/RETRYING)——历史记录(FIRED/CANCELLED/FAILED)
 * 只会随唤醒次数越积越多，全量返回让每次查询的 token 线性膨胀。
 * 历史按需显式查(status=fired/cancelled/failed)，且只回最近 20 条；note 截断防长文本。
 */
private const val WAKE_HISTORY_MAX = 20
private const val WAKE_NOTE_MAX = 80

internal fun buildWakeRecordsTool(
    wakeRegistry: WakeRegistry,
    conversationId: kotlin.uuid.Uuid,
): Tool = Tool(
    name = "query_wake_records",
    description = "Query your wake/scheduled-task records. By default returns only ACTIVE tasks " +
        "(pending/retrying) - this is what you almost always need. History (fired/cancelled/failed) " +
        "is returned only when explicitly requested via the status filter, capped to the 20 most recent.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("status", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional filter: omit = active tasks only (default, cheap). " +
                        "\"pending\" = active; \"fired\" / \"cancelled\" / \"failed\" = history (max 20 recent)")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 query_wake_records 查看唤醒任务记录。" +
            "默认(不带参数)只返回进行中的任务,这已足够回答「我定了什么提醒/还挂着哪些任务」。" +
            "只有TA明确要看历史(比如「刚才那个提醒触发了吗」「之前的任务记录」)才传 status=fired/cancelled/failed 查最近20条。" +
            "TA询问定时提醒或唤醒设置时主动调用。"
    },
    execute = { args ->
        val statusFilter = (args as? JsonObject)?.get("status")
            ?.let { part -> (part as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            ?.lowercase()
            ?.takeIf { it in listOf("pending", "fired", "cancelled", "failed") }
        val all = wakeRegistry.list(conversationId = conversationId.toString())
        val active = all.filter { it.status == "PENDING" || it.status == "RETRYING" }
        val records = when (statusFilter) {
            null, "pending" -> active
            else -> all.filter { it.status == statusFilter.uppercase() }
                .take(WAKE_HISTORY_MAX)
        }
        val payload = buildJsonObject {
            put("total", records.size)
            if (statusFilter == null || statusFilter == "pending") {
                val historyCount = all.size - active.size
                if (historyCount > 0) {
                    put("history_hint",
                        "另有 $historyCount 条历史记录(已触发/已取消/已放弃)未列出;需要时用 status=fired/cancelled/failed 查询")
                }
            } else if (all.count { it.status == statusFilter.uppercase() } > WAKE_HISTORY_MAX) {
                put("truncated", "只返回最近 ${WAKE_HISTORY_MAX} 条")
            }
            put("records", kotlinx.serialization.json.buildJsonArray {
                records.forEach { r ->
                    add(buildJsonObject {
                        put("note", r.note.take(WAKE_NOTE_MAX))
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
