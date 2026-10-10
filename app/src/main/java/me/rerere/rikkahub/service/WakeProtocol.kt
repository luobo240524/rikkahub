package me.rerere.rikkahub.service

/**
 * 「助手自主唤醒」协议：提示词、触发消息与标记解析。
 *
 * 流程：
 * 1. assistant.autonomousWake 开启时，system prompt 注入 AUTONOMOUS_WAKE_PROMPT
 * 2. 模型在回复末尾输出 <wake after="30m" note="事项"/> 安排下一次唤醒（也可以不输出 = 沉默）
 * 3. 生成完成后 ChatService 解析标记、用 WorkManager 定时；标记文本在显示层
 *    转换为「⏰ 已设置 xx 后唤醒」确认条，数据库中保留原始标记
 * 4. 时间到后 AutonomousWakeWorker 调用 ChatService.triggerAutonomousWake，
 *    注入一条隐藏的合成用户消息（不落库、不显示）触发新一轮生成
 */
const val AUTONOMOUS_WAKE_PROMPT = """
[自主唤醒] 你拥有自主醒来能力：在回复的最末尾单独一行输出 <wake after="30m" note="事项"/>，可以安排自己在指定时间后自动醒来。
- 时长格式：<数字><单位>，单位 m=分钟、h=小时、d=天；最短 1 分钟，最长 24 小时，例如 <wake after="2h" note="查看天气"/>
- note 是这次唤醒要做的事情的简短说明，必须填写，用户会在界面上看到它
- 用户明确要求短间隔（如"1分钟后提醒我"）时按用户要求输出
- 醒来时你会收到一条隐藏的系统消息，届时你可以调用工具、整理记忆，也可以直接给用户发消息
- 只在有明确价值时安排唤醒（等待条件变化、定期整理记忆、用户交代过的定时任务等）；没有需要就不要输出标记
- 输出唤醒标记后不要再输出任何其他内容
[输出格式] 你的回复必须是纯文本正文。绝对禁止把回复包装成 Text(text=...)、JSON 或任何序列化/代码格式；
那会被原样显示给用户。标记（<split/>、<wake...>）是唯一的例外。
"""

/** 唤醒时注入的隐藏触发消息（isSynthetic=true，不落库不显示） */
const val WAKE_TRIGGER_PROMPT =
    "你自主醒来了（由你之前安排的定时唤醒触发）。现在回顾当前对话与你的记忆，" +
    "执行你之前想做的事情：可以调用工具，也可以给用户留言。" +
    "如果无事可做，简单说明即可，并重新决定是否安排下一次唤醒。"

/**
 * 唤醒标记：<wake after="30m"/> 或 <wake after="30m" note="事项"/>
 * group1=数值 group2=单位 group3=事项(可选)
 */
val WAKE_TAG = Regex("""<wake\s+after\s*=\s*"(\d+)\s*([mhd])"(?:\s+note\s*=\s*"([^"]*)")?\s*/?\s*>""")

/** 流式输出中可能残存的未闭合标记（仅用于显示层清理） */
val WAKE_TAG_PARTIAL = Regex("""<wake[^>]*$""")

/**
 * 模型偶发把整条回复包装成 Kotlin 数据类 toString 格式：
 * Text(text=正文..., metadata=null)
 * 此净化器剥掉该包装，掐断"模型模仿上下文垃圾格式"的循环。
 */
fun stripToStringWrapper(text: String): String {
    var t = text.trim()
    if (t.startsWith("Text(text=")) {
        t = t.removePrefix("Text(text=")
        t = t.replace(Regex(""",?\s*metadata=null\)\s*$"""), "")
    }
    return t
}

/** 解析唤醒间隔（分钟），钳制在 1 分钟 ~ 24 小时 */
fun parseWakeMinutes(value: String, unit: String): Long {
    val raw = value.toLongOrNull() ?: return -1
    val minutes = when (unit) {
        "h" -> raw * 60
        "d" -> raw * 1440
        else -> raw
    }
    return minutes.coerceIn(1, 1440)
}

/** 把原始唤醒标记转换为用户可见的确认条（Markdown 引用块） */
fun prettifyWakeTag(text: String): String {
    return WAKE_TAG.replace(text) { match ->
        val value = match.groupValues[1]
        val unitName = when (match.groupValues[2]) {
            "h" -> "小时"
            "d" -> "天"
            else -> "分钟"
        }
        val note = match.groupValues[3].trim()
        buildString {
            append("> ⏰ 已设置 ")
            append(value)
            append(' ')
            append(unitName)
            append("后唤醒")
            if (note.isNotEmpty()) {
                append("｜事项：")
                append(note)
            }
        }
    }
}

/** 流式未闭合残片替换为占位，完成后会显示完整确认条 */
fun prettifyWakeTagPartial(text: String): String {
    return WAKE_TAG_PARTIAL.replace(text, "> ⏰ ")
}
