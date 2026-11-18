package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.util.concurrent.ConcurrentHashMap

/**
 * 生命体征自评协议:模型在回复末尾输出 <vitals>{...}</vitals>(系统指令要求),
 * 本 transformer 保证这行字 TA 永远看不到,并把自评落库。
 *
 * ⚠️ 关键结构约束(r294~r299 自评 0 条的根因):GenerationLoop 在生成结束后会把
 * visualTransform 的结果直接赋回真实消息列表,再调 onGenerationFinish——
 * 也就是标签在 onGenerationFinish 有机会解析之前就已被剥掉。
 * 所以必须在 visualTransform 剥标签前解析并缓存(按消息 id 键控),
 * onGenerationFinish 从缓存取值落库。流式期间 visualTransform 反复调用,
 * 解析幂等覆盖缓存,只有 onGenerationFinish 会 remove+上报,不会重复打点。
 *
 * 协议字段:v=效价(-1~1) a=唤醒(0~1) emo=情绪名 cause=起因(一句话)
 * 解析失败静默丢弃,状态层走 msg_out 固定脉冲兜底——"数是算出来的"依然成立。
 */
object VitalsReportTransformer : OutputMessageTransformer, KoinComponent {
    // 未闭合也要剥(流式中途标签只来了一半):闭了吃闭,没闭吃到结尾。
    // IGNORE_CASE:有的模型会吐 <VITALS>;标签名两侧容忍空白。
    private val TAG_REGEX = Regex("\\n?<vitals\\s*>[\\s\\S]*?(</vitals\\s*>|\\z)", RegexOption.IGNORE_CASE)
    private val JSON_REGEX = Regex("<vitals\\s*>\\s*(\\{[\\s\\S]*?\\})\\s*(</vitals\\s*>)?", RegexOption.IGNORE_CASE)

    private class Parsed(val v: Float, val a: Float, val emo: String, val cause: String)

    private val pendingReports = ConcurrentHashMap<String, Parsed>()

    private fun stripTag(text: String): String = text.replace(TAG_REGEX, "").trimEnd()

    /** v/a 兼容数字与字符串两种写法;缺字段或解析不了返回 null */
    private fun JsonObject.numField(key: String): Float? {
        val p = this[key]?.jsonPrimitive ?: return null
        return p.floatOrNull ?: p.contentOrNull?.trim()?.toFloatOrNull()
    }

    // 宽松兜底正则全部预编译:①初始化期就把 ICU 不认的模式暴露出来而不是生成中途炸 ②不重复编译
    // (r301 事故:Regex(",\\s*}") 在 Android ICU 正则里 `}` 被当区间量词开头直接 PatternSyntaxException,
    // 桌面 JVM 能编过所以 CI 编译不报;只有标签真的出现时才执行到,于是带出了运行时炸)
    private val LENIENT_V = Regex("[\"']?v[\"']?\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val LENIENT_A = Regex("[\"']?a[\"']?\\s*:\\s*(\\d+(?:\\.\\d+)?)")
    private val LENIENT_EMO = Regex("[\"']?emo[\"']?\\s*:\\s*[\"']?([^,\"}\\n]*)")
    private val LENIENT_CAUSE = Regex("[\"']?cause[\"']?\\s*:\\s*[\"']?([^,\"}\\n]*)")
    private val FENCE_REGEX = Regex("```(?:json)?")

    /** 严格 JSON 优先;失败退宽松正则(模型常写出没引号的 key、裸数字);整段受保护绝不影响生成 */
    private fun parseVitals(raw: String): Parsed? = runCatching {
        val cleaned = raw
            .replace(FENCE_REGEX, "")
            .replace("“", "\"").replace("”", "\"")
            .replace("：", ":") // 全角冒号
            .replace(",}", " }").replace(", }", " }") // 尾逗号(纯字符串替换,不用正则)
            .trim()
        runCatching {
            val obj = Json.parseToJsonElement(cleaned).jsonObject
            val v = obj.numField("v") ?: return@runCatching null
            val a = obj.numField("a") ?: return@runCatching null
            val emo = obj["emo"]?.jsonPrimitive?.contentOrNull ?: ""
            val cause = obj["cause"]?.jsonPrimitive?.contentOrNull ?: ""
            return Parsed(v, a, emo, cause)
        }
        // 宽松兜底:引号可有可无,值允许裸数字/裸词
        val v = LENIENT_V.find(cleaned)?.groupValues?.get(1)?.toFloatOrNull() ?: return@runCatching null
        val a = LENIENT_A.find(cleaned)?.groupValues?.get(1)?.toFloatOrNull() ?: return@runCatching null
        val emo = LENIENT_EMO.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val cause = LENIENT_CAUSE.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        Parsed(v, a, emo, cause)
    }.getOrNull()

    private fun transformParts(message: UIMessage): UIMessage {
        var changed = false
        val newParts = message.parts.map { part ->
            when {
                // 流式期间每个 chunk 都会走到这里:先用廉价 contains 预筛(正则带懒惰量词,
                // 对长文本没命中也要整段回溯扫描,卡顿大头),命中才跑正则
                part is UIMessagePart.Text && part.text.contains("<vitals", ignoreCase = true) -> {
                    changed = true
                    // 剥标签前先解析缓存——onGenerationFinish 时文本已被剥干净,只能靠缓存
                    JSON_REGEX.find(part.text)?.let { m ->
                        parseVitals(m.groupValues[1])?.let { parsed ->
                            if (pendingReports.size > 64) pendingReports.clear() // 防异常中止导致的积压
                            pendingReports[message.id.toString()] = parsed
                        }
                    }
                    part.copy(text = stripTag(part.text))
                }
                // 有的模型把自评吐进思考里(原生 reasoning part),一样抓:缓存+剥(思考面板里也不显示)
                part is UIMessagePart.Reasoning && part.reasoning.contains("<vitals", ignoreCase = true) -> {
                    changed = true
                    JSON_REGEX.find(part.reasoning)?.let { m ->
                        parseVitals(m.groupValues[1])?.let { parsed ->
                            if (pendingReports.size > 64) pendingReports.clear()
                            pendingReports[message.id.toString()] = parsed
                        }
                    }
                    part.copy(reasoning = stripTag(part.reasoning))
                }
                else -> part
            }
        }
        return if (changed) message.copy(parts = newParts) else message
    }

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
        if (ctx.assistant.vitalsEnabled) {
            val last = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
            val cached = last?.id?.toString()?.let { pendingReports.remove(it) }
            if (cached != null) {
                runCatching {
                    get<VitalsRecorder>().report(
                        subject = ctx.assistant.id.toString(),
                        valenceTarget = cached.v,
                        arousalTarget = cached.a,
                        cause = cached.cause,
                    )
                }
            } else {
                // 诊断脉冲:文本和思考里都没有自评标签——模型没吐(或被供应商拦了),
                // 在潮汐带放一条可见标记,用于区分「模型不吐」和「解析失败」
                runCatching {
                    get<VitalsRecorder>().pulse(
                        subject = ctx.assistant.id.toString(),
                        kind = "self_report",
                        valenceDelta = 0f,
                        arousalDelta = 0f,
                        label = "⚠️本轮没收到自评",
                    )
                }
            }
        }
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT) transformParts(message) else message
        }
    }
}
