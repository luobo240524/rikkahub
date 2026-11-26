package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit
import me.rerere.rikkahub.R
import me.rerere.rikkahub.WORLD_LETTER_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.utils.NotificationUtil
import org.koin.core.context.GlobalContext

class WorldLetterDeliveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_LETTER_ID, 0L)
        if (id <= 0L) return Result.failure()
        val repository = GlobalContext.get().get<WorldRepository>()
        val letters = repository.getDueLetters()
        val letter = letters.firstOrNull { it.id == id } ?: return Result.success()
        // 无通知权限:保持未通知状态,下次启动恢复排程时补发(不静默吞掉这封信)
        if (!NotificationUtil.hasNotificationPermission(applicationContext)) return Result.success()
        if (!repository.markLetterNotified(letter.id)) return Result.success()
        val intent = Intent(applicationContext, me.rerere.rikkahub.RouteActivity::class.java).apply {
            action = ACTION_WORLD_LETTER
            putExtra(EXTRA_ASSISTANT_ID, letter.assistantId)
            putExtra(EXTRA_LETTER_ID, letter.id)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            letter.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationUtil.notify(applicationContext, WORLD_LETTER_NOTIFICATION_CHANNEL_ID, letter.id.toInt()) {
            title = "一封信送达了"
            content = "邮局里有一封信，等你拆开。"
            smallIcon = R.drawable.small_icon
            autoCancel = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = pending
            useBigTextStyle = true
        }
        return Result.success()
    }

    companion object {
        const val ACTION_WORLD_LETTER = "me.rerere.rikkahub.action.WORLD_LETTER"
        const val EXTRA_ASSISTANT_ID = "assistantId"
        const val EXTRA_LETTER_ID = "letterId"
        private const val KEY_LETTER_ID = "letterId"

        fun workName(id: Long) = "world_letter_delivery_$id"

        fun schedule(context: Context, id: Long, scheduledAt: Long?) {
            if (scheduledAt == null) return
            val delay = (scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<WorldLetterDeliveryWorker>()
                .setInputData(Data.Builder().putLong(KEY_LETTER_ID, id).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(id), ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
