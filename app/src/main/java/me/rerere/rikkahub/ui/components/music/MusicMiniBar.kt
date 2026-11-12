package me.rerere.rikkahub.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.data.music.MusicPlayerController
import org.koin.compose.koinInject

/**
 * 一起听悬浮球:全页悬浮层(ChatPage 根部挂载),r250 重做。
 * - 52dp 圆球(封面),可拖到屏幕任意位置;暂停时球面盖 ▶ 角标
 * - 点按悬浮球 = 直接弹歌词面板(当前行高亮随播放滚动)
 * - 歌词面板右上角 × = 询问「是否关闭一起听」,确认后停止播放、悬浮球消失
 * - 无播放时整层隐藏;播完整歌单自动停止(见 MusicPlayerController)
 */
@Composable
fun MusicMiniBar(
    modifier: Modifier = Modifier,
    musicPlayer: MusicPlayerController = koinInject(),
) {
    val state by musicPlayer.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    if (!state.isActive) return

    var showLyrics by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val ballPx = with(density) { 52.dp.toPx() }
        var offset by remember { mutableStateOf(Offset(-1f, -1f)) }
        // 默认落在右上角(顶栏下方一点);-1 = 未初始化
        val pos = if (offset.x < 0f) {
            Offset(maxW - ballPx - with(density) { 12.dp.toPx() }, with(density) { 96.dp.toPx() })
        } else {
            offset
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }
                .size(52.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            (pos.x + dragAmount.x).coerceIn(0f, maxW - ballPx),
                            (pos.y + dragAmount.y).coerceIn(0f, maxH - ballPx),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { showLyrics = true }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (song.coverUrl != null) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.MusicNote03,
                        contentDescription = song.name,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (state.phase == MusicPlayerController.Phase.Paused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Play,
                            contentDescription = "已暂停",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    if (showLyrics) {
        MusicLyricsSheet(
            musicPlayer = musicPlayer,
            onDismiss = { showLyrics = false },
        )
    }
}

@Composable
private fun MusicLyricsSheet(
    musicPlayer: MusicPlayerController,
    onDismiss: () -> Unit,
) {
    val state by musicPlayer.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val scope = rememberCoroutineScope()
    var confirmClose by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // 顶部行:占位 + × 关闭入口(关闭前询问)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "一起听",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmClose = true }) {
                Icon(HugeIcons.Cancel01, contentDescription = "关闭一起听")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(120.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (song.coverUrl != null) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = HugeIcons.MusicNote03,
                            contentDescription = song.name,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 歌词区:当前行高亮 + 自动跟随
            val listState = rememberLazyListState()
            LaunchedEffect(state.currentLineIndex) {
                val idx = state.currentLineIndex
                if (idx >= 0 && state.lyrics.isNotEmpty()) {
                    listState.animateScrollToItem(idx)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.lyrics.isEmpty()) {
                    item {
                        Text(
                            text = "(暂无歌词)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(state.lyrics) { index, line ->
                    val isCurrent = index == state.currentLineIndex
                    Text(
                        text = line.text,
                        style = if (isCurrent) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = { musicPlayer.prev() }) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = "上一首")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { musicPlayer.togglePlay() },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (state.phase == MusicPlayerController.Phase.Playing) {
                                HugeIcons.Pause
                            } else {
                                HugeIcons.Play
                            },
                            contentDescription = null,
                        )
                    }
                }
                IconButton(onClick = { musicPlayer.next() }) {
                    Icon(HugeIcons.ArrowRight01, contentDescription = "下一首")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMs(state.positionMs) + " / " + formatMs(
                    if (state.durationMs > 0) state.durationMs else song.durationMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("是否关闭一起听?") },
            text = { Text("将停止播放并收起悬浮球") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    scope.launch {
                        musicPlayer.stopAwait()
                        onDismiss()
                    }
                }) { Text("确认关闭") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("继续听") }
            },
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
