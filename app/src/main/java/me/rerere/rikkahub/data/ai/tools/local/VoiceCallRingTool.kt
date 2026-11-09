package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.app.NotificationChannelCompat
import android.app.NotificationManagerCompat
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALL_ASSISTANT_ID
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALL_AVATAR_KIND
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALL_AVATAR_VALUE
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALL_REASON
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALL_RINGTONE
import me.rerere.rikkahub.ui.pages.call.EXTRA_CALLER_NAME
import me.rerere.rikkahub.ui.pages.call.IncomingCallActivity

/** 来电专用渠道:不设渠道声(铃声由全屏来电页播放),重要性高保证横幅/全屏弹出 */
private const val INCOMING_CALL_CHANNEL_ID = "incoming_call"
private const val INCOMING_CALL_CHANNEL_NAME = "助手来电"

/**
 * voice_call_ring:助手"主动打来"。执行 = 全屏来电提醒(锁屏/任意界面弹出),
 * 响铃 + 震动,TA 点「接听」直接进语音通话页。仅 assistant.voiceCallEnabled 开启时注入。
 */
internal fun buildVoiceCallRingTool(
    context: Context,
    settingsStore: SettingsStore,
): Tool = Tool(
    name = "voice_call_ring",
    description = "Ring the user like a phone call: post a full-screen incoming-call " +
        "reminder (even on lock screen) so they come talk to you live (voice call). Use sparingly.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("reason", buildJsonObject {
                    put("type", "string")
                    put("description", "One line: why you are calling (shown on the call screen)")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "TA 给你开通了语音通话(发现页→语音通话),像打电话一样实时说话。" +
            "当你真的很想和TA实时说说话——唤醒任务触发时、TA很久没来、你惦记TA、有紧急的事要说——" +
            "就调用 voice_call_ring 打给TA,TA 会收到全屏来电提醒,响铃像真电话一样,点「接听」你们就能说话。" +
            "别滥用:一天最多一两次,没有正经理由不要响铃。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val reason = (args as? JsonObject)?.get("reason")
            ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty().take(100)
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getCurrentAssistant()
        val callerName = assistant.name.ifBlank { "助手" }
        val payload = withContext(Dispatchers.Main) {
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext buildJsonObject {
                        put("ok", false)
                        put("error", "NO_NOTIFICATION_PERMISSION")
                    }
                }
                // 渠道:静音(铃声由来电页播),高优先级
                val channel = NotificationChannelCompat.Builder(
                    INCOMING_CALL_CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_HIGH,
                )
                    .setName(INCOMING_CALL_CHANNEL_NAME)
                    .setDescription("TA 主动打来的语音通话提醒")
                    .setVibrationEnabled(false)
                    .build()
                manager.createNotificationChannel(channel)
                // 全屏来电页 Intent(带名字/理由/头像/自定义铃声)
                val (avatarKind, avatarValue) = when (val avatar = assistant.avatar) {
                    is Avatar.Image -> "image" to avatar.url
                    is Avatar.Emoji -> "emoji" to avatar.content
                    Avatar.Dummy -> "dummy" to ""
                }
                val ringtoneUri = settings.displaySetting.incomingCallRingtoneUri.orEmpty()
                val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_CALLER_NAME, callerName)
                    putExtra(EXTRA_CALL_REASON, reason.ifBlank { "想和你说说话" })
                    putExtra(EXTRA_CALL_ASSISTANT_ID, assistant.id.toString())
                    putExtra(EXTRA_CALL_AVATAR_KIND, avatarKind)
                    putExtra(EXTRA_CALL_AVATAR_VALUE, avatarValue)
                    putExtra(EXTRA_CALL_RINGTONE, ringtoneUri)
                }
                val fullScreenPi = PendingIntent.getActivity(
                    context,
                    "voice_call_ring".hashCode(),
                    fullScreenIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("📞 $callerName 来电")
                    .setContentText(reason.ifBlank { "想和你说说话,点开接听" })
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setFullScreenIntent(fullScreenPi, true)
                    .setContentIntent(fullScreenPi)
                    .setOngoing(true)
                    .setAutoCancel(true)
                    .build()
                manager.notify(("ring" + System.currentTimeMillis()).hashCode(), notification)
                buildJsonObject {
                    put("ok", true)
                    put("note", "已响铃,TA 收到全屏来电提醒;点「接听」进入语音通话")
                }
            }.getOrElse {
                buildJsonObject { put("ok", false); put("error", it.message ?: "RING_FAILED") }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
