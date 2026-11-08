package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.stickers.StickerStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.WakeRegistry
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
    private val stickerStore: StickerStore,
    private val messageFtsManager: MessageFtsManager,
    private val conversationRepository: ConversationRepository,
    private val wakeRegistry: WakeRegistry,
    private val moodCheckinDAO: MoodCheckinDAO,
    private val momentsPostDAO: MomentsPostDAO,
    private val socialInteractionDAO: SocialInteractionDAO,
    private val secretDAO: SecretDAO,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus, ttsManager, settingsStore) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    val chartDisplayTool by lazy { buildChartDisplayTool() }

    fun sendStickerTool(limit: Int): Tool = buildSendStickerTool(stickerStore, limit)

    fun searchMessagesTool(assistantId: kotlin.uuid.Uuid): Tool =
        buildSearchMessagesTool(messageFtsManager, assistantId)

    fun searchMessageDetailTool(assistantId: kotlin.uuid.Uuid): Tool =
        buildSearchMessageDetailTool(conversationRepository, assistantId)

    fun wakeRecordsTool(conversationId: kotlin.uuid.Uuid): Tool =
        buildWakeRecordsTool(wakeRegistry, conversationId)

    val moodCheckinTool by lazy { buildMoodCheckinTool(moodCheckinDAO) }

    val moodReadTool by lazy { buildMoodReadTool(moodCheckinDAO) }

    val moodInteractTool by lazy {
        buildSocialInteractTool(
            name = "mood_interact",
            targetType = "checkin",
            idParamName = "checkin_id",
            targetLabel = "mood check-in",
            socialInteractionDAO = socialInteractionDAO,
            targetExists = { id -> moodCheckinDAO.getById(id) != null },
        )
    }

    fun momentsPostTool(conversationId: kotlin.uuid.Uuid?): Tool =
        buildMomentsPostTool(momentsPostDAO, conversationId)

    val momentsReadTool by lazy { buildMomentsReadTool(momentsPostDAO) }

    val momentsInteractTool by lazy {
        buildSocialInteractTool(
            name = "moments_interact",
            targetType = "moment",
            idParamName = "post_id",
            targetLabel = "moment post",
            socialInteractionDAO = socialInteractionDAO,
            targetExists = { id -> momentsPostDAO.getById(id) != null },
        )
    }

    val secretsReadTool by lazy { buildSecretsReadTool(secretDAO) }

    val secretsWriteTool by lazy { buildSecretsWriteTool(secretDAO) }

    val secretsInteractTool by lazy {
        buildSocialInteractTool(
            name = "secrets_interact",
            targetType = "secret",
            idParamName = "secret_id",
            targetLabel = "secret note",
            socialInteractionDAO = socialInteractionDAO,
            targetExists = { id -> secretDAO.getById(id) != null },
        )
    }

    fun getTools(assistant: Assistant, conversationId: kotlin.uuid.Uuid? = null): List<Tool> {
        val options = assistant.localTools
        val stickerLimit = assistant.stickerToolLimit
        val assistantId = assistant.id
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ScreenTime)) {
            tools.add(screenTimeTool)
        }
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(calendarQueryTool)
            tools.add(calendarCreateTool)
        }
        if (options.contains(LocalToolOption.ChartDisplay)) {
            tools.add(chartDisplayTool)
        }
        if (options.contains(LocalToolOption.SendSticker)) {
            tools.add(sendStickerTool(stickerLimit))
        }
        if (options.contains(LocalToolOption.SearchMessages)) {
            tools.add(searchMessagesTool(assistantId))
            tools.add(searchMessageDetailTool(assistantId))
        }
        if (assistant.autonomousWake && conversationId != null) {
            tools.add(wakeRecordsTool(conversationId))
        }
        if (options.contains(LocalToolOption.MoodCheckin)) {
            tools.add(moodCheckinTool)
            tools.add(moodReadTool)
            tools.add(moodInteractTool)
        }
        if (options.contains(LocalToolOption.Moments)) {
            tools.add(momentsPostTool(conversationId))
            tools.add(momentsReadTool)
            tools.add(momentsInteractTool)
        }
        // 心里话三件套无条件授权:读写互动不依赖助手本地工具开关
        // (开关缺失导致过"助手没有发心里话的权限",这类配置差异不再影响)
        tools.add(secretsReadTool)
        tools.add(secretsWriteTool)
        tools.add(secretsInteractTool)
        return tools
    }
}
