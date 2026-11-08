package me.rerere.rikkahub.ui.pages.chat

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.stripMarkdown

/**
 * 打电话式语音通话全屏层:复用 ChatVM.voiceSession(实时 ASR → LLM → 流式 TTS)链路,
 * 进场即 start(),字幕区显示「你说的话 / TA 正在说的」,底部字幕开关+挂断(+错误重试)。
 * 权限/ASR 配置/音频焦点的守卫全部走 rememberVoiceModeStarter 的同一套逻辑。
 */
@Composable
fun VoiceCallOverlay(
    vm: ChatVM,
    settings: Settings,
    assistantName: String,
    onDismiss: () -> Unit,
) {
    val start = rememberVoiceModeStarter(vm, settings)
    LaunchedEffect(Unit) { start() }

    val voice by vm.voiceSession.state.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    var captions by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(voice.isActive) {
        seconds = 0
        while (voice.isActive) {
            delay(1000)
            seconds++
        }
    }

    // 字幕:正在识别/刚说的这句话 + 最近一条助手回复
    val lastUserText = voice.transcript
    val lastReply = remember(conversation) {
        conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.parts?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString(" ") { it.text }
            ?.stripMarkdown()?.take(140).orEmpty()
    }

    Dialog(
        onDismissRequest = { vm.voiceSession.stop(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))
                Text(
                    text = when (voice.phase) {
                        VoicePhase.Connecting -> "连接中…"
                        VoicePhase.Listening -> "我在听"
                        VoicePhase.Transcribing -> "识别中…"
                        VoicePhase.Speaking -> "正在说"
                        VoicePhase.Error -> "出错了"
                        VoicePhase.Off -> "已挂断"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = assistantName.firstOrNull()?.toString() ?: "聊",
                        fontSize = 44.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = assistantName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%02d:%02d".format(seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (captions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (lastUserText.isNotBlank()) {
                            CallCaption(
                                speaker = "你",
                                text = lastUserText,
                                alignEnd = true,
                            )
                        }
                        if (lastReply.isNotBlank() && voice.phase != VoicePhase.Listening) {
                            CallCaption(
                                speaker = assistantName,
                                text = lastReply,
                                alignEnd = false,
                            )
                        }
                    }
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
                        onClick = { vm.voiceSession.stop(); onDismiss() },
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
                    if (voice.phase == VoicePhase.Error) {
                        Surface(
                            onClick = { start() },
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
