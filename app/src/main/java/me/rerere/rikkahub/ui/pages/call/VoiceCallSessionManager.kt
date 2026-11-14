package me.rerere.rikkahub.ui.pages.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.asr.providers.VolcengineASRController
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.VoiceCallForegroundService
import me.rerere.rikkahub.ui.hooks.CustomTtsStateImpl
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionController
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.stripMarkdown
import okhttp3.OkHttpClient
import kotlin.uuid.Uuid

/**
 * 语音通话全局会话(Koin 单例):原 VoiceCallVM 逻辑 + 页面级 TTS/ASR 构建全部收拢到这里。
 * 通话页只是绑定 UI:返回键/退桌面 = 最小化,通话在后台继续挂(FGS microphone 保活);
 * 挂断红钮 / TA hang_up_call 才真正结束通话。
 */
class VoiceCallSessionManager(
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    private val conversationRepository: ConversationRepository,
    private val context: Context,
    private val client: OkHttpClient,
    private val appScope: AppScope,
    private val eventBus: AppEventBus,
) {
    private val controller = VoiceSessionController(appScope, context::getString, ::enqueueText)
    val state: StateFlow<VoiceSessionState> = controller.state

    // 通话页专属 TTS 实例:全局实例会被 text_to_speech 工具/消息朗读抢占重置,导致卡「正在说」。
    // 挂在 manager 而非页面组合里:页面最小化后播报继续
    val tts = CustomTtsStateImpl(context.applicationContext, settingsStore)

    private val latestReplyFlow = MutableStateFlow("")
    val latestReply: StateFlow<String> = latestReplyFlow

    /** 本次通话新增回复的原始文本(随流式生成增长,不截断不去markdown):供通话页边生成边按句播报 */
    private val streamingReplyFlow = MutableStateFlow("")
    val streamingReply: StateFlow<String> = streamingReplyFlow

    private val assistantNameFlow = MutableStateFlow("助手")
    val assistantName: StateFlow<String> = assistantNameFlow

    private val assistantAvatarFlow = MutableStateFlow<Avatar>(Avatar.Dummy)
    val assistantAvatar: StateFlow<Avatar> = assistantAvatarFlow

    private var conversationId: Uuid? = null

    /** 指定通话对象(侧边栏拨打面板传入);null = 当前助手。悬浮球回通话页要用 */
    var targetAssistantId: String? = null
        private set

    /** 是否来电接听(TA 主动打来,用户点了「接听」);false = 用户主动拨打 */
    private var incomingCall: Boolean = false

    /** 通话开始时间戳:挂断时算通话时长;通话页计时也按它换算(回绑不归零) */
    var callStartedAtMs: Long? = null
        private set

    /** 是否 TA 主动挂断(hang_up_call 工具);false = 用户挂断 */
    private var endedByAssistant: Boolean = false

    /** 通话前已存在的助手消息 id:字幕只显示本次通话新增的回复,旧消息不再反复闪现/跳动 */
    private var preCallAssistantIds: Set<Uuid> = emptySet()

    /** 本轮通话的会话 id:随消息元数据落库,聊天页按它把同一通电话的消息折叠成通话记录卡 */
    var callId: String = Uuid.random().toString()
        private set

    /** 会话流订阅:重进通话页会重新 prepare,先取消旧订阅防重复收集 */
    private var replyObserverJob: Job? = null

    init {
        // TTS 供应商/音色设置变更时同步(原 rememberCustomTtsState 的 DisposableEffect 逻辑,
        // 与 Settings.getSelectedTTSProvider 同口径:选中 id 命中则用,否则第一个)
        appScope.launch {
            settingsStore.settingsFlow
                .map {
                    Triple(it.selectedTTSProviderId, it.ttsProviders, it.defaultTTSPlaybackSpeed)
                }
                .distinctUntilChanged()
                .collect { (selectedId, providers, speed) ->
                    tts.updateProvider(selectedId?.let { id -> providers.firstOrNull { p -> p.id == id } } ?: providers.firstOrNull())
                    tts.setSpeed(speed)
                }
        }
        // TA 调用 hang_up_call 请求挂断:会话匹配才挂(防跨会话误触发);页面只负责跟着退场
        appScope.launch {
            eventBus.events.collect { event ->
                if (event is AppEvent.VoiceCallHangupRequest) {
                    handleHangupRequest(event.conversationId)
                }
            }
        }
    }

    fun setTargetAssistant(id: String?) {
        targetAssistantId = id
    }

    fun setIncomingCall(incoming: Boolean) {
        incomingCall = incoming
    }

    /** TA 调用 hang_up_call 请求挂断:先标记再停,挂断通知按「TA挂断」落;返回是否处理了 */
    fun handleHangupRequest(requestConversationId: Uuid): Boolean {
        if (requestConversationId != conversationId) return false
        endedByAssistant = true
        stopCall()
        return true
    }

    /** 挑会话并准备回复订阅;返回错误文案,null = 就绪 */
    suspend fun prepare(): String? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = targetAssistantId?.let { tid ->
            settings.assistants.firstOrNull { it.id.toString() == tid }
        } ?: settings.getCurrentAssistant()
        assistantNameFlow.value = assistant.name.ifBlank { "助手" }
        assistantAvatarFlow.value = assistant.avatar
        var picked: Uuid? = null
        val lastId = context.readStringPreference("lastConversationId", null)
        if (lastId != null) {
            runCatching {
                val uuid = Uuid.parse(lastId)
                val conv = conversationRepository.getConversationById(uuid)
                if (conv?.assistantId == assistant.id) picked = uuid
            }
        }
        if (picked == null) {
            picked = conversationRepository.getRecentConversations(assistant.id, 1)
                .firstOrNull()?.id
        }
        val id = picked ?: return "还没有和TA的对话,先聊几句再来电吧"
        conversationId = id
        // 每次进通话页 = 一通新电话,重新生成通话会话 id
        callId = Uuid.random().toString()
        // 订阅会话流,给字幕取最新一条助手回复。
        // 首个快照记录通话前已有的助手消息 id,之后只认新增回复(修旧消息在字幕区反复跳动)
        replyObserverJob?.cancel()
        replyObserverJob = appScope.launch {
            var firstSnapshot = true
            chatService.getConversationFlow(id).collect { conv ->
                // 首个快照先落「通话前已有消息」集合,再筛新增回复,否则历史全量灌进字幕/流
                if (firstSnapshot) {
                    preCallAssistantIds = conv.currentMessages
                        .filter { it.role == MessageRole.ASSISTANT }
                        .map { it.id }
                        .toSet()
                    firstSnapshot = false
                }
                val newReplies = conv.currentMessages.filter {
                    it.role == MessageRole.ASSISTANT && it.id !in preCallAssistantIds
                }
                latestReplyFlow.value = newReplies.lastOrNull()
                    ?.parts?.filterIsInstance<UIMessagePart.Text>()
                    ?.joinToString(" ") { it.text }
                    ?.stripMarkdown()?.take(140).orEmpty()
                // 流式播报源:全部新增回复原文拼接(与 ChatService 回复 Deferred 同源同口径)
                streamingReplyFlow.value = newReplies
                    .joinToString("\n") { it.toText() }
            }
        }
        return null
    }

    private fun enqueueText(text: String): Deferred<String?> {
        val id = conversationId
            ?: return CompletableDeferred(null)
        return chatService.enqueueVoiceMessage(
            conversationId = id,
            text = text,
            callId = callId,
            // 收纳卡显示「你拨打的/TA打来的」:来电接听=TA打来的
            callFrom = if (incomingCall) "assistant" else "user",
        )
    }

    /**
     * 起呼:前置检查(provider/模型)→ 拉前台服务(microphone 保活)→ 启动会话循环。
     * 返回错误文案,null = 已起呼。麦克风权限与「正在语音输入」检查由通话页做(依赖页面状态)。
     * Off/Error 均可起呼(通话中重进页面不会走到这里,Error 态允许「重试」重拨)。
     */
    fun startCall(): String? {
        val phase = state.value.phase
        if (phase != me.rerere.rikkahub.ui.pages.chat.VoicePhase.Off &&
            phase != me.rerere.rikkahub.ui.pages.chat.VoicePhase.Error
        ) return null
        val setting = settingsStore.settingsFlow.value
        val provider = setting.getSelectedASRProvider()
        val blocked = when {
            conversationId == null -> "通话未就绪,重进页面再试"
            provider == null -> "未配置语音识别(ASR),先到设置里配好"
            !provider.supportsServerVadVoiceMode -> "当前 ASR 不支持通话式自动断句,换 OpenAI Realtime/DashScope/火山引擎"
            setting.getCurrentChatModel() == null -> "请先选择模型"
            else -> null
        }
        if (blocked != null) return blocked
        callStartedAtMs = System.currentTimeMillis()
        VoiceCallForegroundService.acquire(context, assistantNameFlow.value)
        controller.start(
            createAsr = { createCallAsr(checkNotNull(provider)) },
            // TTS 统一传入:供应商异步装载,未就绪时在 speak 内等待(最多 6s),
            // 避免起呼瞬间 isAvailable=false 导致整通电话 speak=null、助手全程不出声
            // continuation=false=本轮首句(flush 重开);true=后续句(追加队列,不重置合成器,预取缓存保留)
            speak = ::speakTurn,
            stopSpeaking = tts::stop,
            observeReply = streamingReply,
        )
        return null
    }

    /** 与聊天页语音模式同源:建立 ASR 前请求独占音频焦点(通话用法)(自 VoiceCallPage 迁入) */
    private fun createCallAsr(provider: ASRProviderSetting): ASRController {
        val delegate = when (provider) {
            is ASRProviderSetting.OpenAIRealtime -> {
                check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
                OpenAIRealtimeASRController(context, client, provider)
            }
            is ASRProviderSetting.DashScope -> {
                check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
                DashScopeASRController(context, client, provider)
            }
            is ASRProviderSetting.Volcengine -> {
                check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
                VolcengineASRController(context, client, provider)
            }
            else -> error("当前 ASR 不支持通话式自动断句")
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        return object : ASRController by delegate {
            override fun start(onTranscriptChange: (String) -> Unit) {
                check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "无法取得麦克风音频焦点,稍后再试"
                }
                delegate.start(onTranscriptChange)
            }

            override fun dispose() {
                try {
                    delegate.dispose()
                } finally {
                    audioManager.abandonAudioFocusRequest(focus)
                }
            }
        }
    }

    /** TTS 播报一句:显示设置(只读引号/括号外)→ 去markdown → 等装载 → 合成播报;失败只跳过本句不炸通话 */
    private suspend fun speakTurn(reply: String, continuation: Boolean) {
        try {
            val setting = settingsStore.settingsFlow.value
            var text = reply
            if (setting.displaySetting.ttsOnlyReadQuoted) {
                text = text.extractQuotedContentAsText() ?: text
            }
            if (setting.displaySetting.ttsOnlyReadOutsideBrackets) {
                text = text.removeBracketedContent() ?: text
            }
            text = text.stripMarkdown()
            if (text.isNotBlank()) {
                if (!tts.isAvailable.value) {
                    // 等装载完成或等出错,超时由外层 runCatching 兜底跳过
                    withTimeoutOrNull(6_000) {
                        combine(tts.isAvailable, tts.error) { avail, err -> avail || err != null }
                            .first { it }
                    }
                }
                if (tts.isAvailable.value) {
                    tts.speak(text, flushCalled = !continuation)
                    // 合成/播放失败只跳过本句发声(字幕里仍有文字),不把整通电话打成 Error
                    combine(tts.isSpeaking, tts.error) { speaking, error ->
                        !speaking || error != null
                    }.first { it }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VoiceCallSession", "TTS speak skipped: ${e.message}")
        }
    }

    /**
     * 挂断:结束会话,并把通话事实告知助手(落隐藏备注+可选回应生成),释放前台服务。
     * 拒接/超时未接走 IncomingCallActivity.notifyCallDeclined,不经过这里。
     */
    fun stopCall() {
        controller.stop()
        VoiceCallForegroundService.release(context)
        val convId = conversationId
        val startedAt = callStartedAtMs
        if (convId != null && startedAt != null) {
            callStartedAtMs = null
            val durationSec = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            chatService.notifyCallEnded(
                conversationId = convId,
                initiatedByAssistant = incomingCall,
                answered = true,
                durationSec = durationSec,
                rounds = countCallRounds(convId),
                endedByAssistant = endedByAssistant,
            )
        }
        endedByAssistant = false
    }

    /** 本通电话的轮数 = 带 call_id 的用户消息数(每说一句算一轮) */
    private fun countCallRounds(convId: Uuid): Int {
        return runCatching {
            chatService.getConversationFlow(convId).value.currentMessages.count { message ->
                message.role == MessageRole.USER && message.parts.filterIsInstance<UIMessagePart.Text>()
                    .any { part ->
                        (part.metadata?.get("call_id") as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull == callId
                    }
            }
        }.getOrDefault(0)
    }
}
