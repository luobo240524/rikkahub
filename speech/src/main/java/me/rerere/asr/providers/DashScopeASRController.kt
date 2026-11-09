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

private const val TAG = "DashScopeASR"
private const val MAX_WEBSOCKET_QUEUE_BYTES = 100_000L
private const val SESSION_FINISH_TIMEOUT_MS = 10_000L

class DashScopeASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.DashScope
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var recorderJob: Job? = null
    private var finishTimeoutJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())
    private val partialTranscripts = ConcurrentHashMap<String, String>()
    // 断线自动重连:语音通话中网络抖动/服务端断开时重试,已识别文本保留
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private companion object {
        val MAX_RECONNECT_ATTEMPTS = 3
        val RECONNECT_DELAYS_MS = longArrayOf(1000L, 2000L, 4000L)
    }

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
        reconnectJob?.cancel()
        reconnectJob = null
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
                if (this@DashScopeASRController.webSocket !== webSocket ||
                    state.value.status != ASRStatus.Connecting
                ) {
                    webSocket.close(1000, "cancelled")
                    return
                }
                reconnectAttempts = 0
                webSocket.send(provider.sessionUpdateEvent().toString())
                _state.update { it.copy(status = ASRStatus.Listening, errorMessage = null) }
                startRecorder(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@DashScopeASRController.webSocket === webSocket) {
                    handleServerEvent(webSocket, text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val handshakeResult = response?.let { " (HTTP ${it.code} ${it.message})" }.orEmpty()
                Log.e(TAG, "DashScope ASR websocket failed$handshakeResult", t)
                if (this@DashScopeASRController.webSocket !== webSocket) return

                finishTimeoutJob?.cancel()
                finishTimeoutJob = null
                this@DashScopeASRController.webSocket = null
                releaseRecorder()

                // 断线自动重连:Listening/Connecting 态且非鉴权错误时,按退避重试;重连不清空已识别文本
                val httpCode = response?.code
                val retryable = state.value.status == ASRStatus.Listening ||
                    state.value.status == ASRStatus.Connecting
                if (!retryable || httpCode == 401 || httpCode == 403 ||
                    reconnectAttempts >= MAX_RECONNECT_ATTEMPTS
                ) {
                    setError(t.message ?: "ASR websocket failed")
                    return
                }
                val delayMs = RECONNECT_DELAYS_MS[reconnectAttempts.coerceIn(0, RECONNECT_DELAYS_MS.size - 1)]
                reconnectAttempts++
                // 关键:重连期间不置 errorMessage——listen() 对 errorMessage 非空一律判致命,
                // 之前设「连接断开,正在重连…」会把重连误杀成通话错误(重连形同虚设)
                _state.update {
                    it.copy(
                        status = ASRStatus.Connecting,
                        errorMessage = null
                    )
                }
                reconnectJob = scope.launch {
                    delay(delayMs)
                    if (state.value.status == ASRStatus.Connecting &&
                        this@DashScopeASRController.webSocket == null
                    ) {
                        connectSocket()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "DashScope ASR websocket closed: code=$code, reason=$reason")
                if (this@DashScopeASRController.webSocket !== webSocket) return

                finishTimeoutJob?.cancel()
                finishTimeoutJob = null
                this@DashScopeASRController.webSocket = null
                releaseRecorder()
                // 服务端主动断开(空闲超时等)在通话中是常态:与 onFailure 同样走退避重连,重试耗尽才 Idle
                val retryable = state.value.status == ASRStatus.Listening ||
                    state.value.status == ASRStatus.Connecting
                if (retryable && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    val delayMs = RECONNECT_DELAYS_MS[reconnectAttempts.coerceIn(0, RECONNECT_DELAYS_MS.size - 1)]
                    reconnectAttempts++
                    _state.update { it.copy(status = ASRStatus.Connecting, errorMessage = null) }
                    reconnectJob = scope.launch {
                        delay(delayMs)
                        if (state.value.status == ASRStatus.Connecting &&
                            this@DashScopeASRController.webSocket == null
                        ) {
                            connectSocket()
                        }
                    }
                } else {
                    _state.update {
                        it.copy(
                            status = ASRStatus.Idle,
                            errorMessage = null
                        )
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
        val wasListening = state.value.status == ASRStatus.Listening
        val activeRecorderJob = recorderJob
        val socket = webSocket
        if (socket == null && activeRecorderJob == null) {
            releaseRecorder()
            _state.update { it.copy(status = ASRStatus.Idle) }
            return
        }

        _state.update { it.copy(status = ASRStatus.Stopping) }
        activeRecorderJob?.cancel()
        // AudioRecord.read() is blocking, so stop it to let the cancelled recorder job finish.
        runCatching { audioRecord?.stop() }

        finishTimeoutJob?.cancel()
        finishTimeoutJob = scope.launch {
            // Do not enqueue session.finish until the capture loop can no longer send audio frames.
            activeRecorderJob?.join()
            releaseRecorder()

            if (socket == null) {
                _state.update { it.copy(status = ASRStatus.Idle) }
                return@launch
            }
            if (webSocket !== socket) return@launch

            if (!wasListening) {
                socket.cancel()
                webSocket = null
                _state.update { it.copy(status = ASRStatus.Idle) }
                return@launch
            }

            val finishSent = socket.send(sessionFinishEvent().toString())
            if (!finishSent) {
                Log.w(TAG, "Failed to finish DashScope ASR session; closing websocket")
                socket.cancel()
                webSocket = null
                _state.update { it.copy(status = ASRStatus.Idle) }
                return@launch
            }

            delay(SESSION_FINISH_TIMEOUT_MS)
            if (webSocket === socket) {
                Log.w(TAG, "Timed out waiting for session.finished; closing websocket")
                socket.close(1000, "session finish timeout")
                webSocket = null
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        reconnectJob?.cancel()
        reconnectJob = null
        releaseRecorder()
        finishTimeoutJob?.cancel()
        finishTimeoutJob = null
        webSocket?.cancel()
        webSocket = null
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder(socket: WebSocket) {
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
                                .put("event_id", "evt_${System.currentTimeMillis()}")
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

    private fun handleServerEvent(socket: WebSocket, text: String) {
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

            "conversation.item.input_audio_transcription.text" -> {
                val itemId = event.optString("item_id", "default")
                val preview = event.optString("text") + event.optString("stash")
                if (preview.isNotEmpty()) {
                    partialTranscripts[itemId] = preview
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
                val itemId = event.optString("item_id", "default")
                val error = event.optJSONObject("error")
                val message = error?.optString("message") ?: "ASR transcription failed"
                Log.e(TAG, "DashScope ASR transcription failed: $message")
                partialTranscripts.remove(itemId)
                publishTranscript()
                _state.update { it.copy(errorMessage = message) }
            }

            "error" -> {
                val error = event.optJSONObject("error")
                val message = error?.optString("message") ?: "ASR realtime error"
                Log.e(TAG, "DashScope ASR server error: $message")
                setError(message)
            }

            "session.finished" -> {
                finishTimeoutJob?.cancel()
                finishTimeoutJob = null
                socket.close(1000, "session finished")
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

private fun sessionFinishEvent(): JSONObject {
    return JSONObject()
        .put("event_id", "evt_session_finish_${System.currentTimeMillis()}")
        .put("type", "session.finish")
}

private fun ASRProviderSetting.DashScope.websocketEndpoint(): String {
    val endpoint = websocketUrl
        .trim()
        .trimEnd('/')
        .replace("/api-ws/v1/inference", "/api-ws/v1/realtime")
    val separator = if (endpoint.contains("?")) "&" else "?"
    return if (endpoint.contains("model=")) endpoint
    else "${endpoint}${separator}model=${model}"
}

private fun ASRProviderSetting.DashScope.sessionUpdateEvent(): JSONObject {
    val transcription = JSONObject()
    if (language.isNotBlank()) transcription.put("language", language)

    val session = JSONObject()
        .put("input_audio_format", "pcm")
        .put("sample_rate", sampleRate)
        .put("input_audio_transcription", transcription)

    session.put(
        "turn_detection",
        JSONObject()
            .put("type", "server_vad")
            // 阈值 <=0 视为未配置(旧版本默认 0.0 会传给服务端导致 VAD 异常),省略让服务端用默认灵敏度
            .apply { if (vadThreshold > 0f) put("threshold", vadThreshold) }
            .put("silence_duration_ms", silenceDurationMs)
    )

    return JSONObject()
        .put("event_id", "evt_session_update")
        .put("type", "session.update")
        .put("session", session)
}
