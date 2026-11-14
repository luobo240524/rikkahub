package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import kotlin.uuid.Uuid

/**
 * hang_up_call:通话进行中 TA 主动挂断电话(用户已授权)。
 * 执行 = 发 VoiceCallHangupRequest 事件,VoiceCallSessionManager 收到后结束通话,页面跟着退场;
 * 挂断后的通话事实通知由 VoiceCallSessionManager.stopCall 统一落库(endedBy=assistant)。
 */
internal fun buildHangUpCallTool(
    eventBus: AppEventBus,
    conversationId: Uuid,
): Tool = Tool(
    name = "hang_up_call",
    description = "End the current voice call yourself. Use it when the conversation has naturally finished, " +
        "you need to go, or it is time for the user to rest. Say goodbye in text BEFORE calling this tool — " +
        "the tool call ends the call immediately.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { }
        )
    },
    systemPrompt = { _, _ ->
        "通话挂断规则:\n" +
            "- 你有挂断当前通话的权限:该说再见的时候(话题聊完/该睡了/你有别的事)可以调用 hang_up_call 主动挂断\n" +
            "- 先把想说的话用文字说完,最后再调用——调用后通话立即结束,无法再补充\n" +
            "- 也可以一直陪TA聊下去,由你根据气氛判断;不用每次通话都急着挂\n" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = {
        eventBus.emit(AppEvent.VoiceCallHangupRequest(conversationId))
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", true)
            put("note", "已挂断通话")
        }.toString()))
    }
)
