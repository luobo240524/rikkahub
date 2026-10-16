package me.rerere.rikkahub.service

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * 「助手自主唤醒」协议：提示词、触发消息与标记解析。
 *
 * 标记语法：
 * - 一次性: <wake after="30m" note="事项"/>  (m/h/d)
 * - 每日循环: <wake every="day 09:00" note="事项"/>
 * - 每周循环: <wake every="week 1 09:00" note="事项"/>  (1-7 = 周一到周日)
 * - 每月循环: <wake every="month 10 09:00" note="事项"/>  (每月 10 号)
 * - 取消循环: <wake cancel/>
 *
 * 流程：
 * 1. assistant.autonomousWake 开启时，system prompt 注入 AUTONOMOUS_WAKE_PROMPT
 * 2. 模型在回复末尾输出唤醒标记安排任务（也可以不输出 = 沉默）
 * 3. 生成完成后 ChatService 解析标记、用 WorkManager 定时；标记文本在显示层
 *    转换为「⏰ 已设置…」确认条，数据库中保留原始标记
 * 4. 时间到后 AutonomousWakeWorker 调用 ChatService.triggerAutonomousWake，
 *    注入一条隐藏的合成用户消息（不落库、不显示）触发新一轮生成
 * 5. 循环任务在每次触发完成后自动续期下一次；中间的普通对话不会取消已安排的任务，
 *    只有模型输出 <wake cancel/>、输出新标记替换、或用户关闭唤醒开关时才会取消
 */
const val AUTONOMOUS_WAKE_PROMPT = """
[自主唤醒] 你拥有自主醒来能力，可以在回复的最末尾单独一行输出以下标记之一：
- 定时一次性: <wake after="30m" note="事项"/>  （m=分钟 h=小时 d=天，最短 1 分钟，最长 24 小时）
- 每日循环: <wake every="day 09:00" note="事项"/>
- 每周循环: <wake every="week 1 09:00" note="事项"/>  （数字 1-7 = 周一到周日）
- 每月循环: <wake every="month 10 09:00" note="事项"/>  （每月 10 号的 09:00）
- 取消全部循环任务: <wake cancel/>
- 只取消某一项: <wake cancel note="事项"/>  （note 必须逐字复制安排时的原文，一个字都不能改）
- note 是这次唤醒要做的事情的简短说明，必须填写，用户会在界面上看到它
- 你不能直接验证唤醒是否生效：如用户追问或你不确定，调用 query_wake_records 工具核对（仅返回当前对话的记录）；
  如果收到「唤醒标记未被接受」的系统备注，必须如实告知用户唤醒没有生效，绝不能谎报已设置
- 用户明确要求的时间间隔/时间点按用户要求输出
- after= 只接受时长（如 30m、2h）；要表达每天的某个时间点必须用 every="day HH:MM"，禁止把时间点塞进 after=
- 决策直接做：不要把犹豫、自我对话、英文思考过程写进回复——回复里只出现给用户看的最终内容，决定不安排唤醒时正常回复即可，一个字都不要提唤醒决策
- 循环任务会一直执行，中间的普通聊天不会打断它；只有你输出 <wake cancel/> 才会取消
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
    "如果无事可做，简单说明即可。" +
    "关于唤醒安排：已安排的循环任务会由系统自动按周期续期，不需要你重复安排；" +
    "刚完成的那次唤醒对应的事项已经完成，不要为同一事项重新安排唤醒；" +
    "但如果你完成本阶段任务后还有明确的下一步（需要过一段时间继续），" +
    "可以安排下一次唤醒并写清楚下一步事项——仅限有真实后续任务时才这样做。"

/**
 * 唤醒标记：<wake ...> 宽松匹配（属性顺序无关、单双引号兼容）。
 * 具体属性由 parseWakeSchedule 解析。
 */
val WAKE_TAG = Regex("""<wake\b[^>]*>""")

/** 流式输出中可能残存的未闭合标记（仅用于显示层清理） */
val WAKE_TAG_PARTIAL = Regex("""<wake[^>]*$""")

/** 解析出的唤醒计划 */
sealed interface WakeSchedule {
    val note: String

    /** 一次性：N 分钟后 */
    data class OneShot(val minutes: Long, override val note: String = "") : WakeSchedule

    /** 日历循环：daily / weekly / monthly */
    data class Cycle(val type: String, val hour: Int, val minute: Int, val day: Int, override val note: String = "") : WakeSchedule

    /** 取消任务：note 为空 = 全部；指定 = 按事项取消（需与安排时一致） */
    data class Cancel(override val note: String = "") : WakeSchedule
}

private val EVERY_TAG = Regex(
    """every\s*=\s*["']?(daily|day|weekly|week|monthly|month)(?:\s+(\d{1,2}))?\s+(\d{1,2}):(\d{2})["']?"""
)

private fun normalizeCycleType(raw: String): String = when (raw) {
    "day" -> "daily"
    "week" -> "weekly"
    "month" -> "monthly"
    else -> raw
}

/** 从标记文本解析唤醒计划；无法识别返回 null */
fun parseWakeSchedule(tag: String): WakeSchedule? {
    // 宽松解析：不同模型书写习惯不同，先归一化全角引号/等号/冒号/全角空格
    val normalized = tag
        .replace('＝', '=')
        .replace('：', ':')
        .replace('\u201c', '"').replace('\u201d', '"')
        .replace('\u2018', '\'').replace('\u2019', '\'')
        .replace('\u3000', ' ')
    val note = Regex("""note\s*=\s*["']?([^"']*)["']?""").find(normalized)?.groupValues?.get(1)?.trim() ?: ""
    // after 单位兼容：m/分钟/分/min、h/小时/时、d/天/日（大小写不敏感）
    val after = Regex("""after\s*=\s*["']?(\d+)\s*([a-zA-Z\u4e00-\u9fa5]+)["']?""").find(normalized)
    if (after != null) {
        val minutes = parseWakeMinutesLenient(after.groupValues[1], after.groupValues[2])
        if (minutes <= 0) return null
        return WakeSchedule.OneShot(minutes, note)
    }
    val every = EVERY_TAG.find(normalized)
    if (every != null) {
        val type = normalizeCycleType(every.groupValues[1])
        val day = (every.groupValues[2]?.toIntOrNull() ?: 0).coerceIn(0, 31)
        val hour = (every.groupValues[3].toIntOrNull() ?: 0).coerceIn(0, 23)
        val minute = (every.groupValues[4].toIntOrNull() ?: 0).coerceIn(0, 59)
        return WakeSchedule.Cycle(type, hour, minute, day, note)
    }
    if (tag.contains("cancel")) return WakeSchedule.Cancel(note)
    return null
}

/**
 * 模型偶发把整条回复包装成 Kotlin 数据类 toString 格式：
 * Text(text=正文..., metadata=null)
 * 此净化器剥掉该包装，掐断"模型模仿上下文垃圾格式"的循环。
 */
fun stripToStringWrapper(text: String): String {
    var t = text.trim()
    // 模型偶发把回复包装成 Text(text=..., metadata=null) 序列化格式；
    // 多段粘连时中段会出现 "正文1, metadata=null)\nText(text=正文2"，先还原为换行
    t = t.replace(Regex("""[,，]?\s*metadata=null\)\s*\n*\s*Text\(text=\s*"""), "\n")
    // 再剥开头/结尾的残留碎片
    repeat(3) {
        t = t.replace(Regex("""^Text\(text="""), "")
        t = t.replace(Regex("""^[,，]?\s*metadata=null\)\s*"""), "")
    }
    t = t.replace(Regex(""",?\s*metadata=null\)\s*$"""), "")
    return t.trim()
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

/** 宽松时长单位解析：兼容 中英文/简写（30分钟/30min/2小时/2h/1天/1d） */
private fun parseWakeMinutesLenient(value: String, unitRaw: String): Long {
    val raw = value.toLongOrNull() ?: return -1
    val unit = unitRaw.trim().lowercase()
    val minutes = when {
        unit in setOf("m", "min", "mins", "minute", "minutes", "分钟", "分") -> raw
        unit in setOf("h", "hr", "hrs", "hour", "hours", "小时", "时") -> raw * 60
        unit in setOf("d", "day", "days", "天", "日") -> raw * 1440
        else -> -1L
    }
    return minutes.coerceIn(1, 1440)
}

/** 计算循环计划的下一次触发时间（毫秒延时，至少 1 分钟） */
fun nextCycleDelayMillis(cycle: WakeSchedule.Cycle): Long {
    val now = LocalDateTime.now()
    val target: LocalDateTime = when (cycle.type) {
        "daily" -> {
            var next = now.toLocalDate().atTime(cycle.hour, cycle.minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            next
        }
        "weekly" -> {
            val targetDay = DayOfWeek.of(cycle.day.coerceIn(1, 7))
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(targetDay))
                .atTime(cycle.hour, cycle.minute)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            next
        }
        else -> { // monthly
            val day = cycle.day.coerceIn(1, 28)
            var next = now.toLocalDate().withDayOfMonth(day.coerceAtMost(now.toLocalDate().lengthOfMonth()))
                .atTime(cycle.hour, cycle.minute)
            if (!next.isAfter(now)) {
                next = now.toLocalDate().plusMonths(1).withDayOfMonth(day).atTime(cycle.hour, cycle.minute)
            }
            next
        }
    }
    return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
}

/** 把原始唤醒标记转换为用户可见的确认条（Markdown 引用块） */
fun prettifyWakeTag(text: String): String {
    return WAKE_TAG.replace(text) { match ->
        val schedule = parseWakeSchedule(match.value) ?: return@replace match.value
        val note = schedule.note
        buildString {
            append("> ⏰ ")
            when (schedule) {
                is WakeSchedule.OneShot -> {
                    append("已设置 ")
                    append(schedule.minutes)
                    append(" 分钟后唤醒")
                }
                is WakeSchedule.Cycle -> {
                    val hm = "%02d:%02d".format(schedule.hour, schedule.minute)
                    append("已设置")
                    when (schedule.type) {
                        "daily" -> append("每天 $hm")
                        "weekly" -> append("每周${"一二三四五六日".getOrNull(schedule.day - 1) ?: schedule.day.toString()} $hm")
                        else -> append("每月 ${schedule.day} 日 $hm")
                    }
                    append("循环唤醒")
                }
                is WakeSchedule.Cancel -> {
                    append("已取消循环唤醒")
                    if (schedule.note.isNotEmpty()) append("（${schedule.note}）")
                }
            }
            if (note.isNotEmpty() && schedule !is WakeSchedule.Cancel) {
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
