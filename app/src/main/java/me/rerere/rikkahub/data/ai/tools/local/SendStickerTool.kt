package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.stickers.StickerStore

/**
 * send_sticker：助手从用户贴纸库中选择一张表情包发送。
 * 渲染端 (ChatMessageCot.StickerBlock) 会把成功执行的工具调用渲染成贴纸大图。
 */
internal fun buildSendStickerTool(stickerStore: StickerStore): Tool = Tool(
    name = "send_sticker",
    description = "Send a sticker (emoji image) from the user's sticker library to express emotion. " +
        "Only send stickers when it fits the conversation naturally; do not send them repeatedly.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "The sticker id to send. Must be one of the ids listed in the system prompt.")
                })
            },
            required = listOf("id")
        )
    },
    systemPrompt = { _, _ ->
        val stickers = stickerStore.list()
        if (stickers.isEmpty()) {
            "The sticker library is empty; never call send_sticker."
        } else {
            buildString {
                append("Available stickers (id -> description):\n")
                stickers.forEach { sticker ->
                    append("- ${sticker.id}: ${sticker.description.ifBlank { "(no description)" }}\n")
                }
            }
        }
    },
    execute = { args ->
        val id = (args as? kotlinx.serialization.json.JsonObject)?.get("id")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        val sticker = id?.let { stickerStore.get(it) }
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
