package me.rerere.rikkahub.ui.pages.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.VolcengineASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.stripMarkdown
import okhttp3.OkHttpClient
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 打电话式语音通话页(发现页/下拉/侧边栏拨打面板入口,路由 Screen.VoiceCall):
 * 进页即挑会话起呼,大头像+计时+实时字幕(你说的话/TA 最近回复),底部字幕开关+挂断。
 * assistantId=null = 当前助手;侧边栏拨打面板传指定助手。
 * 权限/ASR 配置/音频焦点守卫与聊天页语音模式同源。
 */
@Composable
fun VoiceCallPage(
    assistantId: String? = null,
    incoming: Boolean = false,
    vm: VoiceCallVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val client = koinInject<OkHttpClient>()
    val asr = LocalASRState.current
    // 通话页用独占 TTS 实例:全局实例会被 text_to_speech 工具/消息朗读抢占重置,导致卡「正在说」
    val tts = rememberCustomTtsState()
    val toaster = LocalToaster.current
    val eventBus = koinInject<me.rerere.rikkahub.data.event.AppEventBus>()
    val permission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permission)
    val settingsStore = koinInject<SettingsStore>()
    val setting by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val voice by vm.state.collectAsStateWithLifecycle()
    val latestReply by vm.latestReply.collectAsStateWithLifecycle()

    var prepareError by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var startAttempted by remember { mutableStateOf(false) }
    var captions by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(assistantId) {
        vm.setTargetAssistant(assistantId)
        vm.setIncomingCall(incoming)
        val err = vm.prepare()
        if (err != null) prepareError = err else prepared = true
        // TTS 未配置时明确提示(此前静默变纯文字,用户以为坏了);provider 异步装载,稍等再判防误报
        kotlinx.coroutines.delay(800)
        if (!tts.isAvailable.value) {
            toaster.show("未配置语音合成(TTS),TA 只会打字不出声;到 设置→语音服务→语音合成 配置")
        }
    }

    // TA 调用 hang_up_call 主动挂断:结束通话回聊天页(挂断事实由 VM.stopCall 统一告知;会话不匹配不关页面)
    LaunchedEffect(eventBus) {
        eventBus.events.collect { event ->
            if (event is me.rerere.rikkahub.data.event.AppEvent.VoiceCallHangupRequest) {
                if (vm.handleHangupRequest(event.conversationId)) {
                    navController.popBackStack()
                }
            }
        }
    }

    LaunchedEffect(voice.isActive) {
        seconds = 0
        while (voice.isActive) {
            delay(1000)
            seconds++
        }
    }

    val startCall: () -> Unit = {
        if (prepared) {
            val provider = setting.getSelectedASRProvider()
            val blocked = when {
                provider == null -> "未配置语音识别(ASR),先到设置里配好"
                !provider.supportsServerVadVoiceMode -> "当前 ASR 不支持通话式自动断句,换 OpenAI Realtime/DashScope/火山引擎"
                setting.getCurrentChatModel() == null -> "请先选择模型"
                asr.state.value.isRecording -> "请先结束正在进行的语音输入"
                else -> null
            }
            when {
                blocked != null -> toaster.show(blocked)
                !permission.allRequiredPermissionsGranted -> permission.requestPermissions()
                else -> vm.startCall(
                    createAsr = { createCallAsr(context, client, checkNotNull(provider)) },
                    // TTS 统一传入:供应商异步装载,未就绪时在 speak 内等待(最多 6s),
                    // 避免起呼瞬间 isAvailable=false 导致整通电话 speak=null、助手全程不出声
                    // continuation=false=本轮首句(flush 重开);true=后续句(追加队列,不重置合成器,预取缓存保留)
                    speak = { reply, continuation ->
                        try {
                            var text = reply
                            if (setting.displaySetting.ttsOnlyReadQuoted) {
                                text = text.extractQuotedContentAsText() ?: text
                            }
                            if (setting.displaySetting.ttsOnlyReadOutsideBrackets) {
                                text = text.removeBracketedContent() ?: text
                            }
                            text = text.stripMarkdown()
                            if (text.isNotBlank()) {
                                if (!tts.isAvailable.value) {
                                    // 等装载完成或等出错,超时由外层 runCatching 兜底跳过
                                    kotlinx.coroutines.withTimeoutOrNull(6_000) {
                                        combine(tts.isAvailable, tts.error) { avail, err -> avail || err != null }
                                            .first { it }
                                    }
                                }
                                if (tts.isAvailable.value) {
                                    tts.speak(text, flush = !continuation)
                                    // 合成/播放失败只跳过本句发声(字幕里仍有文字),不把整通电话打成 Error
                                    combine(tts.isSpeaking, tts.error) { speaking, error ->
                                        !speaking || error != null
                                    }.first { it }
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("VoiceCallPage", "TTS speak skipped: ${e.message}")
                        }
                    },
                    stopSpeaking = tts::stop,
                )
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
                onClick = { vm.stopCall(); navController.popBackStack() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft01,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = when (voice.phase) {
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Connecting -> "连接中…"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Listening -> "我在听"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Transcribing -> "识别中…"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Responding -> "思考中…"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Speaking -> "正在说"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Error -> "出错了"
                    me.rerere.rikkahub.ui.pages.chat.VoicePhase.Off -> "已挂断"
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
            CallBigAvatar(avatar = vm.assistantAvatar, name = vm.assistantName)
            Spacer(Modifier.height(20.dp))
            Text(
                text = vm.assistantName,
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
                    // 通话新增的回复才显示(VM 已过滤通话前旧消息);不再按 phase 闪现/隐藏,字幕稳定
                    CallCaption(speaker = vm.assistantName, text = latestReply, alignEnd = false)
                }
            }
        }
        if (voice.phase == me.rerere.rikkahub.ui.pages.chat.VoicePhase.Error && voice.error != null) {
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
                onClick = { vm.stopCall(); navController.popBackStack() },
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
            if (voice.phase == me.rerere.rikkahub.ui.pages.chat.VoicePhase.Error || voice.phase == me.rerere.rikkahub.ui.pages.chat.VoicePhase.Off) {
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

/** 与聊天页语音模式同源:建立 ASR 前请求独占音频焦点(通话用法) */
private fun createCallAsr(context: Context, client: OkHttpClient, provider: ASRProviderSetting): ASRController {
    val delegate = when (provider) {
        is ASRProviderSetting.OpenAIRealtime -> {
            check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
            OpenAIRealtimeASRController(context, client, provider)
        }
        is ASRProviderSetting.DashScope -> {
            check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
            DashScopeASRController(context, client, provider)
        }
        is ASRProviderSetting.Volcengine -> {
            check(provider.apiKey.isNotBlank()) { "先配置 ASR 的 API Key" }
            VolcengineASRController(context, client, provider)
        }
        else -> error("当前 ASR 不支持通话式自动断句")
    }
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()
    return object : ASRController by delegate {
        override fun start(onTranscriptChange: (String) -> Unit) {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "无法取得麦克风音频焦点,稍后再试"
            }
            delegate.start(onTranscriptChange)
        }

        override fun dispose() {
            try {
                delegate.dispose()
            } finally {
                audioManager.abandonAudioFocusRequest(focus)
            }
        }
    }
}
