package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.stickers.StickerStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * 折叠历史中已执行的 send_sticker 工具调用：
 * assistant 的 tool_call + tool result 结构 → 一条纯文本 "[助手发送了表情包（意思是：xx）]"。
 *
 * 目的：
 * 1. 大幅减少 token（去掉 tool_calls JSON 与长文件路径）
 * 2. 稳定上下文前缀 → 提高 DeepSeek 等按前缀计费缓存的命中率
 *
 * 仅折叠「消息内全部工具调用都是已执行的 send_sticker」的助手消息，
 * 混合其他工具调用（搜索/日历等）的消息保持原样，避免破坏协议结构。
 */
object StickerToolFoldTransformer : InputMessageTransformer, KoinComponent {
    private const val STICKER_TOOL_NAME = "send_sticker"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val store = get<StickerStore>()
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            val tools = message.parts.filterIsInstance<UIMessagePart.Tool>()
            if (tools.isEmpty()) return@map message
            // 混合其他工具调用不折叠
            if (tools.any { it.toolName != STICKER_TOOL_NAME }) return@map message
            // 有未执行的工具不折叠（流程进行中）
            if (tools.any { !it.isExecuted }) return@map message

            message.copy(
                parts = message.parts.flatMap { part ->
                    if (part is UIMessagePart.Tool) {
                        listOf(UIMessagePart.Text(part.foldStickerText(store)))
                    } else {
                        listOf(part)
                    }
                }
            )
        }
    }

    private fun UIMessagePart.Tool.foldStickerText(store: StickerStore): String {
        // output 的 Text part 内是 JSON：{"success":true,"url":"file://..."}
        val url = output.filterIsInstance<UIMessagePart.Text>()
            .firstNotNullOfOrNull { part ->
                runCatching {
                    (Json.parseToJsonElement(part.text) as? JsonObject)
                        ?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
                }.getOrNull()
            }
        val fileName = url?.substringAfterLast('/')
        val desc = fileName?.let { store.getByFileName(it) }
            ?.description?.take(16)
        return when {
            !desc.isNullOrBlank() -> "[助手发送了表情包（意思是：$desc）]"
            else -> "[助手发送了表情包]"
        }
    }
}
