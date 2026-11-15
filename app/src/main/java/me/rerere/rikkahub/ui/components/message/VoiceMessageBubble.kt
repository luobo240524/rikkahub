package me.rerere.rikkahub.ui.components.message

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.ui.context.LocalToaster
import java.io.File

/**
 * 语音条转写文本(显示在语音条下方):默认展开,可一键收起/展开。
 * 转写由录音时的流式 ASR 边录边转生成,同时作为 Text part 供 LLM 理解。
 */
@Composable
fun VoiceTranscriptCollapsible(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * 语音条气泡(微信式):播放/暂停 + 波形 + 时长,应用内 ExoPlayer 播放(容器嗅探,兼容各 TTS 供应商音频)。
 * 波形高度由 (index, duration) 决定——同一条消息永远同一形状。
 * transcript 非空时(如助手 send_voice 的原文),点击语音条会在下方展开/收起文字内容。
 */
@Composable
fun VoiceMessageBubble(
    part: UIMessagePart.Audio,
    modifier: Modifier = Modifier,
    transcript: String? = null,
) {
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationSec by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var showTranscript by rememberSaveable { mutableStateOf(false) }
    val toaster = LocalToaster.current
    val context = LocalContext.current

    // 播放失败统一收尾:释放播放器复位状态 + 可见提示(此前 MediaPlayer 失败无监听=静默死气泡)
    fun playFailed(detail: String? = null) {
        runCatching { player?.release() }
        player = null
        playing = false
        progress = 0f
        toaster.show(if (detail.isNullOrBlank()) "语音条播放失败" else "语音条播放失败($detail)")
    }

    Column(modifier = modifier) {

    DisposableEffect(part.url) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    // 元数据时长兜底,prepared 后用真实时长
    LaunchedEffect(part.url) {
        val meta = part.metadata?.get("duration")
            ?.let { it as? JsonPrimitive }?.intOrNull ?: 0
        if (durationSec == 0) durationSec = meta
    }

    val bars = remember(durationSec) {
        List(22) { i -> ((i * 37 + durationSec * 13) % 15) + 6 } // 6..20dp
    }

    fun toggle() {
        val p = player
        if (p == null) {
            runCatching {
                // 统一走 ExoPlayer:MediaPlayer 对部分 WAV 头变体严格(DeepSeek 语音条不能播的根因;
                // 通话 TTS 同一份字节用 ExoPlayer 能放),且容器嗅探自动兜住 mp3/wav/m4a 混装
                val uri = if (part.url.startsWith("file://")) {
                    Uri.parse(part.url)
                } else {
                    Uri.fromFile(File(part.url))
                }
                val exo = ExoPlayer.Builder(context).build()
                exo.setMediaItem(MediaItem.fromUri(uri))
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> durationSec = (exo.duration / 1000L).toInt().coerceAtLeast(1)
                            Player.STATE_ENDED -> {
                                playing = false
                                progress = 0f
                            }
                            else -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playFailed(error.errorCodeName)
                    }
                })
                exo.prepare()
                exo.play()
                player = exo
            }.onFailure {
                playFailed()
            }
        } else if (playing) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    LaunchedEffect(playing, part.url) {
        while (playing && isActive) {
            player?.let { p ->
                runCatching {
                    val d = p.duration
                    if (d > 0) progress = (p.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                }
            }
            delay(200)
        }
    }

    Surface(
        onClick = {
            toggle()
            if (!transcript.isNullOrBlank()) showTranscript = true
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (playing) HugeIcons.Pause else HugeIcons.Play,
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(18.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            ) {
                bars.forEachIndexed { i, h ->
                    val lit = (i + 1).toFloat() / bars.size <= progress
                    Box(
                        modifier = Modifier
                            .size(3.dp, h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (lit) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                }
                            ),
                    )
                }
            }
            Text(
                text = "${durationSec.coerceAtLeast(1)}″",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
        // 点击语音条后展开的文字内容(可收起):助手 send_voice 的原文
        if (showTranscript && !transcript.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            VoiceTranscriptCollapsible(text = transcript)
        }
    }
}
