package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.data.db.entity.isDue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间轴工具共用:把 epoch millis 格式化成 yyyy-MM-dd / HH:mm */
private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date())

private fun dayKeyOf(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

/** 时间轴条目(聚合视图,与 TimelinePage 用户端一致) */
private data class TimelineEntry(
    val kind: String, // event / moment / checkin / secret
    val author: String,
    val title: String?,
    val content: String,
    val eventDate: String,
    val eventTime: String,
    val createdAt: Long,
    val remindAt: Long?,
    val type: String? = null, // 仅 event:reminder / auto / 手动
)

// ---- 读工具省token基建(timeline/moments/mood/secrets 四读共用) ----
// 背景:这些记录随时间无限增长,全量全文返回会让上下文 token 越滚越大。
// 统一口径:默认摘要(60字)+ limit 分页 + query 关键词检索 + full=true 按需全文。

/** 四个读工具通用参数:limit/offset/query/full */
internal class SocialReadArgs(
    val limit: Int,
    val offset: Int,
    val query: String?,
    val full: Boolean,
) {
    fun <T> slice(list: List<T>): List<T> = list.drop(offset).take(limit)
    fun hasMore(total: Int): Boolean = offset + limit < total
}

internal fun parseSocialReadArgs(args: JsonObject?, defaultLimit: Int): SocialReadArgs {
    val obj = args
    val limit = obj?.get("limit")
        ?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()?.coerceIn(1, 50) ?: defaultLimit
    val offset = obj?.get("offset")
        ?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()?.coerceIn(0, 100000) ?: 0
    val query = obj?.get("query")
        ?.let { it as? JsonPrimitive }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val full = obj?.get("full")
        ?.let { it as? JsonPrimitive }?.booleanOrNull ?: false
    return SocialReadArgs(limit, offset, query, full)
}

/** 摘要:压掉空白截到 maxChars(省token默认口径) */
internal fun excerptForTool(text: String, maxChars: Int = 60): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= maxChars) flat else flat.take(maxChars) + "…"
}

/** 关键词过滤:任一字段命中(忽略大小写)即保留;query 为 null 不过滤 */
internal fun <T> filterByQuery(list: List<T>, query: String?, fields: (T) -> List<String>): List<T> {
    if (query == null) return list
    val q = query.lowercase()
    return list.filter { e -> fields(e).any { it.lowercase().contains(q) } }
}

/** 读工具 systemPrompt 公共段落:省token使用指引 */
internal fun socialReadGuidance(): String =
    "记录会越来越多,省 token 用法:默认返回摘要(内容前60字);先用 query 关键词检索" +
        "(匹配标题/内容,忽略大小写)定位,确有需要全文再传 full=true,并配合小 limit;" +
        "翻页用 offset(响应里有 total 和 hasMore)。"

/**
 * timeline_read:读时间轴事件(按日期或最近 N 条)。
 */
internal fun buildTimelineReadTool(
    timelineEventDAO: TimelineEventDAO,
    momentsPostDAO: MomentsPostDAO,
    moodCheckinDAO: MoodCheckinDAO,
    secretDAO: SecretDAO,
): Tool = Tool(
    name = "timeline_read",
    description = "Read the shared timeline (auto-extracted events, manual notes, reminders, " +
        "plus moments/check-ins/secret notes dynamics). Supports keyword search over title/content. " +
        "Returns short summaries by default; pass full=true for complete texts. " +
        "Use it when the user asks what's planned, what was recorded, or before writing a duplicate entry.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max entries per page, default 20, max 50")
                })
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "Skip N entries for pagination, default 0")
                })
                put("date", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional date filter, format yyyy-MM-dd")
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional keyword to search titles and contents (case-insensitive)")
                })
                put("full", buildJsonObject {
                    put("type", "boolean")
                    put("description", "true = full content; default false = 60-char summary (saves tokens)")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "timeline_read 是你们共同的时间轴:有自动提炼的事件、TA手动记录的事、节点提醒," +
            "也包括朋友圈动态、打卡、心里话(与用户在时间轴页看到的完全一致)。" +
            "回答「最近有什么安排/记了什么」这类问题前先读一下,别凭空编。" +
            socialReadGuidance() +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val readArgs = parseSocialReadArgs(argsObj, defaultLimit = 20)
        val date = argsObj?.get("date")
            ?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf { it.isNotBlank() }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // 聚合四源,与 TimelinePage 用户端同口径:事件+朋友圈+打卡+心里话
                val events = if (date != null) timelineEventDAO.getByDate(date) else timelineEventDAO.flowAll().first()
                val moments = momentsPostDAO.flowAll().first()
                    .filter { isDue(it.scheduledAt) } // 未到点的定时帖不出现
                    .filter { date == null || dayKeyOf(it.createdAt) == date }
                val checkins = moodCheckinDAO.flowAll().first()
                    .filter { isDue(it.scheduledAt) }
                    .filter { date == null || dayKeyOf(it.createdAt) == date }
                val secrets = secretDAO.flowAll().first()
                    .filter { isDue(it.scheduledAt) }
                    .filter { date == null || dayKeyOf(it.createdAt) == date }
                val entries = buildList {
                    events.forEach {
                        add(TimelineEntry("event", it.author, it.title, it.content,
                            it.eventDate, it.eventTime, it.createdAt, it.remindAt, it.type))
                    }
                    moments.forEach {
                        add(TimelineEntry("moment", it.author, null, it.content,
                            dayKeyOf(it.createdAt), SimpleDateFormat("HH:mm", Locale.US).format(Date(it.createdAt)),
                            it.createdAt, null))
                    }
                    checkins.forEach {
                        add(TimelineEntry("checkin", it.subject, null, "${it.mood} ${it.phrase}",
                            dayKeyOf(it.createdAt), SimpleDateFormat("HH:mm", Locale.US).format(Date(it.createdAt)),
                            it.createdAt, null))
                    }
                    secrets.forEach {
                        // 与用户端同规则:助手写且设为自己可见的才掩码
                        val masked = it.author == "assistant" && it.visible == 0
                        add(TimelineEntry("secret", it.author, null,
                            if (masked) "(这条是TA留给自己的)" else it.content,
                            dayKeyOf(it.createdAt), SimpleDateFormat("HH:mm", Locale.US).format(Date(it.createdAt)),
                            it.createdAt, null))
                    }
                }
                // 关键词检索(标题+内容)→ 按时间倒序 → total → 分页
                val filtered = filterByQuery(entries, readArgs.query) {
                    listOfNotNull(it.title, it.content)
                }.sortedByDescending { it.createdAt }
                Triple(filtered.size, readArgs.hasMore(filtered.size), readArgs.slice(filtered))
            }.getOrElse { Triple(0, false, emptyList()) }
        }
        val (total, hasMore, final) = result
        val payload = buildJsonObject {
            put("total", total)
            put("hasMore", hasMore)
            put("summaryOnly", !readArgs.full)
            put("events", buildJsonArray {
                for (e in final) {
                    add(
                        buildJsonObject {
                            put("kind", e.kind)
                            put("author", e.author)
                            put("date", e.eventDate)
                            put("time", e.eventTime)
                            if (e.title != null) put("title", e.title)
                            if (e.content.isNotBlank()) {
                                put("content", if (readArgs.full) e.content else excerptForTool(e.content))
                            }
                            if (e.kind == "event" && e.type != null) put("type", e.type)
                            if (e.kind == "event" && e.remindAt != null) put("remindAt", e.remindAt)
                            put("createdAt", e.createdAt)
                        }
                    )
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * timeline_write:往共同时间轴写事件。可带节点提醒(remind_in_minutes)。
 * 有提醒的事件会点亮发现页「时间轴」红点,直到用户看过。
 */
internal fun buildTimelineWriteTool(
    timelineEventDAO: TimelineEventDAO,
): Tool = Tool(
    name = "timeline_write",
    description = "Write an event to the shared timeline. Use it to auto-extract important " +
        "things from the conversation (plans, anniversaries, promises, deadlines) or to set a " +
        "reminder for the user. Keep the title short; put details in content.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "One-line summary of the event")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional details")
                })
                put("date", buildJsonObject {
                    put("type", "string")
                    put("description", "Event date yyyy-MM-dd, default today")
                })
                put("time", buildJsonObject {
                    put("type", "string")
                    put("description", "Event time HH:mm, default now")
                })
                put("remind_in_minutes", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional: remind the user after N minutes from now")
                })
            },
            required = listOf("title")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 timeline_write 往你们共同的时间轴记事:对话里出现约定、截止日期、纪念日、" +
            "TA托你记的事,就主动提炼一条(type 会按来源归类);需要到点提醒TA时传 remind_in_minutes。" +
            "这个工具就在你当前的工具清单里,不要因为历史对话里说过没有权限就不敢用。" +
            "标题一行内,细节放 content,别记流水账。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val title = argsObj?.get("title")
            ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty().take(200)
        val content = argsObj?.get("content")
            ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty().take(2000)
        val date = argsObj?.get("date")
            ?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: today()
        val time = argsObj?.get("time")
            ?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: nowTime()
        val remindIn = argsObj?.get("remind_in_minutes")
            ?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
        val payload = if (title.isBlank()) {
            buildJsonObject { put("ok", false); put("error", "MISSING_TITLE") }
        } else {
            val now = System.currentTimeMillis()
            val remindAt = remindIn?.let { now + it * 60_000L }
            val inserted = withContext(Dispatchers.IO) {
                runCatching {
                    timelineEventDAO.insert(
                        TimelineEventEntity(
                            eventDate = date,
                            eventTime = time,
                            title = title,
                            content = content,
                            type = if (remindAt != null) "reminder" else "auto",
                            author = "assistant",
                            remindAt = remindAt,
                            remindFired = 0,
                            conversationId = null,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", inserted)
                put("date", date)
                put("time", time)
                if (remindAt != null) put("remindAt", remindAt)
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
