package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * 注入到 system prompt 的分条指令（仅 assistant.splitMessages 开启时）。
 * 由模型自己在合适的语意位置输出 <split/> 标记，客户端按标记切成多条气泡。
 */
const val SPLIT_MESSAGES_PROMPT = """
[消息分条] 你可以把一条回复拆分成多条连续发送的独立消息：
在想分割的位置单独一行输出标记 <split/>。
- 每条消息要像聊天软件里单独的一条消息：简短自然，可以只有一两句话
- 标记必须独占一行，不要写在代码块或表格里；标记本身不会显示给用户
- 很短的回答不需要拆分
"""

private val SPLIT_MARKER_LINE = Regex("""^\s*<\s*split\s*/?\s*>\s*$""")
private val SPLIT_TAG_ANYWHERE = Regex("""<\s*/?\s*split\s*/?\s*>""")

/**
 * 「消息分条」：把流式输出中的助手回复按 <split/> 标记拆分成多条气泡。
 *
 * 规则：
 * - 只处理最后一条（正在生成的）助手消息，其余历史消息原样保留
 * - 标记必须独占一行，代码块围栏(```)内的标记不生效
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
    if (!SPLIT_TAG_ANYWHERE.containsMatchIn(fullText)) return messages

    val segments = splitByMarker(fullText)
    val nonTextParts = last.parts.filter { it !is UIMessagePart.Text }

    if (segments.size <= 1) {
        // 没有独占一行的标记；清理可能残留的行内标记后原样返回
        val cleaned = SPLIT_TAG_ANYWHERE.replace(fullText, "").trim()
        if (cleaned == fullText.trim()) return messages
        return messages.dropLast(1) + last.copy(
            parts = nonTextParts + UIMessagePart.Text(cleaned)
        )
    }

    // 未完成段落之前的、非空的段落全部「落袋为安」
    val completed = segments.dropLast(1)
        .withIndex()
        .filter { it.value.isNotBlank() }
    if (completed.isEmpty()) return messages

    val head = messages.dropLast(1)
    val committed = completed.map { (index, segment) ->
        last.copy(
            id = stableBubbleId(last.id, index),
            parts = listOf(UIMessagePart.Text(segment.trim())),
            // 尾注(token/速度/耗时)只在最后一条气泡显示, 中间气泡清空
            usage = null,
        )
    }
    val streaming = last.copy(
        parts = nonTextParts + UIMessagePart.Text(segments.last().trim())
    )
    // 诊断：把拆分输入与输出记入内置日志页，用于定位 "Text(text=" 垃圾串来源
    runCatching {
        me.rerere.common.android.Logging.log(
            "BubbleSplitter",
            buildString {
                append("IN  [")
                append(fullText.take(120).replace('\n', '␤'))
                append("] OUT ")
                append(
                    (committed.map { it.parts.filterIsInstance<UIMessagePart.Text>().first().text } +
                        listOf(segments.last().trim()))
                        .joinToString(" || ") { it.take(60).replace('\n', '␤') }
                )
            }
        )
    }
    return head + committed + listOf(streaming)
}

/**
 * 按独占一行的 <split/> 标记拆分。处于 ``` 围栏内的标记不生效，标记行本身被丢弃。
 */
private fun splitByMarker(text: String): List<String> {
    val lines = text.replace("\r\n", "\n").split("\n")
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    var inFence = false
    for ((index, line) in lines.withIndex()) {
        if (line.trimStart().startsWith("```")) inFence = !inFence
        if (!inFence && SPLIT_MARKER_LINE.matches(line)) {
            segments.add(current.toString().trim('\n'))
            current.setLength(0)
            continue
        }
        current.append(line)
        if (index != lines.lastIndex) current.append('\n')
    }
    if (current.isNotBlank()) {
        segments.add(current.toString().trim('\n'))
    }
    return segments.ifEmpty { listOf(text) }
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
