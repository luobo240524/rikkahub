package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderException
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "DeepSeekTTSProvider"

/**
 * DeepSeek TTS:POST {baseUrl}/v1/tools/tts,JSON body {text, voice_id, format},
 * 响应体即完整音频二进制(wav 或 24kHz 16bit 单声道 pcm)。整段包成一个 AudioChunk 发出。
 *
 * 官方文档: https://api-docs.deepseek.com/zh-cn/guides/tts
 * 音色仅 stella(海星,女声)/ tide(暗潮,男声);不支持语速参数。
 * 服务端 filter_markdown 默认 true,朗读前自动去 Markdown。
 */
class DeepSeekTTSProvider : TTSProvider<TTSProviderSetting.DeepSeek> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.DeepSeek,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            put("voice_id", providerSetting.voiceId)
            put("format", providerSetting.format)
        }

        Log.i(
            TAG,
            "generateSpeech: voice=${providerSetting.voiceId} format=${providerSetting.format} chars=${request.text.length}"
        )

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/v1/tools/tts")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(
                    TAG,
                    "DeepSeek TTS request failed: ${response.code} ${response.message}, body: $errorBody"
                )
                throw TTSProviderException(
                    message = "DeepSeek TTS request failed: ${response.code} ${response.message}",
                    statusCode = response.code
                )
            }

            val audioData = response.body?.bytes() ?: ByteArray(0)
            if (audioData.isEmpty()) {
                throw TTSProviderException(
                    message = "DeepSeek TTS returned empty audio",
                    statusCode = response.code
                )
            }

            emit(
                AudioChunk(
                    data = audioData,
                    format = if (providerSetting.format.lowercase() == "pcm") {
                        AudioFormat.PCM
                    } else {
                        AudioFormat.WAV
                    },
                    sampleRate = 24000, // DeepSeek 固定 24kHz;wav 自带头,此值仅供 pcm 使用
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "deepseek",
                        "voice" to providerSetting.voiceId,
                        "format" to providerSetting.format,
                    )
                )
            )
        }
    }
}
