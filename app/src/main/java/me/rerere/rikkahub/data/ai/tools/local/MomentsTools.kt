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
        "after finishing a task. Text only, optionally with a mood emoji tag.",
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
            },
            required = listOf("content")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 moments_post 往「朋友圈」发帖。语气自然一点，像真人发动态；" +
            "TA让你发什么就发什么，不要替TA编造经历；发完简短确认即可。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val content = argsObj?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(2000)
        val mood = argsObj?.get("mood")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull?.take(8)
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
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", true)
                put("content", content)
                mood?.let { put("mood", it) }
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
        }
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
