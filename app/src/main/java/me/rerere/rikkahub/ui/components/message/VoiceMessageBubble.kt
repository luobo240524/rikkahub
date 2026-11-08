package me.rerere.rikkahub.ui.components.message

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play

/**
 * 语音条气泡(微信式):播放/暂停 + 波形 + 时长,应用内 MediaPlayer 播放。
 * 波形高度由 (index, duration) 决定——同一条消息永远同一形状。
 */
@Composable
fun VoiceMessageBubble(
    part: UIMessagePart.Audio,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationSec by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

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
            val mp = MediaPlayer()
            runCatching {
                mp.setDataSource(part.url)
                mp.setOnPreparedListener {
                    durationSec = (it.duration / 1000).coerceAtLeast(1)
                    it.start()
                    playing = true
                }
                mp.setOnCompletionListener {
                    playing = false
                    progress = 0f
                    runCatching { it.release() }
                    player = null
                }
                mp.prepareAsync()
                player = mp
            }.onFailure {
                runCatching { mp.release() }
                player = null
            }
        } else if (playing) {
            runCatching { p.pause() }
            playing = false
        } else {
            runCatching { p.start() }
            playing = true
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
        onClick = { toggle() },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
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
}
