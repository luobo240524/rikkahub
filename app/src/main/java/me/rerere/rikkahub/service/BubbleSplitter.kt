package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * 「消息分条」：把流式输出中的助手回复按段落空行拆分成多条气泡。
 *
 * 规则：
 * - 只处理最后一条（正在生成的）助手消息，其余历史消息原样保留
 * - 在空行（段落边界）处拆分，代码块围栏(```)内的空行不拆
 * - 已完成的段落使用「原消息ID+段落序号」派生的稳定ID，
 *   保证后续流式更新原地覆盖对应气泡，而不是不断新建
 * - 最后一个未完成段落保留原消息ID，继续作为流式气泡展示
 * - 含工具调用的消息不拆分，避免破坏工具调用流程
 */
fun splitAssistantBubbles(messages: List<UIMessage>): List<UIMessage> {
    val last = messages.lastOrNull() ?: return messages
    if (last.role != MessageRole.ASSISTANT) return messages
    if (last.parts.any { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }) return messages

    val textParts = last.parts.filterIsInstance<UIMessagePart.Text>()
    if (textParts.isEmpty()) return messages
    val fullText = textParts.joinToString("\n")

    val segments = splitByParagraph(fullText)
    if (segments.size <= 1) return messages

    // 未完成段落之前的、非空的段落全部「落袋为安」
    val completed = segments.dropLast(1)
        .withIndex()
        .filter { it.value.isNotBlank() }
    if (completed.isEmpty()) return messages

    val nonTextParts = last.parts.filter { it !is UIMessagePart.Text }
    val head = messages.dropLast(1)
    val committed = completed.map { (index, segment) ->
        last.copy(
            id = stableBubbleId(last.id, index),
            parts = listOf(UIMessagePart.Text(segment)),
        )
    }
    val streaming = last.copy(
        parts = nonTextParts + UIMessagePart.Text(segments.last())
    )
    return head + committed + streaming
}

/**
 * 按空行拆分段落。处于 ``` 围栏内的空行不算段落边界。
 */
private fun splitByParagraph(text: String): List<String> {
    if ("\n\n" !in text && "\r\n\r\n" !in text) return listOf(text)
    val normalized = text.replace("\r\n", "\n")
    val lines = normalized.split("\n")
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    var inFence = false
    for ((index, line) in lines.withIndex()) {
        if (line.trimStart().startsWith("```")) inFence = !inFence
        current.append(line)
        if (index != lines.lastIndex) current.append('\n')
        val isBoundary = !inFence && line.isBlank() &&
            current.isNotBlank() && current.toString().isNotBlank()
        if (isBoundary) {
            segments.add(current.toString().trim('\n'))
            current.setLength(0)
        }
    }
    if (current.isNotBlank()) {
        segments.add(current.toString().trim('\n'))
    }
    return if (segments.isEmpty()) listOf(normalized) else segments
}

/**
 * 由原消息ID与段落序号确定性地派生气泡ID，
 * 同一段落在每次流式快照中都会得到同一个ID。
 */
private fun stableBubbleId(base: Uuid, index: Int): Uuid {
    val digest = MessageDigest.getInstance("MD5")
        .digest(base.toByteArray() + index.toString().toByteArray())
    return Uuid.fromByteArray(digest.copyOf(16))
}
