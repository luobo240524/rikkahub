package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
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
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.SettingsStore

/**
 * voice_call_ring:助手"主动打来"。执行 = 发一条来电风格的高优通知,
 * TA 点开通知进 app,从 发现→语音通话 接听。
 * 仅 assistant.voiceCallEnabled 开启时注入。
 */
internal fun buildVoiceCallRingTool(
    context: Context,
    settingsStore: SettingsStore,
): Tool = Tool(
    name = "voice_call_ring",
    description = "Ring the user like a phone call: post a high-priority incoming-call " +
        "notification so they come talk to you live (voice call). Use sparingly.",
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
            "就调用 voice_call_ring 打给TA,TA 会收到「你来电」的通知,点开接听。" +
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
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val pi = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() and 0xffffL).toInt(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val notification = NotificationCompat.Builder(context, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("📞 $callerName 来电")
                    .setContentText(reason.ifBlank { "想和你说说话,点开接听" })
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                manager.notify(("ring" + System.currentTimeMillis()).hashCode(), notification)
                buildJsonObject {
                    put("ok", true)
                    put("note", "已响铃,TA 会收到来电通知;接听入口在 发现→语音通话")
                }
            }.getOrElse {
                buildJsonObject { put("ok", false); put("error", it.message ?: "RING_FAILED") }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
