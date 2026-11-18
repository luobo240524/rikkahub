package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.vitals.VitalsTagParser
import me.rerere.rikkahub.service.VitalsFallbackAnalyzer
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
 * 协议字段:v=效价(-1~1) a=唤醒(0~1) emo=情绪名 cause=起因(一句话) w=强度(可选) neg=否定(可选)
 * 解析与剥标签逻辑在 VitalsTagParser(与小模型兜底分析共享)。
 * 解析失败静默丢弃;模型不吐标签时由小模型兜底分析补一轮(见 VitalsFallbackAnalyzer)。
 */
object VitalsReportTransformer : OutputMessageTransformer, KoinComponent {
    private val pendingReports = ConcurrentHashMap<String, VitalsTagParser.Parsed>()

    private fun transformParts(message: UIMessage): UIMessage {
        var changed = false
        val newParts = message.parts.map { part ->
            when {
                // 流式期间每个 chunk 都会走到这里:先用廉价 contains 预筛(正则带懒惰量词,
                // 对长文本没命中也要整段回溯扫描,卡顿大头),命中才跑正则
                part is UIMessagePart.Text && part.text.contains("<vitals", ignoreCase = true) -> {
                    changed = true
                    // 剥标签前先解析缓存——onGenerationFinish 时文本已被剥干净,只能靠缓存
                    VitalsTagParser.extractJson(part.text)?.let { raw ->
                        VitalsTagParser.parseVitals(raw)?.let { parsed ->
                            if (pendingReports.size > 64) pendingReports.clear() // 防异常中止导致的积压
                            pendingReports[message.id.toString()] = parsed
                        }
                    }
                    part.copy(text = VitalsTagParser.stripTag(part.text))
                }
                // 有的模型把自评吐进思考里(原生 reasoning part),一样抓:缓存+剥(思考面板里也不显示)
                part is UIMessagePart.Reasoning && part.reasoning.contains("<vitals", ignoreCase = true) -> {
                    changed = true
                    VitalsTagParser.extractJson(part.reasoning)?.let { raw ->
                        VitalsTagParser.parseVitals(raw)?.let { parsed ->
                            if (pendingReports.size > 64) pendingReports.clear()
                            pendingReports[message.id.toString()] = parsed
                        }
                    }
                    part.copy(reasoning = VitalsTagParser.stripTag(part.reasoning))
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
                        emo = cached.emo,
                        cause = cached.cause,
                        intensity = cached.w,
                        negated = cached.neg,
                    )
                }
            } else {
                // 诊断脉冲:文本和思考里都没有自评标签——模型没吐(或被供应商拦了),
                // 在潮汐带放一条可见标记,用于区分「模型不吐」和「解析失败」(用户要求保留观测)
                runCatching {
                    get<VitalsRecorder>().pulse(
                        subject = ctx.assistant.id.toString(),
                        kind = "self_report",
                        valenceDelta = 0f,
                        arousalDelta = 0f,
                        label = "⚠️本轮没收到自评",
                    )
                }
                // 小模型兜底:主模型没吐标签时,用快速小模型读本轮对话补一次情绪分析
                // (免费小模型则零成本;节流与失败静默都在 analyzer 内部)
                runCatching {
                    val userText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
                    val replyText = last?.toText().orEmpty()
                    get<VitalsFallbackAnalyzer>().analyzeFallback(
                        subject = ctx.assistant.id.toString(),
                        assistantName = ctx.assistant.name,
                        userAlias = ctx.assistant.userNickname?.takeIf { it.isNotBlank() } ?: "TA",
                        userText = userText,
                        replyText = replyText,
                    )
                }
            }
        }
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT) transformParts(message) else message
        }
    }
}
