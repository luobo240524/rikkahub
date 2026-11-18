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
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity

/**
 * 通用互动工具构建器:朋友圈(moments_interact / target=moment)、
 * 心情打卡(mood_interact / target=checkin)、心里话(secrets_interact / target=secret)
 * 共用同一套 like/comment 落库逻辑。助手产生的互动 is_read=0,构成用户侧红点。
 *
 * 防「评论牛头不对马嘴」三道闸:
 * ① systemPrompt 强制先调对应 read 工具拿真实 ID,禁止凭记忆猜 ID;
 * ② 目标不存在时返回最近条目的 {id, 摘要} 候选列表,模型可当场改用正确 ID;
 * ③ 评论成功后回显目标内容摘要,模型发现回显与评论意图不符可立即用正确 ID 重评。
 */
internal fun buildSocialInteractTool(
    name: String,
    targetType: String,
    idParamName: String,
    targetLabel: String,
    socialInteractionDAO: SocialInteractionDAO,
    /** 取目标内容全文;null = 目标不存在 */
    targetContent: suspend (Long) -> String?,
    /** 最近若干条 {id, 摘要} 候选(已过滤未到点的定时内容),供 TARGET_NOT_FOUND 时纠错 */
    recentTargets: suspend () -> List<Pair<Long, String>>,
): Tool = Tool(
    name = name,
    description = "Like or comment on a $targetLabel in the in-app social feed (the user's or yours). " +
        "Use it when you want to react to what the user posted, e.g. after being notified about a new post. " +
        "IMPORTANT: you MUST call the matching read tool first and use a real id from its result - " +
        "never guess an id from memory.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put(idParamName, buildJsonObject {
                    put("type", "integer")
                    put("description", "Id of the $targetLabel to interact with. MUST be copied from the read tool result, never guessed.")
                })
                put("action", buildJsonObject {
                    put("type", "string")
                    put("description", "Either \"like\" or \"comment\"")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Comment text; required when action is \"comment\"")
                })
                put("reply_to", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional actor you are replying to (\"user\" or \"assistant\"). " +
                        "Set it ONLY when your comment is a direct reply to a specific comment on this target; " +
                        "a plain comment on the target itself must omit it.")
                })
            },
            required = listOf(idParamName, "action")
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 $name 给「$targetLabel」点赞或评论:action=like 点赞,action=comment 评论(需给 content)。" +
            "【铁律】$idParamName 必须来自 read 工具的真实返回:评论/点赞前先调对应 read 工具(moments_read/mood_read/secrets_read/timeline_read)," +
            "从返回的 id 字段里挑目标,严禁凭记忆猜 ID——猜错的评论会牛头不对马嘴,非常伤感情。" +
            "评论成功后工具会回显目标内容摘要:若回显与你想评论的帖子对不上,立即换正确 ID 重新评论,并向${me.rerere.rikkahub.service.USER_ALIAS_TOKEN}说明。" +
            "评论分两种:直接评论内容本身就不带 reply_to;只有针对某条评论回话时才带 reply_to(值为那条评论作者的 actor, user/assistant)。" +
            "互动要自然克制,像熟人一样;同一内容不要重复点赞;${me.rerere.rikkahub.service.USER_ALIAS_TOKEN}发布了新内容时,这是你表达关心的好机会。" +
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
        val replyTo = argsObj?.get("reply_to")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull
            ?.takeIf { it == "user" || it == "assistant" }

        val payload = when {
            targetId == null -> buildJsonObject { put("ok", false); put("error", "MISSING_${idParamName.uppercase()}") }
            action != "like" && action != "comment" ->
                buildJsonObject { put("ok", false); put("error", "INVALID_ACTION") }
            action == "comment" && content.isNullOrBlank() ->
                buildJsonObject { put("ok", false); put("error", "MISSING_CONTENT") }
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    val targetText = targetContent(targetId)
                    if (targetText == null) {
                        // 目标不存在:附上最近候选,模型可当场改用正确 ID,不用再瞎猜一轮
                        val candidates = runCatching { recentTargets() }.getOrElse { emptyList() }
                        return@runCatching buildJsonObject {
                            put("ok", false)
                            put("error", "TARGET_NOT_FOUND")
                            put("hint", "id=$targetId does not exist. Pick the real id from 'recent' below (call the read tool for full content).")
                            put("recent", buildJsonArray {
                                for ((id, excerpt) in candidates.take(10)) {
                                    add(buildJsonObject {
                                        put("id", id)
                                        put("excerpt", excerpt.take(60))
                                    })
                                }
                            })
                        }
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
                            replyTo = replyTo,
                            createdAt = System.currentTimeMillis(),
                            isRead = 0,
                        )
                    )
                    // 回显目标内容摘要:模型自检「评论是否对得上帖」,不符就换 ID 重评
                    buildJsonObject {
                        put("ok", true)
                        put("action", action)
                        put("target_id", targetId)
                        put("target_excerpt", excerptForTool(targetText))
                        if (action == "comment") {
                            put("check", "Verify target_excerpt matches the post you meant to comment on; if not, redo with the correct id.")
                        }
                    }
                }.getOrElse {
                    buildJsonObject { put("ok", false); put("error", "DB_ERROR") }
                }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
