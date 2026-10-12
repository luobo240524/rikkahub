package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.stickers.Sticker
import me.rerere.rikkahub.data.stickers.StickerStore

/**
 * send_sticker：助手从用户贴纸库中选择一张表情包发送。
 * 系统提示只注入置顶优先、按使用频率排序的前 limit 张（短编号 s1..sN，描述截断 16 字），
 * 最大限度减少 token 占用。渲染端 (ChatMessageCot.StickerBlock) 渲染成贴纸大图。
 */
internal fun buildSendStickerTool(stickerStore: StickerStore, limit: Int): Tool {
    fun visible(): List<Pair<String, Sticker>> = stickerStore.visibleForTool(limit)

    return Tool(
        name = "send_sticker",
        description = "Send a sticker (emoji image) from the user's sticker library to express emotion. " +
            "Only send stickers when it fits the conversation naturally; do not send them repeatedly.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "The sticker short id (e.g. s3). Must be one of the ids listed in the system prompt.")
                    })
                },
                required = listOf("id")
            )
        },
        systemPrompt = { _, _ ->
            val visible = visible()
            if (visible.isEmpty()) {
                "The sticker library is empty; never call send_sticker."
            } else {
                buildString {
                    append("表情包使用规则：\n")
                    append("- 调用 send_sticker 工具发送表情包，可与文字同时输出：先写你想说的话，再调用工具（或反过来），顺序自由\n")
                    append("- 绝对禁止在回复文字中出现 send_sticker、工具标记、[助手发送了表情包...] 之类的内部文本——工具调用对用户不可见，发送后直接正常说话即可\n")
                    append("- 可用表情包（id 含义）：\n")
                    visible.forEach { (sid, sticker) ->
                        val desc = sticker.description.take(16).ifBlank { "(no desc)" }
                        append("$sid $desc\n")
                    }
                }
            }
        },
        execute = { args ->
            val id = (args as? JsonObject)?.get("id")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            val sticker = id?.let { stickerStore.resolveShortId(it, limit) }
            if (sticker != null) stickerStore.incrementUse(sticker.id)
            val payload = if (id == null || sticker == null) {
                buildJsonObject {
                    put("error", "STICKER_NOT_FOUND")
                    put("message", "Sticker id '$id' does not exist. Check the id list in the system prompt.")
                }
            } else {
                buildJsonObject {
                    put("success", true)
                    put("url", stickerStore.fileUrl(sticker))
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    )
}
