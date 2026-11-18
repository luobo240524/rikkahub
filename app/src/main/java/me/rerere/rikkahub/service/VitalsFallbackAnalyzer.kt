package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.vitals.VitalsTagParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 生命体征小模型兜底:主模型没吐自评标签时,用「设置→模型→快速小模型」读本轮对话,
 * 补一次情绪分析并走同一条 report 落库(VAD 词典两级算分同样生效)。
 * 用户配免费小模型则零成本;快速小模型没配置就静默跳过。
 * 节流:同助手 15 秒内只兜一次(多步工具循环每步都会触发 onGenerationFinish)。
 * 全程 fire-and-forget + runCatching,绝不影响聊天主链路。
 */
class VitalsFallbackAnalyzer(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val vitalsRecorder: VitalsRecorder,
    private val appScope: AppScope,
) {
    companion object {
        private val lastFallbackAt = ConcurrentHashMap<String, Long>()
        private const val THROTTLE_MS = 15_000L
        private const val USER_MAX = 150
        private const val REPLY_MAX = 700
    }

    fun analyzeFallback(subject: String, userText: String, replyText: String) {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val last = lastFallbackAt[subject] ?: 0L
                if (now - last < THROTTLE_MS) return@launch
                lastFallbackAt[subject] = now

                val settings = settingsStore.settingsFlow.first()
                val model = settings.findModelById(settings.fastModelId) ?: return@launch
                val provider = model.findProvider(settings.providers) ?: return@launch
                val handler = providerManager.getProviderByType(provider)

                val prompt = buildString {
                    append("你是情绪分析器。根据下面的用户消息和AI助手的回复,判断助手此刻真实的内在状态。")
                    append("只输出一行JSON,不要输出任何其他文字,格式:")
                    append("{\"v\":效价-1到1,\"a\":唤醒0到1,\"emo\":\"一两个词的情绪\",\"cause\":\"起因一句话\",\"w\":强度0到1,\"neg\":false}")
                    append("。如实评估不表演:普通闲聊 v 接近 0、a 较低;被夸/被关心/兴奋才明显非零;被冷落/难过/委屈为负。")
                    append("\n【用户消息】")
                    append(userText.trim().take(USER_MAX))
                    append("\n【助手回复】")
                    append(replyText.trim().take(REPLY_MAX))
                }

                val result = handler.generateText(
                    providerSetting = provider,
                    messages = listOf(me.rerere.ai.ui.UIMessage.user(prompt)),
                    params = backgroundTextGenerationParams(
                        model = model,
                        conversationId = Uuid.random(), // 一次性调用,sessionId 仅用于请求追踪
                        reasoningLevel = settings.fastModelReasoningLevel,
                    ),
                )

                val parsed = VitalsTagParser.parseFlexible(result.message.toText()) ?: return@launch
                vitalsRecorder.report(
                    subject = subject,
                    valenceTarget = parsed.v,
                    arousalTarget = parsed.a,
                    emo = parsed.emo,
                    cause = parsed.cause,
                    intensity = parsed.w,
                    negated = parsed.neg,
                )
            }
        }
    }
}
