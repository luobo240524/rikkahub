package me.rerere.rikkahub.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * 引号内文字高亮（移植自官方 3.0.0 MarkdownInline.InlineTextBuilder.appendText）。
 *
 * 规则（与官方一致）：
 * - 配对引号："…"、"…"、「…」、『…』；单引号常作撇号（don't），不参与配对
 * - 只高亮已闭合的引号对：流式输出或孤立引号不会把后面整段染花
 * - 引号可跨越加粗/链接等既有样式（本后处理只叠加 color，不清除原样式）
 * - 调用方传入的颜色为 null 时原样返回（开关关闭）
 */

private val QUOTE_PAIRS = mapOf(
    '"' to '"',
    '"' to '"',
    '「' to '」',
    '『' to '』',
)

/**
 * 给已构建好的 AnnotatedString 中「已闭合的引号对」叠加颜色。
 *
 * @param annotated 原始 AnnotatedString（含加粗/链接等样式）
 * @param color 高亮颜色；null 表示不高亮
 * @return 叠加颜色后的 AnnotatedString（无引号对时原样返回）
 */
fun highlightQuotes(annotated: AnnotatedString, color: Color?): AnnotatedString {
    if (color == null) return annotated
    val text = annotated.text
    if (text.isEmpty()) return annotated

    val style = SpanStyle(color = color)
    // 收集所有需要染色的区间 [start, end)
    val ranges = mutableListOf<Pair<Int, Int>>()
    var pendingClose: Char? = null
    var pendingStart = 0

    text.forEachIndexed { index, char ->
        val close = pendingClose
        if (close == null) {
            QUOTE_PAIRS[char]?.let {
                pendingClose = it
                pendingStart = index
            }
        } else if (char == close) {
            ranges.add(pendingStart to index + 1)
            pendingClose = null
        }
    }
    if (ranges.isEmpty()) return annotated

    // 后叠加：保留原样式，只补 color（与原 addStyle 行为一致，后加的样式优先）
    return AnnotatedString.Builder().apply {
        append(annotated)
        ranges.forEach { (start, end) ->
            addStyle(style, start, end)
        }
    }.toAnnotatedString()
}
