package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity
import me.rerere.rikkahub.data.db.entity.isDue

/**
 * 定时发布时间解析:支持 epoch 毫秒(纯数字≥12位)或本地时间
 * yyyy-MM-dd HH:mm[:ss] / yyyy-MM-dd'T'HH:mm[:ss] / yyyy-MM-dd(当天零点)。
 * 解析失败或时间已过 → null(立即生效)。
 */
internal fun parseScheduledAt(raw: String?, now: Long = System.currentTimeMillis()): Long? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (t.length >= 12 && t.all { it.isDigit() }) {
        return t.toLongOrNull()?.takeIf { it > now }
    }
    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd",
    )
    for (p in patterns) {
        runCatching {
            val ldt = java.time.LocalDateTime.parse(t, java.time.format.DateTimeFormatter.ofPattern(p))
            val millis = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (millis > now) return millis
        }
    }
    return null
}

/**
 * moments_post：助手往朋友圈发帖(一期纯文字 + emoji + 可选心情标签)。
 */
internal fun buildMomentsPostTool(
    momentsPostDAO: MomentsPostDAO,
    conversationId: kotlin.uuid.Uuid?,
): Tool = Tool(
    name = "moments_post",
    description = "Post a short text update to the in-app Moments feed (like a social media post). " +
        "Use it when the user asks you to share/post something, or when you want to share a thought " +
        "after finishing a task. Text only, optionally with a mood emoji tag. " +
        "Optionally schedule it: the post goes live at the given time.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Post content (plain text, keep it short and human)")
                })
                put("mood", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional mood tag as a single emoji, e.g. ☀️ 🌙")
                })
                put("scheduledAt", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional publish time, local format 'yyyy-MM-dd HH:mm'. " +
                        "Until then the post stays invisible to the user. Omit = post now.")
                })
            },
            required = listOf("content")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 moments_post 往「朋友圈」发帖。语气自然一点，像真人发动态；" +
            "TA让你发什么就发什么，不要替TA编造经历；发完简短确认即可。" +
            "要定时发(比如到点给TA送早安/生日祝福)就传 scheduledAt('yyyy-MM-dd HH:mm')," +
            "到点前TA看不到这条,到点自动出现;定时发帖内容也要像平时一样自然。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val content = argsObj?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(2000)
        val mood = argsObj?.get("mood")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull?.take(8)
        val scheduledAt = parseScheduledAt(argsObj?.get("scheduledAt")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull)
        val payload = if (content.isBlank()) {
            buildJsonObject { put("ok", false); put("error", "MISSING_CONTENT") }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    momentsPostDAO.upsert(
                        MomentsPostEntity(
                            author = "assistant",
                            conversationId = conversationId?.toString(),
                            content = content,
                            mood = mood,
                            createdAt = System.currentTimeMillis(),
                            scheduledAt = scheduledAt,
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", true)
                put("content", content)
                mood?.let { put("mood", it) }
                if (scheduledAt != null) {
                    put("scheduled", true)
                    put("dueAt", scheduledAt)
                }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * moments_read：读取朋友圈最近 N 条帖子,供助手回顾和接话。
 */
internal fun buildMomentsReadTool(
    momentsPostDAO: MomentsPostDAO,
): Tool = Tool(
    name = "moments_read",
    description = "Read recent posts from the in-app Moments feed (both user's and your posts). " +
        "Use it when the user asks what has been posted recently or wants you to react to moments.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max posts to read, default 20")
                })
            },
            required = listOf()
        )
    },
    execute = { args ->
        val limit = (args as? JsonObject)?.get("limit")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val posts = withContext(Dispatchers.IO) {
            runCatching { momentsPostDAO.recent(limit) }.getOrElse { emptyList() }
        }.filter { isDue(it.scheduledAt) } // 未到点的定时帖不出现
        val payload = buildJsonObject {
            put("total", posts.size)
            put("posts", buildJsonArray {
                for (p in posts) {
                    add(
                        buildJsonObject {
                            put("id", p.id)
                            put("author", p.author)
                            put("content", p.content)
                            p.mood?.let { put("mood", it) }
                            put("createdAt", p.createdAt)
                        }
                    )
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
