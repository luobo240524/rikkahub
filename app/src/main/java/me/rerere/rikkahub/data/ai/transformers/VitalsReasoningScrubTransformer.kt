package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * r315 思考链静默化:自评协议(system 末尾大段 + 用户消息尾部提醒)会被推理模型复述进思考链
 * ("根据要求,我需要在末尾输出vitals自评标签…"),思考链用户可见,观感差。
 * 指令层已在协议里禁止思考链提及,但指令是软约束;本 transformer 是硬保证:
 * 把 Reasoning part 里含协议关键词的句子整句删掉——只删思考,不碰正文。
 *
 * 必须排在 ThinkTagTransformer 之后(outputTransformers 列表顺序):
 * think 块先变成 Reasoning part 才能被清理;原生 reasoning part 则天然经过这里。
 *
 * 关键词(r316 扩):除协议词(vitals/自评标签/自评协议/内在状态自评/生命体征/本轮必做/系统提示·本轮)外,
 * 补字段名草稿模式——真机实拍模型会在思考里预演标签:"cause填点真的…v=-0.3,a=0.3,emo"心疼""。
 * cause/emo 带 CJK 或带 =/引号才命中(「emo了」「英文 cause of」不误伤);
 * v/a/w=数值 与 neg=true/false 是纯协议写法;效价/唤醒度为字段中文名。
 * 误伤面:偶发的中英混排(如「cause之一」)会掉一句思考——代价可接受,只掉思考不碰正文。
 * META 无量词回溯,直接 containsMatchIn 即可;正则全部预编译(r302 ICU 坑)。
 *
 * GenerationLoop 会把 visualTransform 结果赋回真实消息列表(r300 结构),所以删掉的句子
 * 会持久化进历史——协议复述本就是噪音,落库无副作用。
 */
object VitalsReasoningScrubTransformer : OutputMessageTransformer {
    private val META_KEYWORD =
        Regex(
            "(?i)vitals|自评标签|自评协议|内在状态自评|生命体征|本轮必做|系统提示·本轮" +
                "|cause\\s*[=：:\"“”「]|cause[一-龥]" +
                "|emo\\s*[=：:\"“”「]|emo(?!了)[一-龥]" +
                "|[vaw]\\s*=\\s*[+-]?[\\d.]+" +
                "|neg\\s*=\\s*(true|false|真|假)" +
                "|效价|唤醒度"
        )

    /** 逐句过滤:按句读边界切段,含协议关键词的整段丢弃 */
    private fun scrub(text: String): String {
        if (!META_KEYWORD.containsMatchIn(text)) return text
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
            if (!META_KEYWORD.containsMatchIn(sentence)) sb.append(sentence)
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
        // 只对开了生命体征的助手生效,其余助手的历史 reasoning 一个字不动
        if (!ctx.assistant.vitalsEnabled) return messages
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) message
            else {
                var changed = false
                val newParts = message.parts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.reasoning.isNotEmpty() &&
                        META_KEYWORD.containsMatchIn(part.reasoning)
                    ) {
                        changed = true
                        part.copy(reasoning = scrub(part.reasoning))
                    } else {
                        part
                    }
                }
                if (changed) message.copy(parts = newParts) else message
            }
        }
    }
}
