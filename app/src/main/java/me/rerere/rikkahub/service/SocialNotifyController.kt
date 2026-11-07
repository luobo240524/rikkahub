package me.rerere.rikkahub.service

import android.content.Context
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.hooks.readStringPreference
import java.util.concurrent.atomic.AtomicLong

/**
 * 社交通知:用户在发现页发布动态(朋友圈/打卡/心里话)或评论助手内容时,
 * 挑一个当前助手的会话,后台唤醒一次生成让助手互动。
 *
 * 设计约束:
 * - 助手自己的工具动作不经过这里(防自激励循环)
 * - 助手没开任何社交工具时直接跳过(开不了互动,也别吵聊天)
 * - 15 秒内存去抖,连发多条只提醒一次
 */
class SocialNotifyController(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
) {
    private val lastNotifyAt = AtomicLong(0)

    suspend fun notifyUserActivity(summary: String) {
        val now = System.currentTimeMillis()
        val last = lastNotifyAt.get()
        if (now - last < DEBOUNCE_MS) return
        if (!lastNotifyAt.compareAndSet(last, now)) return

        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getCurrentAssistant()
        if (!assistant.hasSocialTools()) return
        val conversationId = pickConversation(assistant) ?: return
        chatService.triggerSocialNotify(conversationId, summary)
    }

    /**
     * 目标会话:优先取上次打开的会话(须属于当前助手),否则取当前助手最近的会话。
     * 一个会话都没有就不提醒(没有可附着的聊天上下文)。
     */
    private suspend fun pickConversation(assistant: Assistant): kotlin.uuid.Uuid? {
        val lastId = context.readStringPreference("lastConversationId", null)
        if (lastId != null) {
            runCatching {
                val uuid = kotlin.uuid.Uuid.parse(lastId)
                val conv = conversationRepo.getConversationById(uuid)
                if (conv?.assistantId == assistant.id) return uuid
            }
        }
        return conversationRepo.getRecentConversations(assistant.id, 1)
            .firstOrNull()?.id
    }

    private fun Assistant.hasSocialTools(): Boolean = localTools.any {
        it == LocalToolOption.Moments || it == LocalToolOption.MoodCheckin || it == LocalToolOption.Secrets
    }

    companion object {
        private const val DEBOUNCE_MS = 15_000L
    }
}
