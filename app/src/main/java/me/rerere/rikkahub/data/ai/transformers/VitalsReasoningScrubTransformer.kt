package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant

/**
 * r315 思考链静默化:「消息末尾输出某东西」类的后台机制(自评标签/唤醒标记/表情包工具)
 * 会被推理模型复述进思考链("根据要求,我需要在末尾输出vitals自评标签…"),
 * 思考链用户可见,观感差。指令层的「思考里不要提」是软约束;本 transformer 是硬保证:
 * 把 Reasoning part 里含机制关键词的句子整句删掉——只删思考,不碰正文。
 *
 * r323 起按机制分组,各自门控(没开对应机制的助手,思考一个字不动):
 * - vitals 组(assistant.vitalsEnabled):自评协议词表。r315 首建,r316 扩字段草稿模式——
 *   真机实拍模型会在思考里预演标签:"cause填点真的…v=-0.3,a=0.3,emo"心疼""。
 *   cause/emo 带 CJK 或带 =/引号才命中(「emo了」「英文 cause of」不误伤);
 *   v/a/w=数值 与 neg=true/false 是纯协议写法;效价/唤醒度为字段中文名。
 * - wake 组(assistant.autonomousWake):自主唤醒标记语法与机制词。安全性已验证:
 *   唤醒解析器 trailingAssistantWakeText 只读 Text part,思考里的 <wake> 标记本来就不被
 *   解析(模型把标记写进思考=功能已失效),删掉纯是展示层清理,无功能风险。
 *   r325 升级为裸词(\\bwake\\b|唤醒):用户连续手删多条仍见复述,机制词追补是打地鼠,
 *   开唤醒助手的思考里凡含「唤醒/wake」的句子整句删——宁可多删一句自然考量,不留复述。
 * - sticker 组(LocalToolOption.SendSticker 在 localTools):send_sticker 工具名与规则词。
 *   r325 同步升级裸词(表情包)——「发个表情包逗TA」这类选包考量按用户口径同属机制复述。
 * - 通用尾行复述词(r324,任一组激活时追加):「末尾那行/照抄系统/单独一行输出/标准标记」。
 *   三个机制的提示词都含「回复最末尾单独一行输出」式结构指令,模型会换说法演练绕开机制专名
 *   (真机实拍:「末尾那行照抄系统给的」);协议时代存进历史的漏网句也靠它随下轮发送清洗落库。
 *
 * 必须排在 ThinkTagTransformer 之后(outputTransformers 列表顺序):
 * think 块先变成 Reasoning part 才能被清理;原生 reasoning part 则天然经过这里。
 *
 * GenerationLoop 会把 visualTransform 结果赋回真实消息列表(r300 结构),所以删掉的句子
 * 会持久化进历史——机制复述本就是噪音,落库无副作用。
 */
object VitalsReasoningScrubTransformer : OutputMessageTransformer {
    private val VITALS_KEYWORD =
        Regex(
            "(?i)vitals|自评标签|自评协议|内在状态自评|生命体征|本轮必做|系统提示·本轮" +
                "|cause\\s*[=：:\"“”「]|cause[一-龥]" +
                "|emo\\s*[=：:\"“”「]|emo(?!了)[一-龥]" +
                "|[vaw]\\s*=\\s*[+-]?[\\d.]+" +
                "|neg\\s*=\\s*(true|false|真|假)" +
                "|效价|唤醒度"
        )

    private val WAKE_KEYWORD =
        Regex(
            "(?i)\\bwake\\b|唤醒"
        )

    private val STICKER_KEYWORD =
        Regex(
            "(?i)send_sticker|表情包"
        )

    /**
     * r324 通用尾行复述词:三个机制的提示词都含「回复最末尾单独一行输出」式的结构指令,
     * 推理模型会换着说法演练("末尾那行照抄系统给的"),绕开机制专名。真机实拍补词。
     * 误伤面:「末尾那行」偶指对方消息末行——代价仍是只掉一句思考不碰正文,可接受。
     */
    private val GENERIC_KEYWORD =
        Regex(
            "(?i)末尾那行|照抄系统|单独一行输出|标准标记|系统提示"
        )

    /** 按助手开关装配生效词表;一个机制都没开就无需进入逐句过滤 */
    private fun activeKeywords(assistant: Assistant): List<Regex> = buildList {
        if (assistant.vitalsEnabled) add(VITALS_KEYWORD)
        if (assistant.autonomousWake) add(WAKE_KEYWORD)
        if (LocalToolOption.SendSticker in assistant.localTools) add(STICKER_KEYWORD)
        // 任一末尾输出机制开着,就追加通用尾行复述词(历史思考里的协议时代漏网句也靠它清)
        if (isNotEmpty()) add(GENERIC_KEYWORD)
    }

    /** 逐句过滤:按句读边界切段,含任一生效组关键词的整段丢弃 */
    private fun scrub(text: String, keywords: List<Regex>): String {
        if (keywords.none { it.containsMatchIn(text) }) return text
        val sb = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            var end = i
            while (end < n) {
                val c = text[end]
                // 句读边界:中英标点+换行。英文句点也切,避免整段英文推理因一个关键词被全删
                if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' ||
                    c == '.' || c == '\n' || c == '；' || c == ';'
                ) {
                    end++
                    break
                }
                end++
            }
            val sentence = text.substring(i, end)
            if (keywords.none { it.containsMatchIn(sentence) }) sb.append(sentence)
            i = end
        }
        // 收尾:清掉删除留下的连续空行,首尾去空白
        val out = sb.toString().replace("\n{3,}".toRegex(), "\n\n")
        return out.trim('\n')
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val keywords = activeKeywords(ctx.assistant)
        if (keywords.isEmpty()) return messages
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) message
            else {
                var changed = false
                val newParts = message.parts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.reasoning.isNotEmpty() &&
                        keywords.any { it.containsMatchIn(part.reasoning) }
                    ) {
                        changed = true
                        part.copy(reasoning = scrub(part.reasoning, keywords))
                    } else {
                        part
                    }
                }
                if (changed) message.copy(parts = newParts) else message
            }
        }
    }
}
