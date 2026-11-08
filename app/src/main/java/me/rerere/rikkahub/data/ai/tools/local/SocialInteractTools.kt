package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity

/**
 * 通用互动工具构建器:朋友圈(moments_interact / target=moment)与
 * 心情打卡(mood_interact / target=checkin)共用同一套 like/comment 落库逻辑。
 * 助手产生的互动 is_read=0,构成用户侧红点。
 */
internal fun buildSocialInteractTool(
    name: String,
    targetType: String,
    idParamName: String,
    targetLabel: String,
    socialInteractionDAO: SocialInteractionDAO,
    targetExists: suspend (Long) -> Boolean,
): Tool = Tool(
    name = name,
    description = "Like or comment on a $targetLabel in the in-app social feed (the user's or yours). " +
        "Use it when you want to react to what the user posted, e.g. after being notified about a new post.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put(idParamName, buildJsonObject {
                    put("type", "integer")
                    put("description", "Id of the $targetLabel to interact with (from the read tool)")
                })
                put("action", buildJsonObject {
                    put("type", "string")
                    put("description", "Either \"like\" or \"comment\"")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Comment text; required when action is \"comment\"")
                })
            },
            required = listOf(idParamName, "action")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 $name 给「$targetLabel」点赞或评论:action=like 点赞,action=comment 评论(需给 content)。" +
            "互动要自然克制,像熟人一样;同一内容不要重复点赞;对方发布了新内容时,这是你表达关心的好机会。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val targetId = argsObj?.get(idParamName)
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull?.toLongOrNull()
        val action = argsObj?.get("action")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull
        val content = argsObj?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull?.take(500)

        val payload = when {
            targetId == null -> buildJsonObject { put("ok", false); put("error", "MISSING_${idParamName.uppercase()}") }
            action != "like" && action != "comment" ->
                buildJsonObject { put("ok", false); put("error", "INVALID_ACTION") }
            action == "comment" && content.isNullOrBlank() ->
                buildJsonObject { put("ok", false); put("error", "MISSING_CONTENT") }
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    if (!targetExists(targetId)) {
                        return@runCatching buildJsonObject { put("ok", false); put("error", "TARGET_NOT_FOUND") }
                    }
                    if (action == "like" && socialInteractionDAO.countLike(targetType, targetId, "assistant") > 0) {
                        return@runCatching buildJsonObject { put("ok", false); put("error", "ALREADY_LIKED") }
                    }
                    socialInteractionDAO.insert(
                        SocialInteractionEntity(
                            targetType = targetType,
                            targetId = targetId,
                            actor = "assistant",
                            type = action,
                            content = content,
                            createdAt = System.currentTimeMillis(),
                            isRead = 0,
                        )
                    )
                    buildJsonObject { put("ok", true); put("action", action) }
                }.getOrElse {
                    buildJsonObject { put("ok", false); put("error", "DB_ERROR") }
                }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
