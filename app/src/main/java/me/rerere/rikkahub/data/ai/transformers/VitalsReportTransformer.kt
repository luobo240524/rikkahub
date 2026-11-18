package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.VitalsRecorder
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * 生命体征自评协议:模型在回复末尾输出 <vitals>{...}</vitals>(系统指令要求),
 * 本 transformer 保证这行字 TA 永远看不到:
 * - visualTransform:流式期间持续剥掉(含未闭合标签,与 ThinkTag 同套路)
 * - onGenerationFinish:剥掉 + 解析 JSON → VitalsRecorder.report 落库
 * 解析失败静默丢弃,状态层走 msg_out 固定脉冲兜底——"数是算出来的"依然成立。
 * 协议字段:v=效价(-1~1) a=唤醒(0~1) emo=情绪名 cause=起因(一句话)
 */
object VitalsReportTransformer : OutputMessageTransformer, KoinComponent {
    // 未闭合也要剥(流式中途标签只来了一半):闭了吃闭,没闭吃到结尾。
    // IGNORE_CASE:有的模型会吐 <VITALS>;标签名两侧容忍空白。
    private val TAG_REGEX = Regex("\\n?<vitals\\s*>[\\s\\S]*?(</vitals\\s*>|\\z)", RegexOption.IGNORE_CASE)
    private val JSON_REGEX = Regex("<vitals\\s*>\\s*(\\{[\\s\\S]*?\\})\\s*(</vitals\\s*>)?", RegexOption.IGNORE_CASE)

    private fun stripTag(text: String): String = text.replace(TAG_REGEX, "").trimEnd()

    /** v/a 兼容数字与字符串两种写法("v":"0.2" 和 "v":0.2 都收);缺字段或解析不了返回 null */
    private fun JsonObject.numField(key: String): Float? {
        val p = this[key]?.jsonPrimitive ?: return null
        return p.floatOrNull ?: p.contentOrNull?.trim()?.toFloatOrNull()
    }

    private fun transformParts(message: UIMessage): UIMessage = message.copy(
        parts = message.parts.map { part ->
            if (part is UIMessagePart.Text && TAG_REGEX.containsMatchIn(part.text)) {
                part.copy(text = stripTag(part.text))
            } else {
                part
            }
        }
    )

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT) transformParts(message) else message
        }
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 先从原始文本抓自评 JSON(剥标签前!),解析成功则落库;然后统一剥标签返回
        if (ctx.assistant.vitalsEnabled) {
            runCatching {
                val last = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?: return@runCatching
                val raw = last.parts.filterIsInstance<UIMessagePart.Text>()
                    .lastOrNull()?.text ?: return@runCatching
                val json = JSON_REGEX.find(raw)?.groupValues?.get(1)
                    ?: return@runCatching
                // 有的模型把 JSON 包在 ```json 围栏里,先剥围栏再解析
                val cleaned = json.replace(Regex("```(?:json)?"), "").trim()
                val obj = Json.parseToJsonElement(cleaned).jsonObject
                val v = obj.numField("v") ?: return@runCatching
                val a = obj.numField("a") ?: return@runCatching
                val emo = obj["emo"]?.jsonPrimitive?.contentOrNull ?: ""
                val cause = obj["cause"]?.jsonPrimitive?.contentOrNull ?: ""
                get<VitalsRecorder>().report(
                    subject = ctx.assistant.id.toString(),
                    valenceTarget = v,
                    arousalTarget = a,
                    emo = emo.ifBlank { "平静" },
                    cause = cause,
                )
            }
        }
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT) transformParts(message) else message
        }
    }
}
