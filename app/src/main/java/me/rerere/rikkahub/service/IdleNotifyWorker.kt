package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * 静默提醒轮询(唤醒系统新增规则):每 15 分钟检查一次,
 * 会话满 1 小时无新消息 → 后台提醒助手可主动关心;时长可累计,同一段静默期内每小时提醒一次。
 * 夜间 23:00-09:00 不生效;具体判定与触发在 ChatService.runIdleNotifyCheck。
 */
class IdleNotifyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            GlobalContext.get().get<ChatService>().runIdleNotifyCheck()
        }
        // 失败也不重试:下个 15 分钟周期自然会再查
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "idle_notify_check"

        /** App 启动时注册唯一周期任务(KEEP:已存在不替换) */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IdleNotifyWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
