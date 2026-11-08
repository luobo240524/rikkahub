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
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.entity.SecretEntity

/**
 * secrets_read:读用户的心里话。visible=0 的条目只回"对方设置为不可见",绝不吐内容。
 */
internal fun buildSecretsReadTool(
    secretDAO: SecretDAO,
): Tool = Tool(
    name = "secrets_read",
    description = "Read the user's private heart-words (secrets) journal. Entries the user marked " +
        "as invisible to you will be masked. Use it only when the user asks about them or you were " +
        "notified about a new entry.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max entries to read, default 20")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "secrets_read 读到的是TA的心里话,非常私密。对可见的内容要温柔、认真回应,绝不嘲笑;" +
            "显示为「对方设置为不可见」的条目不要猜、不要追问,尊重对方的边界。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val limit = (args as? JsonObject)?.get("limit")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val secrets = withContext(Dispatchers.IO) {
            runCatching { secretDAO.recent(limit) }.getOrElse { emptyList() }
        }
        val payload = buildJsonObject {
            put("total", secrets.size)
            put("secrets", buildJsonArray {
                for (s in secrets) {
                    add(
                        buildJsonObject {
                            put("id", s.id)
                            put("author", s.author)
                            // 自己写的内容对自己始终可见;对方写的按 visible 开关
                            val readable = s.author == "assistant" || s.visible == 1
                            if (readable) {
                                put("visible", s.visible == 1)
                                put("content", s.content)
                            } else {
                                put("visible", false)
                                put("content", "(对方设置为不可见)")
                            }
                            put("createdAt", s.createdAt)
                        }
                    )
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * secrets_write:助手也写心里话(用户验收需求)。
 * visible 语义同用户侧:对对方(小渝)是否可见;不可见的条目对方界面上只显示占位。
 */
internal fun buildSecretsWriteTool(
    secretDAO: SecretDAO,
): Tool = Tool(
    name = "secrets_write",
    description = "Write a private heart-word (secret) to the shared secrets journal. " +
        "Use it when you want to jot down a private thought about the day or about the user. " +
        "Optionally mark it invisible to the user.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "The secret content (plain text, keep it short and sincere)")
                })
                put("visible", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether the user can read this entry, default true")
                })
            },
            required = listOf("content")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 secrets_write 写自己的心里话,和TA的放在同一个本子里。" +
            "写点真诚的小事或感受,不要写敏感的废话;设 visible=false 的条目TA看不到,只属于你自己。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val content = argsObj?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(2000)
        val visible = argsObj?.get("visible")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val payload = if (content.isBlank()) {
            buildJsonObject { put("ok", false); put("error", "MISSING_CONTENT") }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    secretDAO.insert(
                        SecretEntity(
                            content = content,
                            visible = if (visible) 1 else 0,
                            author = "assistant",
                            conversationId = null,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", true)
                put("visible", visible)
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
