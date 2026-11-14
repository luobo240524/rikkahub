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

/** 自动重试上限:生成失败重发同一句 / ASR 失败重建重听,各限 2 次,成功即归零,超限才落 Error(手动重试) */
private const val MAX_GEN_RETRIES = 2
private const val MAX_ASR_RETRIES = 2

/** 软切句标点:久等不到句末标点时从逗号/顿号先开口,压首音频延迟(至少积累 24 字才软切,切点至少第 8 字后) */
private const val SOFT_SENTENCE_ENDERS = "，,、"

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
        /** retryText 非空 = 生成失败(可重发该句);null = ASR/监听失败(可重建重听) */
        data class Failed(val error: Exception, val retryText: String? = null) : Event
    }

    fun start(
        createAsr: () -> ASRController,
        speak: (suspend (String, Boolean) -> Unit)?,
        stopSpeaking: () -> Unit,
        observeReply: StateFlow<String>? = null,
    ) {
        // speak(text, continuation):continuation=false 整段/首句(flush 重开),true=同一轮的后续句(追加,不重置合成器)
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
        speak: (suspend (String, Boolean) -> Unit)?,
        observeReply: StateFlow<String>?,
    ) = coroutineScope {
        // 流式模式:通话页传入 observeReply(新助手回复原文,随生成增长),边生成边按句播报;
        // 聊天页语音模式不传,沿用「整段生成完再念」的老路径
        val streamingMode = speak != null && observeReply != null
        val events = Channel<Event>(Channel.UNLIMITED)
        val submitted = Channel<Pair<String, Deferred<String?>>>(Channel.UNLIMITED)
        val replies = ArrayDeque<String>()
        var speakingJob: Job? = null
        var speaking = false
        var asr: ASRController? = null
        var capture: Job? = null
        // 连续空转写计数:VAD 断句但文字为空会被静默丢弃,之前会无限循环卡「我在听」;连续 3 次给出可见错误
        var blankUtterances = 0
        // 自动重试计数:失败退避重试,收到有效轮次即归零;超限才把通话打成 Error
        var genRetries = 0
        var asrRetries = 0

        // 提交一句话给聊天队列并按需启动流式播报;失败重试也走同一条路
        suspend fun submitUtterance(text: String) {
            val reply = enqueueMessage(text)
            mutableState.update { it.copy(transcript = text, pendingReplies = it.pendingReplies + 1) }
            submitted.send(text to reply)
            if (streamingMode) {
                speaking = true
                mutableState.update { it.copy(phase = VoicePhase.Responding) }
                speakingJob = launch {
                    try {
                        streamSpeakTurn(reply, observeReply!!, speak!!)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        events.send(Event.Failed(e, retryText = text))
                    }
                }
            }
        }

        // Await replies in submission order, without blocking capture. Queue removal returns null.
        launch {
            var currentText = ""
            try {
                for ((text, reply) in submitted) {
                    currentText = text
                    events.send(Event.Reply(reply.await()))
                }
            } catch (e: Exception) {
                if (e is CancellationException && !isActive) throw e
                // 带上失败那句话,主循环可退避重发同一句
                events.send(Event.Failed(e, retryText = currentText))
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
                    speak(replies.removeFirst(), false)
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
                            asrRetries = 0
                            submitUtterance(event.text)
                        } else {
                            blankUtterances++
                            if (blankUtterances >= 3) {
                                throw IllegalStateException("连续 $blankUtterances 次没有识别到有效语音:检查麦克风权限/遮挡,或换 ASR 供应商试试")
                            }
                        }
                    }
                    is Event.Reply -> {
                        genRetries = 0
                        mutableState.update { it.copy(pendingReplies = (it.pendingReplies - 1).coerceAtLeast(0)) }
                        if (!streamingMode) {
                            event.text?.takeIf { speak != null && it.isNotBlank() }?.let { replies.addLast(it) }
                        }
                    }
                    is Event.Failed -> {
                        val retryText = event.retryText
                        if (retryText != null && genRetries < MAX_GEN_RETRIES) {
                            // 生成失败:退避后重发同一句,通话不轻易断(用户观感=「TA想一下再说」)
                            genRetries++
                            mutableState.update { it.copy(pendingReplies = (it.pendingReplies - 1).coerceAtLeast(0)) }
                            delay(800L * genRetries)
                            submitUtterance(retryText)
                        } else if (retryText == null && asrRetries < MAX_ASR_RETRIES) {
                            // ASR/监听失败(连接超时/断连/静默超时):重建识别器重听
                            asrRetries++
                            capture?.cancel()
                            capture?.join()
                            capture = null
                            asr = null
                            delay(500L * asrRetries)
                        } else {
                            throw event.error
                        }
                    }
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
        speakFn: suspend (String, Boolean) -> Unit,
    ) {
        var spokenEnd = observe.value.stripMarkdown().length
        // 本轮已开过口:首句 flush 重开合成器,后续句追加进队列(不重置,预取缓存保留)
        var continuation = false
        while (currentCoroutineContext().isActive) {
            val clean = observe.value.stripMarkdown()
            if (clean.length > spokenEnd) {
                val rest = clean.substring(spokenEnd)
                // 硬切:句末标点;软切:积累≥24字仍无句末标点时,从最近的逗号/顿号先开口(长句不再干等到句号)
                val hardCut = rest.indexOfLast { SENTENCE_ENDERS.indexOf(it) >= 0 }
                val softCut = if (hardCut < 0 && rest.length >= 24) {
                    rest.indexOfLast { SOFT_SENTENCE_ENDERS.indexOf(it) >= 0 }
                } else -1
                val cut = if (hardCut >= 0) hardCut else if (softCut >= 8) softCut else -1
                if (cut >= 0) {
                    val sentence = rest.substring(0, cut + 1).trim()
                    spokenEnd += cut + 1
                    if (sentence.isNotBlank()) {
                        mutableState.update { it.copy(phase = VoicePhase.Speaking) }
                        speakFn(sentence, continuation)
                        continuation = true
                        delay(150) // 句间小间隔(原250ms,配合不重置合成器可更紧凑)
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
                speakFn(tail, continuation)
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
