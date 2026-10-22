package me.rerere.rikkahub.utils

/**
 * 引号字体高亮：把文本中引号内的内容包上 HTML 颜色标签，供 MarkdownNew(WebView) 渲染。
 *
 * 规则：
 * - 识别中文引号 「」『』 与英文引号 "..." '...'
 * - 跳过代码块（``` 围栏内）与行内代码（`...`），避免破坏代码内容
 * - 已含 HTML 标签的文本（hasHtml 场景）不处理，防止标签嵌套错乱
 * - 空内容/未闭合引号不处理
 */

private val COLOR_MAP = mapOf(
    "yellow" to "#FFC107",
    "red" to "#F44336",
    "green" to "#4CAF50",
    "blue" to "#2196F3",
)

fun quoteHighlightColorHex(name: String): String = COLOR_MAP[name] ?: COLOR_MAP["yellow"]!!

private val CODE_FENCE = Regex("```[\\s\\S]*?```")
private val INLINE_CODE = Regex("`[^`\\n]*`")

/** 中英文引号配对（按优先级：中文全角 > 英文双引 > 英文单引） */
private val QUOTE_PATTERNS = listOf(
    Regex("「([^「」\\n]*)」"),
    Regex("『([^『』\\n]*)』"),
    Regex("\"([^\"\\n]*)\""),
    Regex("'([^'\\n]*)'"),
)

/**
 * 高亮引号内容。
 * @param text 原始文本
 * @param colorName 颜色名（yellow/red/green/blue）
 * @return 处理后的文本（引号内容被包上 color span；无引号或无需处理时原样返回）
 */
fun highlightQuotes(text: String, colorName: String): String {
    if (text.isBlank()) return text
    // 已有 HTML 标签则不处理（防止嵌套破坏）
    if (text.contains("<") && text.contains(">")) return text

    val hex = quoteHighlightColorHex(colorName)
    val open = "<span style=\"color:$hex\">"
    val close = "</span>"

    // 保护代码块/行内代码：先抽取占位，处理后再还原
    val stash = mutableListOf<String>()
    fun stashCode(input: String): String {
        var s = input
        CODE_FENCE.findAll(s).forEach { m ->
            stash.add(m.value); s = s.replace(m.value, "\u0000C${stash.size - 1}\u0000")
        }
        INLINE_CODE.findAll(s).forEach { m ->
            stash.add(m.value); s = s.replace(m.value, "\u0000C${stash.size - 1}\u0000")
        }
        return s
    }
    fun unstash(input: String): String =
        stash.foldRight(input) { code, acc -> acc.replace("\u0000C${stash.indexOf(code)}\u0000", code) }

    var result = stashCode(text)
    for (pattern in QUOTE_PATTERNS) {
        result = pattern.replace(result) { m ->
            val inner = m.groupValues[1]
            if (inner.isBlank()) m.value else "$open${m.value}$close"
        }
    }
    return unstash(result)
}
