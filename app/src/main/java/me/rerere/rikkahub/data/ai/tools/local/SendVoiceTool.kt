package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.controller.TtsChunk
import me.rerere.tts.controller.TtsSynthesizer
import me.rerere.tts.controller.pcmToWav
import me.rerere.tts.provider.TTSManager
import java.io.File

/**
 * send_voice:助手用 TTS 合成一条语音条发给用户(像微信语音)。
 * - 合成结果落盘 filesDir/voice_replies/,输出 JSON 带 file:// 地址与时长
 * - 渲染端 (ChatMessageCot.VoiceBlock) 解析输出复用 VoiceMessageBubble 播放
 * - TTS 未配置/合成失败返回结构化错误,由 systemPrompt 引导助手改用文字回复
 */
internal fun buildSendVoiceTool(
    context: Context,
    ttsManager: TTSManager,
    settingsStore: SettingsStore,
): Tool {
    return Tool(
        name = "send_voice",
        description = "Send a voice message (text-to-speech audio) to the user, like a WeChat voice note. " +
            "Use it when the user asks you to speak/send a voice message, or when tone matters: greetings, comfort, " +
            "reading something aloud, singing a short line. Write your visible text first; the tool call ends the turn.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to speak aloud in natural spoken language. No markdown, no emoji, no lists. Keep it within 300 characters.")
                    })
                },
                required = listOf("content")
            )
        },
        systemPrompt = { _, _ ->
            buildString {
                append("语音条使用规则：\n")
                append("- 调用 send_voice 工具可以用你的声音发一条语音条；先写完文字内容，最后再调用工具——调用后本轮回复立即结束，无法再补充文字\n")
                append("- content 是要念出来的内容：用自然口语，不要 markdown/表情符号/列表；长度尽量不超过 300 字\n")
                append("- 适合场景：用户说「发语音/说给我听」，问候、安慰、念东西、语气很重要的时刻；普通信息回复不需要调用\n")
                append("- 每条回复最多调用一次 send_voice；一次调用失败就不要重试，直接用文字把内容说出来\n")
                append("- 绝对禁止在回复文字中出现 send_voice、工具标记、「（已发送语音…）」等内部记录文本——那些是系统备注，不是要说的话\n")
            }
        },
        execute = { args ->
            val content = (args as? JsonObject)?.get("content")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
            if (content.isNullOrBlank()) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("error", "EMPTY_CONTENT")
                            put("message", "content is required and must be non-blank")
                        }.toString()
                    )
                )
            }
            val setting = settingsStore.settingsFlow.first().getSelectedTTSProvider()
            if (setting == null) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("error", "TTS_NOT_CONFIGURED")
                            put("message", "未配置语音合成(TTS),无法发语音条;请直接改用文字回复")
                        }.toString()
                    )
                )
            }
            val payload = runCatching {
                val response = TtsSynthesizer(ttsManager).synthesize(setting, TtsChunk(index = 0, text = content.take(500)))
                val dir = File(context.filesDir, "voice_replies").apply { mkdirs() }
                val ext = when (response.format) {
                    AudioFormat.PCM -> "wav"
                    AudioFormat.WAV -> "wav"
                    AudioFormat.MP3 -> "mp3"
                    AudioFormat.OGG -> "ogg"
                    AudioFormat.AAC -> "m4a"
                    AudioFormat.OPUS -> "opus"
                }
                val bytes = if (response.format == AudioFormat.PCM) {
                    pcmToWav(response.audioData, response.sampleRate ?: 24000)
                } else {
                    response.audioData
                }
                val file = File(dir, "voice_${System.currentTimeMillis()}.$ext")
                file.writeBytes(bytes)
                // 时长估算仅对 WAV/PCM 可靠;其余格式交给播放器 prepared 后取真实时长
                val durationSec = if (response.format == AudioFormat.PCM || response.format == AudioFormat.WAV) {
                    val sampleRate = response.sampleRate ?: 24000
                    (bytes.size / 2 / sampleRate).coerceAtLeast(1)
                } else 0
                buildJsonObject {
                    put("success", true)
                    put("url", "file://${file.absolutePath}")
                    if (durationSec > 0) put("duration", durationSec)
                }.toString()
            }.getOrElse { e ->
                buildJsonObject {
                    put("error", "TTS_FAILED")
                    put("message", e.message ?: "TTS synthesis failed")
                }.toString()
            }
            listOf(UIMessagePart.Text(payload))
        }
    )
}
