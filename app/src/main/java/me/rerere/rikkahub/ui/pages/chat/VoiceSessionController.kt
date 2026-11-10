package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rerere.asr.ASRController
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.MessageQueuePausedException
import me.rerere.rikkahub.utils.stripMarkdown

enum class VoicePhase { Off, Connecting, Listening, Transcribing, Responding, Speaking, Error }

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.Off,
    val transcript: String = "",
    val error: String? = null,
    val pendingReplies: Int = 0,
) {
    val isActive: Boolean get() = phase != VoicePhase.Off && phase != VoicePhase.Error
}

/**
 * 句末标点:流式模式下按这些字符切句送 TTS。
 */
private const val SENTENCE_ENDERS = "。！？!?；;\n"

/** Capture and generation run independently. Only TTS owns an exclusive microphone pause. */
class VoiceSessionController(
    private val scope: CoroutineScope,
    private val getString: (Int) -> String,
    private val enqueueMessage: (String) -> Deferred<String?>,
) {
    private val mutableState = MutableStateFlow(VoiceSessionState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    private sealed interface Event {
        data class Utterance(val text: String) : Event
        data class Reply(val text: String?) : Event
        data class Failed(val error: Exception) : Event
    }

    fun start(
        createAsr: () -> ASRController,
        speak: (suspend (String) -> Unit)?,
        stopSpeaking: () -> Unit,
        observeReply: StateFlow<String>? = null,
    ) {
        if (job?.isCompleted == false) return
        mutableState.value = VoiceSessionState(VoicePhase.Connecting)
        job = scope.launch {
            try {
                stopSpeaking()
                delay(200)
                runSession(createAsr, speak, observeReply)
            } catch (e: Exception) {
                if (e is CancellationException && !currentCoroutineContext().isActive) throw e
                mutableState.update {
                    it.copy(
                        phase = VoicePhase.Error,
                        error = when (e) {
                            is TimeoutCancellationException -> "Speech recognition timed out. Restart voice mode."
                            is MessageQueuePausedException -> getString(R.string.chat_page_voice_queue_paused)
                            else -> e.message ?: getString(R.string.chat_page_voice_failed)
                        },
                    )
                }
            } finally {
                stopSpeaking()
            }
        }
    }

    private suspend fun runSession(
        createAsr: () -> ASRController,
        speak: (suspend (String) -> Unit)?,
        observeReply: StateFlow<String>?,
    ) = coroutineScope {
        // 流式模式:通话页传入 observeReply(新助手回复原文,随生成增长),边生成边按句播报;
        // 聊天页语音模式不传,沿用「整段生成完再念」的老路径
        val streamingMode = speak != null && observeReply != null
        val events = Channel<Event>(Channel.UNLIMITED)
        val submitted = Channel<Deferred<String?>>(Channel.UNLIMITED)
        val replies = ArrayDeque<String>()
        var speakingJob: Job? = null
        var speaking = false
        var asr: ASRController? = null
        var capture: Job? = null
        // 连续空转写计数:VAD 断句但文字为空会被静默丢弃,之前会无限循环卡「我在听」;连续 3 次给出可见错误
        var blankUtterances = 0

        // Await replies in submission order, without blocking capture. Queue removal returns null.
        launch {
            try {
                for (reply in submitted) events.send(Event.Reply(reply.await()))
            } catch (e: Exception) {
                if (e is CancellationException && !isActive) throw e
                events.send(Event.Failed(e))
            }
        }

        try {
            while (isActive) {
                // Finish any sentence already in progress before giving TTS the microphone pause.
                if (!streamingMode && speak != null && replies.isNotEmpty() && asr?.state?.value?.voiceTurn?.itemId == null) {
                    capture?.cancelAndJoin()
                    capture = null
                    asr = null
                    mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                    speak(replies.removeFirst())
                    delay(300) // Let the loudspeaker's tail decay before opening the microphone.
                    continue
                }
                if (streamingMode && speaking) {
                    // 通话流式模式:生成+播报期间不开麦(像打电话,一人说完另一人再说),等播报结束
                    speakingJob?.join()
                    speaking = false
                    delay(300)
                    continue
                }
                if (capture == null) {
                    mutableState.update { it.copy(phase = VoicePhase.Connecting, transcript = "") }
                    val recorder = createAsr()
                    asr = recorder
                    capture = launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            events.send(Event.Utterance(listen(recorder)))
                        } catch (e: Exception) {
                            if (e is CancellationException && !isActive) throw e
                            events.send(Event.Failed(e))
                        }
                    }
                }
                when (val event = events.receive()) {
                    is Event.Utterance -> {
                        capture?.join()
                        capture = null
                        asr = null
                        if (event.text.isNotBlank()) {
                            blankUtterances = 0
                            val reply = enqueueMessage(event.text)
                            mutableState.update { it.copy(transcript = event.text, pendingReplies = it.pendingReplies + 1) }
                            submitted.send(reply)
                            if (streamingMode) {
                                speaking = true
                                mutableState.update { it.copy(phase = VoicePhase.Responding) }
                                speakingJob = launch {
                                    try {
                                        streamSpeakTurn(reply, observeReply!!, speak!!)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        events.send(Event.Failed(e))
                                    }
                                }
                            }
                        } else {
                            blankUtterances++
                            if (blankUtterances >= 3) {
                                throw IllegalStateException("连续 $blankUtterances 次没有识别到有效语音:检查麦克风权限/遮挡,或换 ASR 供应商试试")
                            }
                        }
                    }
                    is Event.Reply -> {
                        mutableState.update { it.copy(pendingReplies = (it.pendingReplies - 1).coerceAtLeast(0)) }
                        if (!streamingMode) {
                            event.text?.takeIf { speak != null && it.isNotBlank() }?.let { replies.addLast(it) }
                        }
                    }
                    is Event.Failed -> throw event.error
                }
            }
        } finally {
            capture?.cancel()
            speakingJob?.cancel()
            // Submitted messages belong to the chat queue; leaving voice mode only detaches observers.
            submitted.close()
        }
    }

    /**
     * 流式播报一轮回复:盯住 observeReply 的增长文本,句末标点一出现就送 TTS 开口,
     * reply Deferred 完成后念掉剩余尾巴。全程顺序执行,不与下一轮并发。
     * 尾巴也取自同一个流(Deferred 文本与流的空间在多轮累积时不一致,不能混用);
     * Deferred 完成后短暂等流稳定再算尾巴,避免最后一块文本还没进流就被漏掉。
     */
    private suspend fun streamSpeakTurn(
        reply: Deferred<String?>,
        observe: StateFlow<String>,
        speakFn: suspend (String) -> Unit,
    ) {
        var spokenEnd = observe.value.stripMarkdown().length
        while (currentCoroutineContext().isActive) {
            val clean = observe.value.stripMarkdown()
            if (clean.length > spokenEnd) {
                val rest = clean.substring(spokenEnd)
                val cut = rest.indexOfLast { SENTENCE_ENDERS.indexOf(it) >= 0 }
                if (cut >= 0) {
                    val sentence = rest.substring(0, cut + 1).trim()
                    spokenEnd += cut + 1
                    if (sentence.isNotBlank()) {
                        mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                        speakFn(sentence)
                        delay(250) // 喇叭尾音衰减再接下一句
                    }
                    continue
                }
            }
            if (reply.isCompleted) break
            delay(120)
        }
        // 失败要冒泡(Error 阶段),成功则继续算尾巴
        reply.await()
        var prev = observe.value
        var waited = 0
        while (waited < 1500) {
            delay(150); waited += 150
            val cur = observe.value
            if (cur == prev) {
                // 连续两轮采样不变视为已稳定
                delay(150); waited += 150
                if (observe.value == prev) break
            }
            prev = cur
        }
        val cleanFinal = prev.stripMarkdown()
        if (cleanFinal.length > spokenEnd) {
            val tail = cleanFinal.substring(spokenEnd).trim()
            if (tail.isNotBlank()) {
                mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                speakFn(tail)
            }
        }
    }

    private suspend fun listen(asr: ASRController): String {
        try {
            asr.start {}
            withTimeout(15_000) {
                asr.state.first {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    it.status != ASRStatus.Connecting
                }.also { check(it.status == ASRStatus.Listening || it.voiceTurn.isComplete) { "Unable to start speech recognition" } }
            }
            val ended = withTimeout(120_000) {
                asr.state.onEach {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    check(it.status == ASRStatus.Listening || it.voiceTurn.isComplete) { "Speech recognition disconnected" }
                    mutableState.update { current -> current.copy(phase = VoicePhase.Listening, transcript = it.transcript) }
                }.first { it.voiceTurn.speechEnded }
            }
            asr.pauseCapture()
            mutableState.update { it.copy(phase = VoicePhase.Transcribing, transcript = ended.transcript) }
            return withTimeout(15_000) {
                asr.state.first {
                    check(it.errorMessage == null) { it.errorMessage.orEmpty() }
                    check(it.voiceTurn.isComplete || it.status == ASRStatus.Listening) {
                        "Speech recognition disconnected before the final transcript was received"
                    }
                    it.voiceTurn.isComplete
                }.voiceTurn.finalText.orEmpty()
            }
        } finally {
            asr.dispose()
        }
    }

    fun stop() {
        job?.cancel()
        mutableState.value = VoiceSessionState()
    }
}
