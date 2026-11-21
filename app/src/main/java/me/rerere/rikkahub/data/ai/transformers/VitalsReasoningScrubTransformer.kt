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
 *   不收裸「唤醒」——"明早叫你起床"式的自然考量若含机制词会被误删,先用精确词观察。
 * - sticker 组(LocalToolOption.SendSticker 在 localTools):send_sticker 工具名与规则词。
 *   不收裸「表情包」——「这里发个表情包活跃气氛」是自然思考,不误伤。
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
            "(?i)<wake|wake\\s+(after|every|cancel)" +
                "|唤醒标记|唤醒机制|唤醒任务|安排唤醒"
        )

    private val STICKER_KEYWORD =
        Regex(
            "(?i)send_sticker|表情包工具|表情包(使用)?规则"
        )

    /** 按助手开关装配生效词表;一个机制都没开就无需进入逐句过滤 */
    private fun activeKeywords(assistant: Assistant): List<Regex> = buildList {
        if (assistant.vitalsEnabled) add(VITALS_KEYWORD)
        if (assistant.autonomousWake) add(WAKE_KEYWORD)
        if (LocalToolOption.SendSticker in assistant.localTools) add(STICKER_KEYWORD)
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
