package me.rerere.rikkahub.ui.pages.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import org.koin.compose.koinInject

/**
 * 打电话式语音通话页(发现页/下拉/侧边栏拨打面板入口,路由 Screen.VoiceCall):
 * 进页即挑会话起呼,大头像+计时+实时字幕(你说的话/TA 最近回复),底部字幕开关+挂断。
 * assistantId=null = 当前助手;侧边栏拨打面板传指定助手。
 * 会话状态在全局 VoiceCallSessionManager:返回键 = 最小化挂后台(悬浮球/通知可回),
 * 只有挂断红钮 / TA 挂断才结束通话;通话中重进页面只回绑,不起新电话。
 */
@Composable
fun VoiceCallPage(
    assistantId: String? = null,
    incoming: Boolean = false,
    callManager: VoiceCallSessionManager = koinInject(),
) {
    val navController = LocalNavController.current
    val asr = LocalASRState.current
    val toaster = LocalToaster.current
    val eventBus = koinInject<me.rerere.rikkahub.data.event.AppEventBus>()
    val permission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permission)
    val voice by callManager.state.collectAsStateWithLifecycle()
    val latestReply by callManager.latestReply.collectAsStateWithLifecycle()
    val assistantName by callManager.assistantName.collectAsStateWithLifecycle()
    val assistantAvatar by callManager.assistantAvatar.collectAsStateWithLifecycle()

    var prepareError by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var startAttempted by remember { mutableStateOf(false) }
    var captions by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(assistantId) {
        if (voice.phase != VoicePhase.Off) {
            // 通话进行中:重进页面只回绑(悬浮球/通知回来),不重复 prepare/起呼
            if (assistantId != null && callManager.targetAssistantId != assistantId) {
                toaster.show("已有通话进行中")
            }
            prepared = true // Error 态回绑后仍可用「重试」
            return@LaunchedEffect
        }
        callManager.setTargetAssistant(assistantId)
        callManager.setIncomingCall(incoming)
        val err = callManager.prepare()
        if (err != null) prepareError = err else prepared = true
        // TTS 未配置时明确提示(此前静默变纯文字,用户以为坏了);provider 异步装载,稍等再判防误报
        kotlinx.coroutines.delay(800)
        if (!callManager.tts.isAvailable.value) {
            toaster.show("未配置语音合成(TTS),TA 只会打字不出声;到 设置→语音服务→语音合成 配置")
        }
    }

    // TA 调用 hang_up_call 主动挂断:manager 落挂断事实,页面跟着退场(会话不匹配不关页面)
    LaunchedEffect(eventBus) {
        eventBus.events.collect { event ->
            if (event is me.rerere.rikkahub.data.event.AppEvent.VoiceCallHangupRequest) {
                if (callManager.handleHangupRequest(event.conversationId)) {
                    navController.popBackStack()
                }
            }
        }
    }

    // 计时按通话开始时间换算:最小化后回绑不归零,挂断归零
    val callVisible = voice.phase != VoicePhase.Off
    LaunchedEffect(callVisible) {
        if (!callVisible) {
            seconds = 0
            return@LaunchedEffect
        }
        while (isActive) {
            val startedAt = callManager.callStartedAtMs
            seconds = startedAt
                ?.let { ((System.currentTimeMillis() - it) / 1000).toInt().coerceAtLeast(0) }
                ?: 0
            delay(1000)
        }
    }

    val startCall: () -> Unit = {
        if (prepared) {
            when {
                !permission.allRequiredPermissionsGranted -> permission.requestPermissions()
                asr.state.value.isRecording -> toaster.show("请先结束正在进行的语音输入")
                else -> {
                    // provider/模型检查在 manager 内,返回错误文案
                    callManager.startCall()?.let { toaster.show(it) }
                }
            }
        }
    }

    // 就绪即起呼(仅一次);失败可按重试
    LaunchedEffect(prepared) {
        if (prepared && !startAttempted) {
            startAttempted = true
            startCall()
        }
    }

    // 通话中系统返回手势 = 最小化(通话继续挂后台);非通话态(挂断/出错未接)直接退出
    BackHandler(enabled = callVisible) {
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { navController.popBackStack() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft01,
                        contentDescription = if (callVisible) "最小化通话" else "返回",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = when (voice.phase) {
                    VoicePhase.Connecting -> "连接中…"
                    VoicePhase.Listening -> "我在听"
                    VoicePhase.Transcribing -> "识别中…"
                    VoicePhase.Responding -> "思考中…"
                    VoicePhase.Speaking -> "正在说"
                    VoicePhase.Error -> "出错了"
                    VoicePhase.Off -> "已挂断"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        // 接通界面正中:大头像 + 助手名称 + 计时
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallBigAvatar(avatar = assistantAvatar, name = assistantName)
            Spacer(Modifier.height(20.dp))
            Text(
                text = assistantName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "%02d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (prepareError != null) {
            Text(
                text = prepareError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (captions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (voice.transcript.isNotBlank()) {
                    CallCaption(speaker = "你", text = voice.transcript, alignEnd = true)
                }
                if (latestReply.isNotBlank()) {
                    // 通话新增的回复才显示(manager 已过滤通话前旧消息);不再按 phase 闪现/隐藏,字幕稳定
                    CallCaption(speaker = assistantName, text = latestReply, alignEnd = false)
                }
            }
        }
        if (voice.phase == VoicePhase.Error && voice.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = voice.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Surface(
                onClick = { captions = !captions },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "字幕",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (captions) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Surface(
                onClick = { callManager.stopCall(); navController.popBackStack() },
                shape = CircleShape,
                color = Color(0xFFE05555),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "挂断",
                        tint = Color.White,
                    )
                }
            }
            if (voice.phase == VoicePhase.Error || voice.phase == VoicePhase.Off) {
                Surface(
                    onClick = { startCall() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "重试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

/** 通话页大头像:真实头像(图片/表情)优先,无则首字母圆底 */
@Composable
private fun CallBigAvatar(avatar: Avatar, name: String) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (avatar) {
            is Avatar.Image -> AsyncImage(
                model = avatar.url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            is Avatar.Emoji -> Text(text = avatar.content, fontSize = 72.sp)
            Avatar.Dummy -> Text(
                text = name.firstOrNull()?.toString() ?: "聊",
                fontSize = 56.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CallCaption(
    speaker: String,
    text: String,
    alignEnd: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (alignEnd) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = speaker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
