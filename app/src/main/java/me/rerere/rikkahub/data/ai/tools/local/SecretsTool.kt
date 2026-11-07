package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.SecretDAO

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
        "secrets_read 读到的是用户的心里话,非常私密。对可见的内容要温柔、认真回应,绝不嘲笑;" +
            "显示为「对方设置为不可见」的条目不要猜、不要追问,尊重对方的边界。"
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
                            if (s.visible == 1) {
                                put("visible", true)
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
