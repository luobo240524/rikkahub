package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import org.koin.core.context.GlobalContext

/**
 * 「助手自主唤醒」定时任务：由 WorkManager 在约定的延迟后触发，
 * 调用 ChatService 对指定会话执行一轮自主生成。
 */
class AutonomousWakeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    // 加速任务在 Android 12 以下需要前台信息兜底（复用低优先级静默渠道）
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val notification = androidx.core.app.NotificationCompat.Builder(
            applicationContext, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
            .setContentTitle("助手唤醒任务执行中")
            .setSilent(true)
            .build()
        return androidx.work.ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        val raw = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()
        val conversationId = runCatching { Uuid.parse(raw) }.getOrNull() ?: return Result.failure()
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        val taskKey = inputData.getString(KEY_TASK_KEY) ?: ""
        val noteTag = inputData.getString(KEY_NOTE_TAG) ?: ""
        val createdAt = inputData.getLong(KEY_CREATED_AT, 0L)
        val cycleSpec = inputData.getString(KEY_CYCLE_SPEC)
        // 加速任务不可带初始延迟：到点前的等待在任务内部完成（前台通知兜底）
        val fireAt = inputData.getLong(KEY_FIRE_AT, 0L)
        if (fireAt > 0) {
            val remaining = fireAt - System.currentTimeMillis()
            if (remaining > 0) {
                runCatching { setForeground(getForegroundInfo()) }
                kotlinx.coroutines.delay(remaining)
            }
        }
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
        const val KEY_FIRE_AT = "fireAt"

        private const val NOTIFICATION_ID = 2001

        /** 每个会话一条唯一任务，新标记会 REPLACE 旧安排 */
        fun workName(conversationId: Uuid) = "autonomous_wake_$conversationId"
    }
}
