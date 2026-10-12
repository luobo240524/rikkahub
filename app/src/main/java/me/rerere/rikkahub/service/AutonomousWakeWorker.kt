package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid
import org.koin.core.context.GlobalContext

/**
 * 「助手自主唤醒」定时任务：由 WorkManager 在约定的延迟后触发，
 * 调用 ChatService 对指定会话执行一轮自主生成。
 */
class AutonomousWakeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val raw = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()
        val conversationId = runCatching { Uuid.parse(raw) }.getOrNull() ?: return Result.failure()
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        val taskKey = inputData.getString(KEY_TASK_KEY) ?: ""
        val noteTag = inputData.getString(KEY_NOTE_TAG) ?: ""
        val createdAt = inputData.getLong(KEY_CREATED_AT, 0L)
        val cycleSpec = inputData.getString(KEY_CYCLE_SPEC)
        val chatService = GlobalContext.get().get<ChatService>()
        chatService.triggerAutonomousWake(conversationId, attempt, taskKey, noteTag, createdAt, cycleSpec)
        return Result.success()
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_ATTEMPT = "attempt"
        const val KEY_TASK_KEY = "taskKey"
        const val KEY_NOTE_TAG = "noteTag"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_CYCLE_SPEC = "cycleSpec"

        /** 每个会话一条唯一任务，新标记会 REPLACE 旧安排 */
        fun workName(conversationId: Uuid) = "autonomous_wake_$conversationId"
    }
}
