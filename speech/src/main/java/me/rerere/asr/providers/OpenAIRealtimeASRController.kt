package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import me.rerere.asr.SilenceGuard
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "OpenAIRealtimeASR"
private const val MAX_WEBSOCKET_QUEUE_BYTES = 100_000L
private val RECONNECT_DELAYS_MS = longArrayOf(1000L, 2000L, 4000L)

class OpenAIRealtimeASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.OpenAIRealtime
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())
    private val partialTranscripts = ConcurrentHashMap<String, String>()
    // 断线自动重连:语音通话中网络抖动/服务端断开时重试,已识别文本保留
    private var reconnectAttempts = 0

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        completedTranscripts.clear()
        partialTranscripts.clear()
        reconnectAttempts = 0
        _state.update {
            ASRState(
                status = ASRStatus.Connecting,
                isAvailable = true
            )
        }
        connectSocket()

    }

    /** 建立 websocket 会话(start 与断线重连共用) */
    private fun connectSocket() {
        val request = Request.Builder()
            .url(provider.websocketEndpoint())
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@OpenAIRealtimeASRController.webSocket !== webSocket ||
                    state.value.status != ASRStatus.Connecting
                ) {
                    webSocket.cancel()
                    return
                }
                reconnectAttempts = 0
                webSocket.send(provider.sessionUpdateEvent().toString())
                _state.update { it.copy(status = ASRStatus.Listening, errorMessage = null) }
                startRecorder(provider, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@OpenAIRealtimeASRController.webSocket === webSocket) handleServerEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@OpenAIRealtimeASRController.webSocket !== webSocket) return
                Log.e(TAG, "Realtime ASR websocket failed", t)
                releaseRecorder()
                // 断线自动重连:Connecting/Listening 态按退避重试(语音通话中网络抖动不再直接终局)
                val retryable = state.value.status == ASRStatus.Connecting ||
                    state.value.status == ASRStatus.Listening
                if (!retryable || reconnectAttempts >= RECONNECT_DELAYS_MS.size) {
                    setError(t.message ?: "ASR websocket failed")
                    return
                }
                val delayMs = RECONNECT_DELAYS_MS[reconnectAttempts]
                reconnectAttempts++
                _state.update { it.copy(status = ASRStatus.Connecting, errorMessage = null) }
                val callback = onTranscriptChange
                scope.launch {
                    delay(delayMs)
                    if (state.value.status == ASRStatus.Connecting && webSocket == null && callback != null) {
                        connectSocket()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@OpenAIRealtimeASRController.webSocket !== webSocket) return
                releaseRecorder()
                // 服务端主动断开同样走重连(空闲超时断开在通话中是常态),重试耗尽才回到 Idle
                val retryable = state.value.status == ASRStatus.Listening ||
                    state.value.status == ASRStatus.Connecting
                if (!retryable || reconnectAttempts >= RECONNECT_DELAYS_MS.size) {
                    _state.update { it.copy(status = ASRStatus.Idle, errorMessage = null) }
                    return
                }
                val delayMs = RECONNECT_DELAYS_MS[reconnectAttempts]
                reconnectAttempts++
                _state.update { it.copy(status = ASRStatus.Connecting, errorMessage = null) }
                scope.launch {
                    delay(delayMs)
                    if (state.value.status == ASRStatus.Connecting && webSocket == null) {
                        connectSocket()
                    }
                }
            }
        })
    }

    override fun pauseCapture() {
        recorderJob?.cancel()
        runCatching { audioRecord?.stop() }
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        val socket = webSocket
        if (socket != null) {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            scope.launch {
                delay(500)
                socket.close(1000, "stop")
                if (webSocket === socket) {
                    webSocket = null
                    _state.update { it.copy(status = ASRStatus.Idle) }
                }
            }
        } else {
            _state.update { it.copy(status = ASRStatus.Idle) }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        val socket = webSocket
        webSocket = null
        socket?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder(
        provider: ASRProviderSetting.OpenAIRealtime,
        socket: WebSocket
    ) {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                provider.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(provider.sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            // 静音守卫:VOICE_COMMUNICATION 构造成功但输出近零电平时,服务端 VAD 永不断句 → 卡「我在听」
            var source: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            fun buildRecorder(): AudioRecord = AudioRecord(
                source,
                provider.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            var micFallbackUsed = false
            var needRebuildMic = false
            var silentError = false
            val silenceGuard = SilenceGuard(onSilent = {
                if (!micFallbackUsed) {
                    micFallbackUsed = true
                    needRebuildMic = true
                    Log.w(TAG, "VOICE_COMMUNICATION 音源静音,回退 MIC 音源重建录音器")
                } else {
                    silentError = true
                }
            })

            try {
                var recorder = buildRecorder()
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    // VOICE_COMMUNICATION 音源在部分国产 ROM 上不可用(或被占用),回退 MIC 重试
                    recorder.release()
                    source = MediaRecorder.AudioSource.MIC
                    micFallbackUsed = true
                    recorder = buildRecorder()
                }
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    throw IllegalStateException("麦克风初始化失败:设备不支持当前采样率/配置")
                }
                audioRecord = recorder
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("麦克风启动失败:可能被其他应用占用(另一路语音输入/通话中)或权限未授权")
                }
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    if (needRebuildMic) {
                        needRebuildMic = false
                        runCatching { recorder.stop() }
                        releaseRecorder()
                        recorder = buildRecorder()
                        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败(MIC 音源):设备不支持当前采样率/配置" }
                        audioRecord = recorder
                        recorder.startRecording()
                        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风启动失败(MIC 音源):可能被其他应用占用" }
                        silenceGuard.reset()
                        continue
                    }
                    if (silentError) {
                        throw IllegalStateException("麦克风没有检测到有效声音输入(已回退 MIC 仍静音):检查麦克风权限/遮挡,或换 ASR 供应商")
                    }
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        silenceGuard.onSample(amplitude)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                        if (socket.queueSize() < MAX_WEBSOCKET_QUEUE_BYTES) {
                            val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                            val event = JSONObject()
                                .put("type", "input_audio_buffer.append")
                                .put("audio", encoded)
                            socket.send(event.toString())
                        } else {
                            Log.w(TAG, "WebSocket queue full, dropping audio frame")
                        }
                    } else if (read == 0) {
                        // 偶发零读:跳过本帧,避免静默空转
                        continue
                    } else if (read < 0) {
                        // stop/release 与阻塞 read 的竞态会让 read 返回负值:
                        // 已不在录音态 = 主动停止,静默退出;否则才是真错误
                        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) break
                        throw IllegalStateException("AudioRecord read error: $read")
                        }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Audio recording failed", e)
                    setError(e.message ?: "Audio recording failed")
                }
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun handleServerEvent(text: String) {
        val event = runCatching { JSONObject(text) }.getOrElse {
            Log.w(TAG, "Invalid realtime event: $text", it)
            return
        }

        when (val type = event.optString("type")) {
            "input_audio_buffer.speech_started" -> {
                val id = event.optString("item_id")
                _state.update { it.copy(voiceTurn = it.voiceTurn.started(id)) }
            }

            "input_audio_buffer.speech_stopped" -> {
                val id = event.optString("item_id")
                _state.update { it.copy(voiceTurn = it.voiceTurn.stopped(id)) }
            }

            "conversation.item.input_audio_transcription.delta" -> {
                val itemId = event.optString("item_id", "default")
                val delta = event.optString("delta")
                if (delta.isNotEmpty()) {
                    partialTranscripts[itemId] = (partialTranscripts[itemId] ?: "") + delta
                    publishTranscript()
                }
            }

            "conversation.item.input_audio_transcription.completed" -> {
                val itemId = event.optString("item_id", "default")
                val transcript = event.optString("transcript").trim()
                _state.update { it.copy(voiceTurn = it.voiceTurn.completed(itemId, transcript)) }
                partialTranscripts.remove(itemId)
                if (transcript.isNotEmpty()) {
                    completedTranscripts.add(transcript)
                }
                publishTranscript()
            }

            "conversation.item.input_audio_transcription.failed" -> {
                setError(event.optJSONObject("error")?.optString("message") ?: "ASR transcription failed")
            }

            "error" -> {
                val error = event.optJSONObject("error")
                setError(error?.optString("message") ?: "ASR realtime error")
            }

            else -> {
                Log.v(TAG, "Ignored realtime event: $type")
            }
        }
    }

    private fun publishTranscript() {
        val transcript = (completedTranscripts + partialTranscripts.values)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch {
            onTranscriptChange?.invoke(transcript)
        }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }
}

private fun ASRProviderSetting.OpenAIRealtime.websocketEndpoint(): String {
    val endpoint = websocketUrl.trim()
    if (endpoint.contains("intent=transcription")) return endpoint
    if (endpoint.contains("model=")) return endpoint
    val separator = if (endpoint.contains("?")) "&" else "?"
    return "${endpoint.trimEnd('/')}${separator}intent=transcription"
}

private fun ASRProviderSetting.OpenAIRealtime.sessionUpdateEvent(): JSONObject {
    val transcription = JSONObject()
        .put("model", model)
    if (language.isNotBlank()) transcription.put("language", language)
    if (prompt.isNotBlank()) transcription.put("prompt", prompt)

    return JSONObject()
        .put("type", "session.update")
        .put(
            "session",
            JSONObject()
                .put("type", "transcription")
                .put(
                    "audio",
                    JSONObject()
                        .put(
                            "input",
                            JSONObject()
                                .put(
                                    "format",
                                    JSONObject()
                                        .put("type", "audio/pcm")
                                        .put("rate", sampleRate)
                                )
                                .put("transcription", transcription)
                                .put(
                                    "noise_reduction",
                                    JSONObject()
                                        .put("type", "near_field")
                                )
                                .put(
                                    "turn_detection",
                                    JSONObject()
                                        .put("type", "server_vad")
                                        .put("threshold", vadThreshold)
                                        .put("prefix_padding_ms", prefixPaddingMs)
                                        // 存量迁移:旧默认500ms断句太灵敏(说话停顿就被切走),仍等于旧默认的
                                        // 静默升到1200ms;用户手动调过的值不动
                                        .put("silence_duration_ms", if (silenceDurationMs == 500) 1200 else silenceDurationMs)
                                )
                        )
                )
        )
}
