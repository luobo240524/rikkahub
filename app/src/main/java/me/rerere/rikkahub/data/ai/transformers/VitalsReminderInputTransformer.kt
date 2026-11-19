package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 生命体征自评提醒(输入侧):把最后一条用户消息在「发给模型的副本」末尾追加一行自评提醒。
 * 只存在于本次请求,不落库、不显示、不影响历史——对指令遵循弱的模型(自评协议靠 system 末尾
 * 一大段容易被忽略)这是最强的约束位置:贴着对话尾部,模型没法看不见。
 * 配合 VitalsReportTransformer 的「⚠️本轮没收到自评」诊断脉冲使用。
 */
object VitalsReminderInputTransformer : InputMessageTransformer {
    // \u200b 零宽空格开头,避免和正文粘连;内容尽量短,省 token
    // r315:补上思考链禁提——推理模型会把这个提醒复述进思考链,思考链用户可见
    private const val REMINDER =
        "\n\n[系统提示·本轮必做]回复的最末尾必须另起一行输出自评标签:" +
            "<vitals>{\"v\":0.1,\"a\":0.3,\"emo\":\"平静\",\"cause\":\"\"}</vitals>" +
            "(v=-1~1效价,a=0~1唤醒,emo=一两个词的情绪,cause=起因一句话,没有就留空)。该行会被系统隐藏,正文和思考过程里都绝不要提及这套机制。"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.vitalsEnabled) return messages
        val idx = messages.indexOfLast { it.role == MessageRole.USER }
        if (idx < 0) return messages
        val target = messages[idx]
        // 追加到最后一个文本 part;纯图片等无文本消息不动
        val lastTextIdx = target.parts.indexOfLast { it is UIMessagePart.Text }
        if (lastTextIdx < 0) return messages
        val newParts = target.parts.mapIndexed { i, part ->
            if (i == lastTextIdx && part is UIMessagePart.Text) {
                part.copy(text = part.text + REMINDER)
            } else {
                part
            }
        }
        return messages.subList(0, idx) + target.copy(parts = newParts) + messages.subList(idx + 1, messages.size)
    }
}
