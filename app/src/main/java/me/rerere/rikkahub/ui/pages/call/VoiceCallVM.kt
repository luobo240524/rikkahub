package me.rerere.rikkahub.ui.pages.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionController
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.utils.stripMarkdown
import kotlin.uuid.Uuid

/**
 * 打电话式语音通话(发现页入口):独立于聊天页。
 * 会话挑选沿用 SocialNotifyController 的策略:上次打开的会话(须属于当前助手)→ 当前助手最近会话。
 * 回复链路 = chatService.enqueueVoiceMessage(实时 ASR → LLM → 流式 TTS)。
 */
class VoiceCallVM(
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    private val conversationRepository: ConversationRepository,
    private val context: Context,
) : ViewModel() {
    private val controller = VoiceSessionController(viewModelScope, context::getString, ::enqueueText)
    val state: StateFlow<VoiceSessionState> = controller.state

    private var conversationId: Uuid? = null
    var assistantName: String = "助手"
        private set
    var assistantAvatar: Avatar = Avatar.Dummy
        private set

    /** 指定通话对象(侧边栏拨打面板传入);null = 当前助手 */
    private var targetAssistantId: String? = null

    /** 是否来电接听(TA 主动打来,用户点了「接听」);false = 用户主动拨打 */
    private var incomingCall: Boolean = false

    /** 通话开始时间戳:挂断时算通话时长 */
    private var callStartedAtMs: Long? = null

    /** 是否 TA 主动挂断(hang_up_call 工具);false = 用户挂断 */
    private var endedByAssistant: Boolean = false

    fun setTargetAssistant(id: String?) {
        targetAssistantId = id
    }

    fun setIncomingCall(incoming: Boolean) {
        incomingCall = incoming
    }

    /** TA 调用 hang_up_call 请求挂断:先标记再停,挂断通知按「TA挂断」落 */
    fun markAssistantHangup() {
        endedByAssistant = true
    }

    /** 通话页收到挂断请求事件:会话匹配才挂(防跨会话误触发);返回是否处理了 */
    fun handleHangupRequest(requestConversationId: Uuid): Boolean {
        if (requestConversationId != conversationId) return false
        markAssistantHangup()
        stopCall()
        return true
    }

    private val latestReplyFlow = MutableStateFlow("")
    val latestReply: StateFlow<String> = latestReplyFlow

    /** 本次通话新增回复的原始文本(随流式生成增长,不截断不去markdown):供通话页边生成边按句播报 */
    private val streamingReplyFlow = MutableStateFlow("")
    val streamingReply: StateFlow<String> = streamingReplyFlow

    /** 通话前已存在的助手消息 id:字幕只显示本次通话新增的回复,旧消息不再反复闪现/跳动 */
    private var preCallAssistantIds: Set<kotlin.uuid.Uuid> = emptySet()

    /** 本轮通话的会话 id:随消息元数据落库,聊天页按它把同一通电话的消息折叠成通话记录卡 */
    var callId: String = Uuid.random().toString()
        private set

    /** 挑会话并准备回复订阅;返回错误文案,null = 就绪 */
    suspend fun prepare(): String? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = targetAssistantId?.let { tid ->
            settings.assistants.firstOrNull { it.id.toString() == tid }
        } ?: settings.getCurrentAssistant()
        assistantName = assistant.name.ifBlank { "助手" }
        assistantAvatar = assistant.avatar
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
        viewModelScope.launch {
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

    fun startCall(
        createAsr: () -> me.rerere.asr.ASRController,
        speak: (suspend (String, Boolean) -> Unit)?,
        stopSpeaking: () -> Unit,
    ) {
        callStartedAtMs = System.currentTimeMillis()
        controller.start(createAsr, speak, stopSpeaking, streamingReply)
    }

    /**
     * 挂断:结束会话,并把通话事实告知助手(落隐藏备注+可选回应生成)。
     * 拒接/超时未接走 IncomingCallActivity.notifyCallDeclined,不经过这里。
     */
    fun stopCall() {
        controller.stop()
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
