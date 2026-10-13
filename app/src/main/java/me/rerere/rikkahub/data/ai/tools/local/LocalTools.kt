package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
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
        return tools
    }
}
