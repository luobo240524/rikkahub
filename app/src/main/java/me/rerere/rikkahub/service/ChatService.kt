package me.rerere.rikkahub.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationLoop
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.StickerToolFoldTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

internal fun backgroundTextGenerationParams(
    model: Model,
    conversationId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = conversationId.toString(),
)

private val forkTitleSuffixRegex = Regex("""\((\d+)\)$""")

internal fun forkConversationTitle(sourceTitle: String, existingTitles: Set<String>): String {
    // 源标题已带 (N) 后缀时递增序号，避免多次 fork 后叠加成 xxx(1)(1)(1)
    val suffix = forkTitleSuffixRegex.find(sourceTitle)
    val baseTitle = suffix?.let { sourceTitle.removeRange(it.range) } ?: sourceTitle
    val start = suffix?.groupValues?.get(1)?.toIntOrNull()?.plus(1) ?: 1
    return generateSequence(start) { it + 1 }
        .map { "$baseTitle($it)" }
        .first { it !in existingTitles }
}

internal fun createForkConversation(
    source: Conversation,
    messageNodes: List<MessageNode>,
    existingTitles: Set<String> = emptySet(),
): Conversation = Conversation(
    id = Uuid.random(),
    assistantId = source.assistantId,
    title = forkConversationTitle(source.title, existingTitles),
    messageNodes = messageNodes,
    customSystemPrompt = source.customSystemPrompt,
    modeInjectionIds = source.modeInjectionIds,
    lorebookIds = source.lorebookIds,
    workspaceCwd = source.workspaceCwd,
    folderId = source.folderId,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

private val inputTransformers by lazy {
    listOf(
        // StickerToolFoldTransformer 已停用：历史折叠文本会被模型模仿（直接打字而不调工具），
        // 保留原生 tool_calls 结构 + 空 reasoning 回传兜底（ChatCompletionsAPI/ResponseAPI 已实现）
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationLoop: GenerationLoop,
    private val translationHandler: TranslationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val chatToolFactory: ChatToolFactory,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
) {
    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
    )

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        if (cause != null) session.messageQueue.pause()
        if (session.state.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchNextQueuedMessage(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize {
                conversationRepo.getConversationById(conversationId) ?: run {
                    // 新建对话, 并添加预设消息
                    val currentSettings = settingsStore.settingsFlowRaw.first()
                    val assistant = currentSettings.getCurrentAssistant()
                    Conversation.ofId(
                        id = conversationId,
                        assistantId = assistant.id,
                        newConversation = true
                    ).updateCurrentMessages(assistant.presetMessages)
                }
            }
            settingsStore.updateAssistant(session.state.value.assistantId)
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val content = queued.parts
        val answer = queued.answer
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = answer,
        ) {
            try {
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)
                session.submittingMessage = null

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                queued.reply?.completeWith(runCatching {
                    val messages = session.state.value.currentMessages
                    check(!session.messageQueue.state.value.paused) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check(messages.none { message -> message.parts.any { it is UIMessagePart.Tool && it.isPending } }) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    val previousIds = currentConversation.currentMessages.map { it.id }.toSet()
                    messages.filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = message.role == MessageRole.USER || regenerateAssistantMsg,
        ) {
            try {
                previousJob?.join()
                val conversation = session.state.value
                Logging.log(TAG, "regen: role=${message.role} nodes=${conversation.messageNodes.size}")

                if (message.role == MessageRole.USER) {
                    // 用户消息重回：截断到当前消息，但旧助手回复保留为版本分支（失败则恢复）
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val droppedNodes = conversation.messageNodes.drop(indexAt + 1)
                    val foldedParts = droppedNodes.flatMap { it.messages }
                        .filter { it.role == MessageRole.ASSISTANT && !it.isSynthetic }
                        .flatMap { it.parts }
                    val oldVersions = if (foldedParts.isEmpty()) emptyList() else listOf(
                        UIMessage(role = MessageRole.ASSISTANT, parts = foldedParts)
                    )
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    val ok = handleMessageComplete(conversationId)
                    if (ok && oldVersions.isNotEmpty()) {
                        // 旧回复并入新回复节点的版本历史（可切换查看），不丢失重回前记录
                        runCatching {
                            val conv2 = session.state.value
                            val idx = conv2.messageNodes.indexOfLast {
                                it.messages.any { m -> m.role == MessageRole.ASSISTANT && !m.isSynthetic }
                            }
                            if (idx >= 0) {
                                val replyNode = conv2.messageNodes[idx]
                                val mergedMessages = oldVersions + replyNode.messages
                                Logging.log(TAG, "regen merge: versions=${mergedMessages.size}")
                                saveConversation(
                                    conversationId,
                                    conv2.copy(
                                        messageNodes = conv2.messageNodes.subList(0, idx) +
                                            listOf(replyNode.copy(
                                                messages = mergedMessages,
                                                selectIndex = mergedMessages.size - 1,
                                            )) +
                                            conv2.messageNodes.subList(idx + 1, conv2.messageNodes.size)
                                    )
                                )
                            }                        }
                    } else if (!ok && droppedNodes.isNotEmpty()) {
                        // 生成失败：恢复被截断的旧回复组（丢掉失败残片节点，防顺序错乱）
                        runCatching {
                            val conv2 = session.state.value
                            saveConversation(
                                conversationId,
                                conv2.copy(
                                    messageNodes = conv2.messageNodes.subList(0, indexAt + 1) + droppedNodes
                                )
                            )
                        }
                    }
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        var nodeIndex = conversation.messageNodes.indexOf(node)
                        // 旧格式分条回复是多条连续助手节点：回退到本轮回复的第一条，实现整条重回
                        // （合成备注节点不是真实对话轮，跳过不挡路）
                        while (nodeIndex > 0) {
                            val prev = conversation.messageNodes[nodeIndex - 1]
                            val prevIsAssistantTurn = prev.messages.isNotEmpty() && (
                                prev.messages.all { it.role == MessageRole.ASSISTANT } ||
                                prev.messages.all { it.isSynthetic }
                            )
                            if (prevIsAssistantTurn) nodeIndex-- else break
                        }
                        // 快照被重回替换的旧回复组：成功后并入新回复的版本历史，失败后恢复。
                        // 旧分条格式一条回复=多个节点，必须折叠成单个版本，
                        // 否则逐节点入版本会出现「一次重回多条记录+内容重复」
                        val snapshotNodes = conversation.messageNodes.drop(nodeIndex)
                        val foldedParts = snapshotNodes.flatMap { it.messages }
                            .filter { it.role == MessageRole.ASSISTANT && !it.isSynthetic }
                            .flatMap { it.parts }
                        val oldVersions = if (foldedParts.isEmpty()) emptyList() else listOf(
                            UIMessage(role = MessageRole.ASSISTANT, parts = foldedParts)
                        )
                        val ok = handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                        if (ok && oldVersions.isNotEmpty()) {
                            runCatching {
                                val conv2 = session.state.value
                                val idx = conv2.messageNodes.indexOfLast {
                                    it.messages.any { m -> m.role == MessageRole.ASSISTANT && !m.isSynthetic }
                                }
                                if (idx >= 0) {
                                    val replyNode = conv2.messageNodes[idx]
                                    val mergedMessages = oldVersions + replyNode.messages
                                    Logging.log(TAG, "regen merge: versions=${mergedMessages.size}")
                                    saveConversation(
                                        conversationId,
                                        conv2.copy(
                                            messageNodes = conv2.messageNodes.subList(0, idx) +
                                                listOf(replyNode.copy(
                                                    messages = mergedMessages,
                                                    selectIndex = mergedMessages.size - 1,
                                                )) +
                                                conv2.messageNodes.subList(idx + 1, conv2.messageNodes.size)
                                        )
                                    )
                                }                            }
                        } else if (!ok && snapshotNodes.isNotEmpty()) {
                            runCatching {
                                val conv2 = session.state.value
                                if (conv2.messageNodes.size >= nodeIndex) {
                                    saveConversation(
                                        conversationId,
                                        conv2.copy(
                                            messageNodes = conv2.messageNodes.subList(0, nodeIndex) + snapshotNodes
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val hasOtherPendingTools = session.state.value.messageNodes.any { node ->
            node.currentMessage.parts.any { part ->
                part is UIMessagePart.Tool && part.isPending && part.toolCallId != toolCallId
            }
        }

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = !hasOtherPendingTools,
        ) {
            try {
                afterPreviousGeneration(previousJob) {
                    val conversation = session.state.value
                    // Ignore double taps and stale approvals for completed or inactive tools.
                    if (conversation.currentMessages.none { message ->
                            message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                        }) return@afterPreviousGeneration
                    val newApprovalState = when {
                        answer != null -> ToolApprovalState.Answered(answer)
                        approved -> ToolApprovalState.Approved
                        else -> ToolApprovalState.Denied(reason)
                    }

                    // Update the tool approval state
                    val updatedNodes = conversation.messageNodes.map { node ->
                        node.copy(
                            messages = node.messages.map { msg ->
                                msg.copy(
                                    parts = msg.parts.map { part ->
                                        when {
                                            part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                                part.copy(approvalState = newApprovalState)
                                            }

                                            else -> part
                                        }
                                    }
                                )
                            }
                        )
                    }
                    val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                    saveConversation(conversationId, updatedConversation)

                    // Check if there are still pending tools
                    val hasPendingTools = updatedNodes.any { node ->
                        node.currentMessage.parts.any { part ->
                            part is UIMessagePart.Tool && part.isPending
                        }
                    }

                    // Only continue generation when all pending tools are handled
                    if (!hasPendingTools) {
                        handleMessageComplete(conversationId)
                    }

                    _generationDoneFlow.emit(conversationId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job, cancelPrevious = false)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null,
        hiddenInput: UIMessage? = null,
    ): Boolean {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        // 对话级模型覆盖优先（对话设置固定），否则跟随助手
        val model = settings.findModelById(
            initialConversation.chatModelIdOverride ?: assistant.chatModelId ?: settings.chatModelId
        )
            ?: throw IllegalStateException("No chat model selected")

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }
        // 搜索开关：对话级覆盖优先
        val effectiveAssistant = assistant.copy(
            enableWebSearch = initialConversation.webSearchOverride ?: assistant.enableWebSearch
        )
        val useExternalWebSearch = shouldUseExternalWebSearch(effectiveAssistant, model)

        var ok = true
        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (useExternalWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            val tools = try {
                chatToolFactory.createTools(
                    settings = settings,
                    assistant = effectiveAssistant,
                    model = model,
                    workspaceCwd = conversation.workspaceCwd,
                    conversationId = conversationId,
                )
            } catch (error: InvalidMcpServerNamesException) {
                sessionManager.get(conversationId)?.messageQueue?.pause()
                addError(
                    error = IllegalStateException(
                        context.getString(
                            R.string.error_mcp_invalid_server_name,
                            error.names.joinToString(", "),
                        )
                    ),
                    conversationId = conversationId,
                )
                return false
            }

            // start generating
            val session = sessionManager.getOrCreate(conversationId)
            generationLoop.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let {
                    val base = if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                    // 自主唤醒：注入隐藏的合成触发消息(不落库不显示)
                    if (hiddenInput != null) base + listOf(hiddenInput) else base
                },
                assistant = effectiveAssistant,
                conversationId = conversationId,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = tools,
            ).onCompletion {
                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = session.finishGeneration { conversation ->
                    saveConversation(conversationId, conversation)
                }

                // 生成结束：取消 Live Update 通知，后台时发送完成通知
                appEventBus.emit(
                    AppEvent.ChatGenerationEnded(
                        conversationId = conversationId,
                        senderName = senderName,
                        contentPreview = updatedConversation.currentMessages.lastOrNull()
                            ?.toText()?.take(50)?.trim() ?: "",
                    )
                )
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        // 净化：剥掉模型偶发生成的 Text(text=..., metadata=null) 序列化包装（所有助手消息）
                        // 快速短路：不含包装痕迹的文本直接跳过，避免流式期间反复跑正则造成卡顿
                        val cleanMessages = chunk.messages.map { m ->
                            if (m.role == MessageRole.ASSISTANT) {
                                m.copy(parts = m.parts.map { p ->
                                    if (p is UIMessagePart.Text &&
                                        (p.text.contains("metadata=null") || p.text.contains("Text(text="))
                                    ) {
                                        p.copy(text = stripToStringWrapper(p.text))
                                    } else {
                                        p
                                    }
                                })
                            } else {
                                m
                            }
                        }
                        // 「消息分条」改为显示层拆分：数据只存一条（<split/> 标记保留在文本中，
                        // 渲染端 ChatMessage 按标记拆成多条气泡），删除/重回天然作用于整条。
                        // 过滤隐藏的合成消息(自主唤醒触发)；唤醒标记保留在文本中，渲染层显示为确认条
                        val displayMessages = cleanMessages.asSequence()
                            .filter { !it.isSynthetic }
                            .toList()
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(displayMessages)
                        updateConversation(conversationId, updatedConversation)

                        // 通知等边缘副作用由 ChatNotificationManager 消费；
                        // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                        chunk.messages.lastOrNull()?.let { lastMessage ->
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                            )
                        }
                    }
                }
            }
        }.onFailure {
            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            if (it is CancellationException) throw it
            ok = false
            sessionManager.get(conversationId)?.messageQueue?.pause()

            it.printStackTrace()
            // 唤醒生成的失败不在对话里放错误卡片（信息从胶囊面板的重试中/已放弃状态看）
            if (hiddenInput == null) {
                addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            }
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            // 唤醒轮空回复视为失败：醒来一言不发（无文字也无任何内容部件）没有意义，
            // 转入重试链（回滚+重试），而不是白白标记已触发/续期
            if (hiddenInput != null) {
                val last = getConversationFlow(conversationId).value.currentMessages.lastOrNull()
                val meaningful = last != null && last.role == MessageRole.ASSISTANT && last.parts.any { pp ->
                    (pp is UIMessagePart.Text && pp.text.isNotBlank()) || pp !is UIMessagePart.Text
                }
                if (!meaningful) {
                    Logging.log(TAG, "handleMessageComplete: wake round produced empty reply, treat as failure")
                    ok = false
                }
            }
            val finalConversation = getConversationFlow(conversationId).value

            sessionManager.launchWithSession(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            sessionManager.launchWithSession(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
            // 自主唤醒：解析回复末尾的 <wake after=".."/> 标记并安排定时任务
            // 唤醒触发的生成（hiddenInput != null）里忽略新调度标记，防自激励循环
            sessionManager.launchWithSession(conversationId) {
                handleWakeSchedule(conversationId, fromWake = hiddenInput != null)
            }
        }
        return ok
    }

    // ---- 助手自主唤醒 ----

    private fun wakeConvTag(conversationId: Uuid) = "wake_conv_$conversationId"

    /** 任务唯一键：会话+事项（同一事项重新安排=替换，不同事项并行共存） */
    private fun wakeTaskKey(conversationId: Uuid, note: String, spec: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest("$conversationId|$note|$spec".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** 事项标签：用于按事项取消任务 */
    private fun wakeNoteTag(conversationId: Uuid, note: String): String =
        "wake_note_" + wakeTaskKey(conversationId, note, "")

    private enum class WakeOutcome { SCHEDULED, CANCELLED, NONE }

    /**
     * 生成完成后解析唤醒标记。支持一次回复中安排多个任务（每个标记一个独立任务）。
     * 无标记时不做任何操作（已安排的任务保留，不会被中间对话取消）。
     */
    private suspend fun handleWakeSchedule(conversationId: Uuid, fromWake: Boolean = false): WakeOutcome {
        val settings = settingsStore.settingsFlow.first()
        val conversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(conversation.assistantId)
            ?: settings.getCurrentAssistant()
        val workManager = WorkManager.getInstance(context)

        // 唤醒开关关闭 = 明确取消该会话全部任务
        if (!assistant.autonomousWake) {
            workManager.cancelAllWorkByTag(wakeConvTag(conversationId))
            wakeRegistry.markAllCancelled(conversationId.toString())
            // 反馈回路：模型输出了标记但开关关闭 → 回写隐藏备注，防止模型谎报已设置
            if (WAKE_TAG.containsMatchIn(trailingAssistantWakeText(conversationId))) {
                postWakeRejectionNote(conversationId, "自主唤醒开关已关闭，标记不会被处理")
            }
            return WakeOutcome.CANCELLED
        }

        val lastMessage = conversation.currentMessages.lastOrNull()
        if (lastMessage == null || lastMessage.role != MessageRole.ASSISTANT) {
            return WakeOutcome.NONE
        }
        val trailingAssistantText = trailingAssistantWakeText(conversationId)
        val matches = WAKE_TAG.findAll(trailingAssistantText).toList()
        if (matches.isEmpty()) {
            // 无标记：保留已安排的任务（一次性与循环都不被中间对话取消）
            Logging.log(TAG, "handleWakeSchedule: no wake marker, keep existing schedules")
            // 防谎言：模型声称已设置提醒/唤醒，却没输出任何 <wake> 标记 → 系统实际什么都没安排，可见提示
            if (assistant.autonomousWake &&
                trailingAssistantText.contains(Regex("已设置|已经设置|已安排|已经安排|已定好|已为您设置")) &&
                trailingAssistantText.contains(Regex("提醒|唤醒"))
            ) {
                postWakeRejectionNote(
                    conversationId,
                    "你声称已设置提醒/唤醒，但回复里没有输出任何 <wake .../> 标记，系统没有安排任何任务。" +
                        "请输出标准标记（如 <wake after=\"30m\" note=\"事项\"/>），不要只口头说明"
                )
            }
            return WakeOutcome.NONE
        }

        var anyScheduled = false
        var anyCancelled = false
        for (match in matches) {
            val schedule = parseWakeSchedule(match.value)
            if (schedule == null) {
                Logging.log(TAG, "handleWakeSchedule: wake marker unparseable: ${match.value}")
                postWakeRejectionNote(conversationId, "标记格式无法解析：${match.value.take(80)}")
                continue
            }
            // 唤醒回复的调度防护（接力放行 + 死循环熔断）：
            // 允许模型完成任务后安排下一步（接力链），但设三道防线
            if (fromWake && schedule !is WakeSchedule.Cancel) {
                // 防线1：一次性任务最小 5 分钟，防紧密空转
                if (schedule is WakeSchedule.OneShot && schedule.minutes < 5) {
                    Logging.log(TAG, "handleWakeSchedule: reject wake-reply one-shot < 5min: ${match.value}")
                    postWakeRejectionNote(conversationId, "接力任务被拒：唤醒回复链里一次性任务最短 5 分钟（当前 ${schedule.minutes} 分钟）")
                    continue
                }
                // 防线2：1 小时内自触发 >= 5 次则熔断（暂停唤醒回复的新调度）
                val recentFired = wakeRegistry.list(conversationId.toString()).count {
                    it.status == "FIRED" && System.currentTimeMillis() - it.updatedAt < 3600000L
                }
                if (recentFired >= 5) {
                    Logging.log(TAG, "handleWakeSchedule: chain breaker: $recentFired wakes in 1h, reject: ${match.value}")
                    postWakeRejectionNote(conversationId, "接力熔断：1 小时内自触发已满 5 次，本轮标记被拒")
                    continue
                }
                Logging.log(TAG, "handleWakeSchedule: wake-reply chain handoff accepted: ${match.value}")
            }
            when (schedule) {
                is WakeSchedule.OneShot -> {
                    val taskKey = wakeTaskKey(conversationId, schedule.note, "after${schedule.minutes}")
                    workManager.enqueueUniqueWork(
                        "autonomous_wake_${conversationId}_$taskKey",
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<AutonomousWakeWorker>()
                            .setInitialDelay(schedule.minutes, TimeUnit.MINUTES)
                            .addTag(wakeConvTag(conversationId))
                            .addTag(wakeNoteTag(conversationId, schedule.note))
                            .setInputData(workDataOf(
                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                AutonomousWakeWorker.KEY_NOTE_TAG to wakeNoteTag(conversationId, schedule.note),
                                AutonomousWakeWorker.KEY_CREATED_AT to System.currentTimeMillis(),
                            ))
                            .build()
                    )
                    wakeRegistry.record(
                        WakeRecord(
                            taskKey = taskKey,
                            conversationId = conversationId.toString(),
                            assistantId = assistant.id.toString(),
                            note = schedule.note,
                            type = "once",
                            fireAt = System.currentTimeMillis() + schedule.minutes * 60_000L,
                            status = "PENDING",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    Logging.log(TAG, "handleWakeSchedule: one-shot in ${schedule.minutes} minutes (note=${schedule.note})")
                    anyScheduled = true
                }
                is WakeSchedule.Cycle -> {
                    val delay = nextCycleDelayMillis(schedule)
                    val taskKey = wakeTaskKey(
                        conversationId, schedule.note,
                        "${schedule.type}${schedule.day}${schedule.hour}${schedule.minute}"
                    )
                    workManager.enqueueUniqueWork(
                        "autonomous_wake_${conversationId}_$taskKey",
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<AutonomousWakeWorker>()
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .addTag(wakeConvTag(conversationId))
                            .addTag(wakeNoteTag(conversationId, schedule.note))
                            .setInputData(workDataOf(
                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                AutonomousWakeWorker.KEY_NOTE_TAG to wakeNoteTag(conversationId, schedule.note),
                                AutonomousWakeWorker.KEY_CREATED_AT to System.currentTimeMillis(),
                                AutonomousWakeWorker.KEY_CYCLE_SPEC to cycleSpecString(schedule),
                            ))
                            .build()
                    )
                    wakeRegistry.record(
                        WakeRecord(
                            taskKey = taskKey,
                            conversationId = conversationId.toString(),
                            assistantId = assistant.id.toString(),
                            note = schedule.note,
                            type = schedule.type,
                            fireAt = System.currentTimeMillis() + delay,
                            status = "PENDING",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    Logging.log(TAG, "handleWakeSchedule: cycle ${schedule.type} ${schedule.hour}:${schedule.minute} day=${schedule.day} scheduled (note=${schedule.note})")
                    anyScheduled = true
                }
                is WakeSchedule.Cancel -> {
                    if (schedule.note.isBlank()) {
                        // 取消该会话全部任务
                        workManager.cancelAllWorkByTag(wakeConvTag(conversationId))
                        wakeRegistry.markAllCancelled(conversationId.toString())
                        Logging.log(TAG, "handleWakeSchedule: all wakes cancelled by model")
                    } else {
                        // 按事项取消（精确哈希匹配；提示词要求模型取消时逐字复制安排时的 note）
                        workManager.cancelAllWorkByTag(wakeNoteTag(conversationId, schedule.note))
                        wakeRegistry.markCancelled(conversationId.toString(), schedule.note)
                        Logging.log(TAG, "handleWakeSchedule: wake cancelled by note=${schedule.note}")
                    }
                    anyCancelled = true
                }
            }
        }
        // 反馈回路闭环：标记被接受后，清理历史中的拒绝备注——
        // 它们只服务于上一轮失败的重试提示，残留在历史里会让模型误以为任务仍处于失败状态
        if ((anyScheduled || anyCancelled) && matches.isNotEmpty()) {
            val convNow = getConversationFlow(conversationId).value
            val cleanedNodes = convNow.messageNodes.filterNot { node ->
                node.messages.size == 1 &&
                    node.messages[0].isSynthetic &&
                    node.messages[0].toText().startsWith("系统备注：唤醒标记未被接受")
            }
            if (cleanedNodes.size != convNow.messageNodes.size) {
                saveConversation(conversationId, convNow.copy(messageNodes = cleanedNodes))
                Logging.log(TAG, "handleWakeSchedule: cleared ${convNow.messageNodes.size - cleanedNodes.size} stale rejection note(s)")
            }
        }
        // 反馈回路：有标记但全部未被接受（解析失败/被拒）→ 回写隐藏备注
        if (matches.isNotEmpty() && !anyScheduled && !anyCancelled) {
            postWakeRejectionNote(conversationId, "标记格式无法被系统解析")
        }
        return when {
            anyCancelled -> WakeOutcome.CANCELLED
            anyScheduled -> WakeOutcome.SCHEDULED
            else -> WakeOutcome.NONE
        }
    }

    /** 末尾连续助手气泡文本（分条场景唤醒标记可能在任意一条里） */
    private fun trailingAssistantWakeText(conversationId: Uuid): String {
        val conversation = getConversationFlow(conversationId).value
        return buildString {
            for (msg in conversation.currentMessages.reversed()) {
                if (msg.role != MessageRole.ASSISTANT) break
                if (msg.parts.any { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }) break
                insert(0, msg.toText())
                insert(0, "\n")
            }
        }
    }

    /** 反馈回路：以系统身份写一条隐藏备注（isSynthetic，界面不显示），模型下轮可见后自行重试 */
    private suspend fun postWakeRejectionNote(conversationId: Uuid, reason: String) {
        val conversation = getConversationFlow(conversationId).value
        // 避免重复备注：最后一条已是同类备注则跳过
        val lastText = conversation.currentMessages.lastOrNull()?.toText() ?: ""
        if (lastText.startsWith("系统备注：唤醒标记未被接受")) return
        val note = UIMessage.system(
            "系统备注：唤醒标记未被接受（$reason）。本次唤醒没有生效——" +
                "注意：5 分钟下限仅针对唤醒回复链里的接力任务，用户直接对话中设置不受此限制，1 分钟也可以；" +
                "请不要告诉用户已经设置成功；如用户确实需要提醒，请立即重新输出正确格式的 <wake .../> 标记重试，" +
                "重试成功前不要告诉用户已经设置成功。"
        ).copy(isSynthetic = true)
        val updated = conversation.copy(
            messageNodes = conversation.messageNodes + note.toMessageNode()
        )
        saveConversation(conversationId, updated)
        Logging.log(TAG, "postWakeRejectionNote: $reason")
    }

    private fun cycleSpecString(cycle: WakeSchedule.Cycle): String =
        "${cycle.type}|${cycle.day}|${cycle.hour}|${cycle.minute}|${cycle.note}"

    private fun parseCycleSpec(spec: String): WakeSchedule.Cycle? {
        val parts = spec.split("|")
        if (parts.size < 4) return null
        return WakeSchedule.Cycle(
            type = parts[0],
            day = parts[1].toIntOrNull() ?: 0,
            hour = parts[2].toIntOrNull() ?: 0,
            minute = parts[3].toIntOrNull() ?: 0,
            note = parts.getOrElse(4) { "" },
        )
    }

    /**
     * 唤醒触发入口：注入隐藏触发消息生成一轮回复。
     * - 生成失败：重试策略=每个 1 分钟窗口最多 3 次，窗口耗尽推移到下一窗口；1 小时内无响应静默取消
     * - 循环任务：生成完成后若模型未输出新标记，自动续期下一次
     */
    fun triggerAutonomousWake(
        conversationId: Uuid,
        attempt: Int = 0,
        taskKey: String = "",
        noteTag: String = "",
        createdAt: Long = 0L,
        cycleSpec: String? = null,
    ) {
        appScope.launch {
            runCatching {
                val exists = conversationRepo.getConversationById(conversationId)
                if (exists == null) {
                    // 会话已被删除，取消残留任务
                    WorkManager.getInstance(context)
                        .cancelAllWorkByTag(wakeConvTag(conversationId))
                    wakeRegistry.markAllCancelled(conversationId.toString())
                    return@launch
                }
                // 官方串行模式：唤醒生成注册为会话生成任务（join 接力在位生成，不并发覆盖）
                // 修复：之前裸调 handleMessageComplete 未注册生成任务，唤醒生成期间 isGenerating=false，
                // 用户消息并发生成与唤醒生成互相用旧快照覆盖（唤醒后消息全丢、界面回滚的根因）
                val session = sessionManager.getOrCreate(conversationId)
                val previousJob = session.getJob()
                var preAttemptCount = -1
                var generationFailed = false
                val job = launchGenerationJob(
                    conversationId = conversationId,
                    keepAliveInBackground = true,
                ) {
                    try {
                        previousJob?.join()
                        initializeConversation(conversationId)
                        // 方案A：记录本轮尝试前的节点边界，失败时精确回滚本轮残缺回复
                        preAttemptCount = getConversationFlow(conversationId).value.messageNodes.size
                        Log.i(TAG, "triggerAutonomousWake: firing for $conversationId (attempt $attempt)")
                        val ok = handleMessageComplete(conversationId, hiddenInput = hidden)
                        if (!ok) {
                            // 生成失败：抛出让重试链接管（20秒x15次一波，波间15分钟，共4波60次）
                            // 此前生成失败被吞掉，markFired 无条件执行（胶囊误显示"已触发"）且重试从不生效
                            generationFailed = true
                            throw IllegalStateException("唤醒生成失败（API流被截断或请求出错）")
                        }
                        wakeRegistry.markFired(taskKey)
                        // 循环任务续期：模型输出了新标记则用新的；没输出则按原周期续期（不会中断）
                        if (cycleSpec != null) {
                            val cycle = parseCycleSpec(cycleSpec)
                            if (cycle != null) {
                                val outcome = handleWakeSchedule(conversationId, fromWake = true)
                                // 防线3：回复里模型只加了一次性接力（没改这个周期本身）→ 原周期照常续期；
                                // 只有显式取消、或同事项重新定了周期，才不自动续期
                                val tookOverCycle = run {
                                    val trailing = buildString {
                                        val msgs = getConversationFlow(conversationId).value.currentMessages
                                        for (msg in msgs.reversed()) {
                                            if (msg.role != MessageRole.ASSISTANT) break
                                            if (msg.parts.any { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }) break
                                            insert(0, msg.toText())
                                            insert(0, "\n")
                                        }
                                    }
                                    WAKE_TAG.findAll(trailing).any {
                                        it.value.contains("every=") && it.value.contains(cycle.note)
                                    }
                                }
                                if (outcome == WakeOutcome.NONE ||
                                    (outcome == WakeOutcome.SCHEDULED && !tookOverCycle)
                                ) {
                                    val delay = nextCycleDelayMillis(cycle)
                                    WorkManager.getInstance(context).enqueueUniqueWork(
                                        "autonomous_wake_${conversationId}_${taskKey}",
                                        ExistingWorkPolicy.REPLACE,
                                        OneTimeWorkRequestBuilder<AutonomousWakeWorker>()
                                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                            .addTag(wakeConvTag(conversationId))
                                            .addTag(noteTag)
                                            .setInputData(workDataOf(
                                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                                AutonomousWakeWorker.KEY_NOTE_TAG to noteTag,
                                                AutonomousWakeWorker.KEY_CREATED_AT to System.currentTimeMillis(),
                                                AutonomousWakeWorker.KEY_CYCLE_SPEC to cycleSpecString(cycle),
                                            ))
                                            .build()
                                    )
                                    // 循环续期后重新置为待触发（fireAt=下次触发时间），避免整天显示"已触发"
                                    wakeRegistry.markPending(taskKey, System.currentTimeMillis() + delay)
                                    Logging.log(TAG, "triggerAutonomousWake: cycle renewed (no new marker)")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 取消异常也按失败处理：系统在 API 挂起超时后强停任务时，
                        // 若放行取消会导致「过了触发点仍待触发、不重试」——必须进入重试链
                        // （重试排程的 REPLACE 会覆盖同名任务，不会重复排队）
                        e.printStackTrace()
                        Logging.log(TAG, "triggerAutonomousWake: attempt $attempt failed: $e")
                        // 方案A失败回滚：精确截回本轮尝试前的边界，聊天里不留半截残缺回复
                        // （仅限生成阶段失败；排程/续期阶段的异常不动聊天内容）
                        if (generationFailed && preAttemptCount >= 0) {
                            runCatching {
                                val conv = getConversationFlow(conversationId).value
                                if (conv.messageNodes.size > preAttemptCount) {
                                    saveConversation(conversationId, conv.copy(
                                        messageNodes = conv.messageNodes.subList(0, preAttemptCount)
                                    ))
                                }
                            }
                        }
                        // 重试策略（定制）：每20秒重试一次（1分钟3次），连续5分钟（15次）为一波；
                        // 一波全失败→15分钟后再来一波（同样15次），共4波=60次重试，耗尽才标记放弃
                        if (attempt >= 60) {
                            Logging.log(TAG, "triggerAutonomousWake: 60 retries exhausted")
                            if (cycleSpec != null) {
                                // 循环任务：放弃本轮但续期到下一周期（明天照常再试，任务不死）
                                val cycle = parseCycleSpec(cycleSpec)
                                if (cycle != null) {
                                    val delay = nextCycleDelayMillis(cycle)
                                    WorkManager.getInstance(context).enqueueUniqueWork(
                                        "autonomous_wake_${conversationId}_${taskKey}",
                                        ExistingWorkPolicy.REPLACE,
                                        OneTimeWorkRequestBuilder<AutonomousWakeWorker>()
                                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                            .addTag(wakeConvTag(conversationId))
                                            .addTag(noteTag)
                                            .setInputData(workDataOf(
                                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                                AutonomousWakeWorker.KEY_NOTE_TAG to noteTag,
                                                AutonomousWakeWorker.KEY_CREATED_AT to System.currentTimeMillis(),
                                                AutonomousWakeWorker.KEY_CYCLE_SPEC to cycleSpecString(cycle),
                                            ))
                                            .build()
                                    )
                                    wakeRegistry.markPending(taskKey, System.currentTimeMillis() + delay)
                                    Logging.log(TAG, "triggerAutonomousWake: cycle survived retries, renewed for next cycle")
                                    return@launchGenerationJob
                                }
                            }
                            // 一次性任务：重试耗尽，标记放弃
                            if (taskKey.isNotBlank()) wakeRegistry.markFailed(taskKey)
                            return@launchGenerationJob
                        }
                        // 进入重试：登记表标记 RETRYING（胶囊面板可见）
                        if (taskKey.isNotBlank()) wakeRegistry.markRetrying(taskKey)
                        val next = attempt + 1
                        val delaySeconds = when {
                            next <= 15 -> 20L
                            next % 15 == 1 -> 15L * 60L
                            else -> 20L
                        }
                        val inputData = if (cycleSpec != null) {
                            workDataOf(
                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                AutonomousWakeWorker.KEY_NOTE_TAG to noteTag,
                                AutonomousWakeWorker.KEY_CREATED_AT to createdAt,
                                AutonomousWakeWorker.KEY_ATTEMPT to (attempt + 1),
                                AutonomousWakeWorker.KEY_FIRE_AT to System.currentTimeMillis() + delaySeconds * 1000L,
                                AutonomousWakeWorker.KEY_CYCLE_SPEC to cycleSpec,
                            )
                        } else {
                            workDataOf(
                                AutonomousWakeWorker.KEY_CONVERSATION_ID to conversationId.toString(),
                                AutonomousWakeWorker.KEY_TASK_KEY to taskKey,
                                AutonomousWakeWorker.KEY_NOTE_TAG to noteTag,
                                AutonomousWakeWorker.KEY_CREATED_AT to createdAt,
                                AutonomousWakeWorker.KEY_ATTEMPT to (attempt + 1),
                                AutonomousWakeWorker.KEY_FIRE_AT to System.currentTimeMillis() + delaySeconds * 1000L,
                            )
                        }
                        // 重试任务分流：波内间隔(20秒)用加速即时启动+内部等待；
                        // 波间间隔(15分钟)用常规延迟任务（加速任务有时长上限，不宜长等）
                        val builder = OneTimeWorkRequestBuilder<AutonomousWakeWorker>()
                        if (delaySeconds <= 60L) {
                            builder.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        } else {
                            builder.setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                        }
                        builder
                            .addTag(wakeConvTag(conversationId))
                            .setInputData(inputData)
                        if (noteTag.isNotBlank()) builder.addTag(noteTag)
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            if (taskKey.isBlank()) "autonomous_wake_${conversationId}_retry_$attempt"
                            else "autonomous_wake_${conversationId}_$taskKey",
                            ExistingWorkPolicy.REPLACE,
                            builder.build()
                        )
                    }
                }
                session.setJob(job, cancelPrevious = false)
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                it.printStackTrace()
                Logging.log(TAG, "triggerAutonomousWake: setup failed: $it")
            }
        }
    }

    private val wakeRegistry: WakeRegistry by lazy {
        WakeRegistry(File(context.filesDir, "wake_records.json"))
    }

    private val hidden: UIMessage by lazy {
        // 唤醒触发改为系统角色注入：提示应由系统发给模型，而不是以用户身份发出
        UIMessage.system(prompt = WAKE_TRIGGER_PROMPT).copy(isSynthetic = true)
    }


    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return@withContext

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.fastModelId)
                ?: throw IllegalStateException(context.getString(R.string.error_fast_model_not_found))
            val provider = model.findProvider(settings.providers)
                ?: throw IllegalStateException(context.getString(R.string.error_fast_model_provider_not_found))

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.message.toText().trim())
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckFastModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(
        conversationId: Uuid,
        conversation: Conversation,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return@runCatching
            val model = settings.findModelById(settings.fastModelId)
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching

            sessionManager.get(conversationId)?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                    )
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )
            val suggestions =
                result.message.toText().split("\n").map { it.trim() }
                    .filter { it.isNotBlank() }

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessionManager.get(conversationId)?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model, conversationId),
            )

            return result.message.toText().trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = sessionManager.getOrCreate(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.updateConversation(conversation)
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize {
                conversationRepo.getConversationById(conversationId)
                    ?: throw NotFoundException("Conversation not found")
            }
            session.updateMetadata(update, persist)
        }
    }

    /**
     * 更新对话级设置覆盖（模型/搜索等），持久化到仓库并同步会话内存状态。
     * 不能用 updateConversationState（只改内存不落库，重启即丢）。
     */
    suspend fun updateConversationOverride(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val conversation = getConversationFlow(conversationId).value
        val updated = update(conversation)
        Logging.log(TAG, "updateConversationOverride: before=${conversation.chatModelIdOverride} after=${updated.chatModelIdOverride}")
        saveConversation(conversationId, updated)
        val after = getConversationFlow(conversationId).value
        Logging.log(TAG, "updateConversationOverride: persisted=${after.chatModelIdOverride} (内存态)")
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            update = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            update = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) != null) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val session = sessionManager.get(newConversation.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val newFiles = newConversation.files + queuedFiles
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }

        // 删除消息或切换分支也可能解除工具审批阻塞，保存成功后重新检查队列。
        // 调度器仍会检查当前生成任务、待审批工具、暂停状态及编辑占位。
        dispatchNextQueuedMessage(conversationId)
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                translationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        // 官方行为：编辑只更新当前选中的消息文本（分支追加），不自动截断、不自动重回——
        // 是否重回由用户自己决定；模型请求只读当前选中分支，看不到修改前的文本
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val existingTitles = conversationRepo
            .getConversationsOfAssistant(currentConversation.assistantId)
            .first()
            .mapTo(mutableSetOf()) { it.title }
        val forkConversation = createForkConversation(currentConversation, copiedNodes, existingTitles)

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        finishInterruptedPendingTools(conversationId)
    }
}
