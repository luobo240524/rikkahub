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
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.SettingsStore
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

    private val latestReplyFlow = MutableStateFlow("")
    val latestReply: StateFlow<String> = latestReplyFlow

    /** 挑会话并准备回复订阅;返回错误文案,null = 就绪 */
    suspend fun prepare(): String? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getCurrentAssistant()
        assistantName = assistant.name.ifBlank { "助手" }
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
        // 订阅会话流,给字幕取最新一条助手回复
        viewModelScope.launch {
            chatService.getConversationFlow(id).collect { conv ->
                latestReplyFlow.value = conv.currentMessages
                    .lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?.parts?.filterIsInstance<UIMessagePart.Text>()
                    ?.joinToString(" ") { it.text }
                    ?.stripMarkdown()?.take(140).orEmpty()
            }
        }
        return null
    }

    private fun enqueueText(text: String): Deferred<String?> {
        val id = conversationId
            ?: return CompletableDeferred(null)
        return chatService.enqueueVoiceMessage(id, text)
    }

    fun startCall(
        createAsr: () -> me.rerere.asr.ASRController,
        speak: (suspend (String) -> Unit)?,
        stopSpeaking: () -> Unit,
    ) = controller.start(createAsr, speak, stopSpeaking)

    fun stopCall() = controller.stop()
}
