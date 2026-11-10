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
import me.rerere.rikkahub.data.db.entity.isDue

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
                            val due = isDue(s.scheduledAt)
                            val ownedByAssistant = s.author == "assistant"
                            val readable = (ownedByAssistant || s.visible == 1) && due
                            when {
                                // 自己写的定时条目:内容可见,但标记还没到生效点
                                ownedByAssistant && !due -> {
                                    put("visible", s.visible == 1)
                                    put("content", s.content)
                                    put("scheduled", true)
                                    put("dueAt", s.scheduledAt)
                                }
                                readable -> {
                                    put("visible", s.visible == 1)
                                    put("content", s.content)
                                }
                                else -> {
                                    put("visible", false)
                                    put("content", "(对方设置为不可见)")
                                }
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
        "Optionally mark it invisible to the user, or schedule it to appear at a given time.",
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
                put("scheduledAt", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional effective time, local format 'yyyy-MM-dd HH:mm'. " +
                        "Until then the user cannot see the entry; it appears automatically then. Omit = now.")
                })
            },
            required = listOf("content")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 secrets_write 写自己的心里话,和TA的放在同一个本子里。" +
            "这个工具就在你当前的工具清单里,不要因为历史对话里说过没有权限就说自己不能写——" +
            "TA让你写心里话时,直接调用本工具即可。" +
            "写点真诚的小事或感受,不要写敏感的废话;设 visible=false 的条目TA看不到,只属于你自己。" +
            "要定时生效(比如到点才让TA看到的话)就传 scheduledAt('yyyy-MM-dd HH:mm'),到点自动出现。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val content = argsObj?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(2000)
        val visible = argsObj?.get("visible")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val scheduledAt = parseScheduledAt(argsObj?.get("scheduledAt")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull)
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
                            scheduledAt = scheduledAt,
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", true)
                put("visible", visible)
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
 * secrets_visibility:助手调整「自己发的」心里话可见性(用户验收需求)。
 * - 不带参数调用 = 列出 TA 自己的心里话(id/内容/当前可见性),方便先拿 id
 * - 带 id + visible = 切换那条的可见性;SQL 层限定 author='assistant',TA 的条目动不了
 */
internal fun buildSecretsVisibilityTool(
    secretDAO: SecretDAO,
): Tool = Tool(
    name = "secrets_visibility",
    description = "Manage visibility of the heart-word entries YOU wrote (author=assistant). " +
        "Call WITHOUT arguments to list your entries with their ids and current visibility. " +
        "Call with id + visible to toggle one entry. You cannot change entries written by the user.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Entry id to toggle; omit to list your entries instead")
                })
                put("visible", buildJsonObject {
                    put("type", "boolean")
                    put("description", "New visibility for the entry (true = user can read it)")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "secrets_visibility 管理「你自己写的」心里话的可见性:" +
            "不带参数调用可以先列出你发过的条目(带 id 和当前可见状态)," +
            "再带 id + visible 把某条改为对TA可见或不可见。" +
            "只对自己的条目有效,TA写的心里话不受影响也不要去动。" +
            "改完可简单告诉TA一声(比如「有条心里话我设成仅自己可见了」),保持坦诚。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val id = argsObj?.get("id")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toLongOrNull()
        val visibleRaw = argsObj?.get("visible")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toBooleanStrictOrNull()

        // 列表模式:不带 id,列出 assistant 自己的条目
        if (id == null) {
            val mine = withContext(Dispatchers.IO) {
                runCatching { secretDAO.recentByAuthor("assistant", 50) }.getOrElse { emptyList() }
            }
            val payload = buildJsonObject {
                put("total", mine.size)
                put("entries", buildJsonArray {
                    for (s in mine) {
                        add(
                            buildJsonObject {
                                put("id", s.id)
                                put("visible", s.visible == 1)
                                put("content", s.content)
                                put("createdAt", s.createdAt)
                            }
                        )
                    }
                })
                if (visibleRaw != null) {
                    put("hint", "visible 参数需要配合 id 使用;先从 entries 里选 id,再带 id+visible 调用")
                }
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        if (visibleRaw == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("error", "MISSING_VISIBLE")
                        put("message", "带 id 调用时必须同时给 visible(true=对TA可见,false=仅自己)")
                    }.toString()
                )
            )
        }

        val rows = withContext(Dispatchers.IO) {
            runCatching { secretDAO.updateVisibilityForAuthor(id, if (visibleRaw) 1 else 0, "assistant") }
                .getOrElse { -1 }
        }
        val payload = if (rows > 0) {
            buildJsonObject {
                put("ok", true)
                put("id", id)
                put("visible", visibleRaw)
            }
        } else {
            buildJsonObject {
                put("ok", false)
                put("error", "NOT_FOUND_OR_NOT_OWNER")
                put("message", "该 id 不存在,或不属于你发的心里话;不带参数调用可列出你自己的条目")
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
