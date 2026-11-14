package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.ACTION_RESUME_VOICE_CALL
import me.rerere.rikkahub.VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.call.VoiceCallSessionManager
import org.koin.android.ext.android.inject

private const val TAG = "VoiceCallFgs"

/**
 * 语音通话前台服务(microphone 类型):通话挂后台(页面退出/退到桌面)时保活进程与麦克风权限。
 * - 通话开始 acquire(通知「通话中 · TA名」,点击回通话页),挂断 release
 * - 兜底:onDestroy 时若通话仍活跃 → stopCall 落挂断事实(滑动划掉应用等极端场景)
 */
class VoiceCallForegroundService : Service() {
    companion object {
        private const val ACTION_ACQUIRE = "me.rerere.rikkahub.action.VOICE_CALL_ACQUIRE"
        private const val ACTION_RELEASE = "me.rerere.rikkahub.action.VOICE_CALL_RELEASE"
        private const val EXTRA_ASSISTANT_NAME = "assistant_name"

        const val NOTIFICATION_ID = 2003

        fun acquire(context: Context, assistantName: String): Boolean {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_ACQUIRE
                putExtra(EXTRA_ASSISTANT_NAME, assistantName)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure {
                Log.e(TAG, "Unable to start voice call foreground service", it)
            }.getOrDefault(false)
        }

        fun release(context: Context) {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_RELEASE
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.e(TAG, "Unable to release voice call foreground service", it)
            }
        }
    }

    private val callManager: VoiceCallSessionManager by inject()
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACQUIRE -> acquire(intent.getStringExtra(EXTRA_ASSISTANT_NAME))
            ACTION_RELEASE -> stopService()
            else -> stopService()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 通话还挂着但服务被系统/用户终结(如最近任务划掉):落挂断事实,TA 不至于被晾着
        if (callManager.state.value.phase != me.rerere.rikkahub.ui.pages.chat.VoicePhase.Off) {
            runCatching { callManager.stopCall() }
        }
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    private fun acquire(assistantName: String?) {
        try {
            val notification = buildNotification(assistantName.orEmpty().ifBlank { "助手" })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground", e)
            stopSelf()
        }
    }

    private fun stopService() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    private fun buildNotification(assistantName: String) =
        NotificationCompat.Builder(this, VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle("通话中 · $assistantName")
            .setContentText("通话正在后台进行,点按继续")
            .setContentIntent(getResumePendingIntent())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun getResumePendingIntent(): PendingIntent {
        val intent = Intent(this, me.rerere.rikkahub.RouteActivity::class.java).apply {
            action = ACTION_RESUME_VOICE_CALL
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
